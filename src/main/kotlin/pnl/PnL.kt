package pnl

import com.enfernuz.quik.lua.rpc.api.messages.GetParamEx
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import common.Connector
import common.HibernateUtil
import common.Telega
import db.dao.TradeDAO
import model.Trade
import org.slf4j.LoggerFactory
import robot.spreadler.SpreadlerBond
import robot.spreadler.SpreadlerConfigurator
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

fun main() {
    TradesFromQuik.load()
    PnL.calc()

    val today = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)
    PnL.sendResult(today, today.plusDays(1))
    HibernateUtil.shutdown()
}

object PnL {
    val log = LoggerFactory.getLogger(this::class.java)!!
    private val usdRate = BigDecimal("70")
    private val eurRate = BigDecimal("90")


    fun sendResult(from: LocalDateTime, to: LocalDateTime) {
        val select = TradeDAO.select("trade_datetime>=${sql(from)} and trade_datetime<${sql(to)}")

        var realizedPnL = BigDecimal.ZERO
        var fee = BigDecimal.ZERO
        var volume = BigDecimal.ZERO
        try {
            select.forEach {
                val fxRate = rateFX(it.currency)
                realizedPnL += it.realizedPnL!! * fxRate
                fee += it.feeAmount!! * fxRate
                volume += it.amount * fxRate
            }
        } catch (e: NullPointerException) {
            throw Exception("Not Calculated")
        }

        val unrealized = unrealizedSpreadlers()

        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        val msg = "    PnL \nfrom ${from.format(formatter)} to ${to.format(formatter)}\n\n" +
                "  Realized PnL= ${realizedPnL.toPlainString()}\n" +
                "  Оборот      = ${volume.stripTrailingZeros().toPlainString()}\n" +
                "  Fee        ~= ${fee.toPlainString()}\n" +
                "  Unrealized  = ${unrealized.stripTrailingZeros().toPlainString()}"
        log.info(msg)
        Telega.Holder.get().sendMessage(msg)
    }

    private fun rateFX(currency: String): BigDecimal {
        return when (currency) {
            "USD" -> { usdRate }
            "EUR" -> { eurRate }
            else -> { BigDecimal.ONE }
        }
    }

    private fun unrealizedSpreadlers(): BigDecimal {
        var unrealized = BigDecimal.ZERO
        for (spreadler in SpreadlerConfigurator.config.spreadlers) {
            val select = TradeDAO.select("sec_code='${spreadler.securityCode}' and trade_datetime = " +
                    "(select max(trade_datetime) from trade where sec_code='${spreadler.securityCode}')"
            , "trade_id desc")
            if (select.isEmpty()) {
                continue
            }
            val lastTrade: Trade = select.first()

            val position = lastTrade.position!!
            val buyAmount = lastTrade.buyAmount!!
            if(position == 0) {
                continue
            }

            val rpcClient = Connector.get()
            synchronized(rpcClient) {
                val fullPrice = fullPrice(spreadler, rpcClient)
                val fxRate = rateFX(currency(spreadler, rpcClient))

                unrealized += (fullPrice * BigDecimal(position) + lastTrade.sellAmount!! - buyAmount) * fxRate
            }
        }

        return unrealized
    }

    fun fullPrice(spreadler: SpreadlerBond, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
        val lastPrice = lastPrice(spreadler, rpcClient)
        val faceValue = faceValue(spreadler, rpcClient)
        val nkd = BigDecimal.ZERO //nkd(spreadler, rpcClient) из квика суммы сделок без нкд
        return lastPrice/BigDecimal("100") * faceValue + nkd
    }

    private fun lastPrice(spreadler: SpreadlerBond, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
        val args = GetParamEx.Args(spreadler.classCode, spreadler.securityCode, "LAST")
        val ex = rpcClient.qlua_getParamEx(args)
        val price = BigDecimal(ex.paramValue)

        if (price > BigDecimal.ZERO) {
            return price
        }

        val args2 = GetParamEx.Args(spreadler.classCode, spreadler.securityCode, "PREVPRICE")
        val ex2 = rpcClient.qlua_getParamEx(args2)
        return BigDecimal(ex2.paramValue)
    }

    private fun nkd(spreadler: SpreadlerBond, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
        val args = GetParamEx.Args(spreadler.classCode, spreadler.securityCode, "ACCRUEDINT")
        val ex = rpcClient.qlua_getParamEx(args)
        return BigDecimal(ex.paramValue)
    }

    private fun faceValue(spreadler: SpreadlerBond, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
        val args = GetParamEx.Args(spreadler.classCode, spreadler.securityCode, "SEC_FACE_VALUE")
        val ex = rpcClient.qlua_getParamEx(args)
        return BigDecimal(ex.paramValue)
    }

    private fun currency(spreadler: SpreadlerBond, rpcClient: ZmqTcpQluaRpcClient): String {
        val args = GetParamEx.Args(spreadler.classCode, spreadler.securityCode, "SEC_FACE_UNIT")
        val ex = rpcClient.qlua_getParamEx(args)
        return ex.paramImage
    }

    private fun sql(time: LocalDateTime): String {
        return "TO_TIMESTAMP('${time.dayOfMonth}.${time.month.value}.${time.year} ${time.hour}:${time.minute}:${time.second}'" +
                ", 'dd.MM.yyyy HH24:mi:ss')"
    }

    fun calc() {
        val trades = TradeDAO.select("position is null", "trade_datetime asc")

        val map = groupBySecCode(trades)

        for (secCode in map.keys) {
            val select = TradeDAO.select("sec_code='$secCode' and trade_datetime = " +
                    "(select max(trade_datetime) from trade where sec_code='$secCode' and realized_pnl is not null)")
            val prevTrade: Trade? = select.singleOrNull()

            var position = if (prevTrade == null) 0 else prevTrade.position!!
            var buyAmount = if (prevTrade == null) BigDecimal.ZERO else prevTrade.buyAmount!!
            var sellAmount: BigDecimal = if (prevTrade == null) BigDecimal.ZERO else prevTrade.sellAmount!!

            val queue = map[secCode]!!
            queue.forEach {
                if (it.direction == "B") {
                    it.position = position + it.quantity
                    it.buyAmount = buyAmount + it.amount
                    it.sellAmount = sellAmount
                    it.realizedPnL = BigDecimal.ZERO
                } else {
                    it.position = position - it.quantity
                    it.buyAmount = buyAmount
                    it.sellAmount = sellAmount + it.amount
                }
                if (it.position!! == 0) {
                    it.realizedPnL = it.sellAmount!! - it.buyAmount!!
                    it.buyAmount = BigDecimal.ZERO
                    it.sellAmount = BigDecimal.ZERO
                } else {
                    it.realizedPnL = BigDecimal.ZERO
                }

                it.feeAmount = it.amount * BigDecimal("0.0003") // ~приблизительно (0,02% БКС, 0,01%МБ)

                TradeDAO.save(it)

                position = it.position!!
                buyAmount = it.buyAmount!!
                sellAmount = it.sellAmount!!
            }
        }
    }

    private fun groupBySecCode(trades: List<Trade>): HashMap<String, PriorityQueue<Trade>> {
        val map = HashMap<String, PriorityQueue<Trade>>()
        for (trade in trades) {
            if (!map.containsKey(trade.securityCode)) {
                //PriorityQueue<Trade>(kotlin.Comparator())
                val queue = PriorityQueue<Trade> { o1, o2 -> o1.trade_datetime.compareTo(o2.trade_datetime) }

                map[trade.securityCode] = queue
            }

            val queue = map[trade.securityCode]!!
            queue.add(trade)
        }
        return map
    }
}