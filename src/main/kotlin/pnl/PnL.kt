package pnl

import db.Trade
import db.dao.TradeDAO
import org.slf4j.LoggerFactory
import robot.Telega
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
}

object PnL {
    val log = LoggerFactory.getLogger(this::class.java)


    fun sendResult(from: LocalDateTime, to: LocalDateTime) {
        val select = TradeDAO.select("trade_datetime>=${sql(from)} and trade_datetime<${sql(to)}")

        var realizedPnL = BigDecimal.ZERO
        var fee = BigDecimal.ZERO
        try {
            select.forEach {
                realizedPnL += it.realizedPnL!!
                fee += it.feeAmount!!
            }
        } catch (e: NullPointerException) {
            throw Exception("Not Calculated")
        }

        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

        val msg = "    PnL \nfrom ${from.format(formatter)} to ${to.format(formatter)}\n\n" +
                "  Realized PnL=$realizedPnL\n  fee ~= $fee"
        log.info(msg)
        Telega.Holder.get().sendMessage(msg)
    }

    private fun sql(time: LocalDateTime): String {
        return "PARSEDATETIME('${time.dayOfMonth}.${time.month.value}.${time.year} ${time.hour}:${time.minute}:${time.second}'" +
                ", 'dd.MM.yyyy HH:mm:ss')"
    }

    fun calc() {
        val trades = TradeDAO.select("position is null")

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
                    if (it.position!! < 0) {
                        it.position = 0
                        it.buyAmount = BigDecimal.ZERO
                        it.sellAmount = BigDecimal.ZERO
                    }

                    if (it.position!! == 0) {
                        it.realizedPnL = it.sellAmount!! - it.buyAmount!!
                        it.buyAmount = BigDecimal.ZERO
                        it.sellAmount = BigDecimal.ZERO
                    } else {
                        it.realizedPnL = BigDecimal.ZERO
                    }
                }
                it.feeAmount = it.amount * BigDecimal("0.0005") // ~приблизительно

                TradeDAO.update(it)

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
                val queue = PriorityQueue<Trade>(kotlin.Comparator
                { o1, o2 -> o1.trade_datetime.compareTo(o2.trade_datetime) })

                map[trade.securityCode] = queue
            }

            val queue = map[trade.securityCode]!!
            queue.add(trade)
        }
        return map
    }
}