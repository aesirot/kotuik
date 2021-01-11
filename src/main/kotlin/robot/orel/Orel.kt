package robot.orel

import bond.*
import com.enfernuz.quik.lua.rpc.api.messages.GetOrderByNumber
import com.enfernuz.quik.lua.rpc.api.messages.GetParamEx
import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import common.*
import model.Bond
import model.SecAttr.MoexClass
import model.robot.PolzuchiiSellState
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import robot.AbstractLoopRobot
import robot.PolzuchiiSell
import robot.PolzuchiiSellRobot
import robot.Robot
import robot.infra.Zavod
import robot.strazh.MoexStrazh
import java.math.BigDecimal
import java.math.RoundingMode
import java.math.RoundingMode.HALF_UP
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class Orel : AbstractLoopRobot() {

    @Transient
    var nkd = HashMap<String, BigDecimal>()

    @Transient
    var duration = HashMap<String, BigDecimal>()

    @Transient
    var approxBID = HashMap<String, BigDecimal>()

    private val debugNotificationMap = HashMap<String, LocalDateTime>()

    @Transient
    lateinit var curveOFZ: Curve

    @Transient
    lateinit var bonds: List<Bond>

    @Transient
    lateinit var log: Logger

    private lateinit var signalPath: Path
    private lateinit var signalDebugPath: Path

    private val dtmFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss.SSS")

    override fun name(): String = "Orel"

    private val limitEntity = HashMap<Long, BigDecimal>()
    private val limitBond = HashMap<String, BigDecimal>()

    private var handler: OrelQuoteHandler? = null

    private val fireBuySpread = BigDecimal("0.28")
    private val sellPremium = BigDecimal("0.24")

    override fun init() {
        super.init()
        log = LoggerFactory.getLogger(this::class.java)
        curveOFZ = CurveHolder.curveOFZ()

        HibernateUtil.getSessionFactory().openSession().use { session ->
            val bondQuery = session.createQuery("from Bond", Bond::class.java)
            bonds = bondQuery.list()
        }

        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            for (bond in bonds) {
                if (bond.getAttr(MoexClass) == null) {
                    continue
                }//todo set
                val classCode = bond.getAttrM(MoexClass) //TODO check class code!!!
                nkd[bond.code] = nkd(classCode, bond.code, rpcClient)
                duration[bond.code] = duration(classCode, bond.code, rpcClient)

                StakanSubscriber.subscribe(classCode, bond.code)

                //проверка статики
                val settleDate = BusinessCalendar.addDays(LocalDate.now(), 1)
                val calcNkd = CalcYield.calcAccrual(bond, settleDate)
                val nkdToPrice = (nkd[bond.code]!! * BigDecimal(100)).divide(bond.nominal, 12, HALF_UP)

                val last = last(classCode, bond.code, rpcClient)
                if (last.compareTo(BigDecimal.ZERO) == 0) {
                    continue
                }
                val ytm = yield(classCode, bond.code, rpcClient)
                val calcYield = (CalcYield.effectiveYTM(bond, settleDate, last + nkdToPrice)
                        * BigDecimal(100))
                    .setScale(2, HALF_UP)

                if (calcNkd - nkd[bond.code]!! > BigDecimal("0.01")) {
                    val msg =
                        "Ошибка статики ${bond.code} нкд факт ${nkd[bond.code]!!.toPlainString()} ож ${calcNkd.toPlainString()}"
                    log.error(msg)
                    Telega.Holder.get().sendMessage(msg)
                }
                if (calcYield.compareTo(ytm) != 0) {
                    val msg =
                        "Ошибка статики ${bond.code} доха факт ${ytm.toPlainString()} ож ${calcYield.toPlainString()}"
                    log.error(msg)
                    Telega.Holder.get().sendMessage(msg)
                }
            }
        }

        signalPath = Paths.get("logs/Orel.log")
        if (!Files.exists(signalPath, LinkOption.NOFOLLOW_LINKS)) {
            val header = "code;time;ask;approxBid;duration;ytm;approxYtm;premium;ytmDiff;vol\n"
            Files.writeString(signalPath, header, UTF_8, StandardOpenOption.CREATE)
        }

        signalDebugPath = Paths.get("logs/OrelDebug.log")
        if (!Files.exists(signalDebugPath, LinkOption.NOFOLLOW_LINKS)) {
            val header = "code;time;ask;approxBid;duration;ytm;approxYtm;premium;ytmDiff;vol\n"
            Files.writeString(signalDebugPath, header, UTF_8, StandardOpenOption.CREATE)
        }

        YtmOfzDeltaService.initAll()

        //handler = OrelQuoteHandler(this)
        //Connector.registerEventHandler(handler!!)
    }

    override fun execute() {
        if (!curveOFZ.isCalculated()) {
            return
        }

        val now = LocalDateTime.now()
        if (now.hour > 18 && now.minute > 40) {
            return
        }

        refreshLimits()

        val settleDate = BusinessCalendar.addDays(LocalDate.now(), 1)

        for (bond in bonds) {
            if (bond.getAttr(MoexClass) == null) {
                //log.error("${bond.code} has no MoexClass")
                continue
            }

            val approxOfz = curveOFZ.approx(duration[bond.code]!!)
            val premiumYtm = YtmOfzDeltaService.getPremiumYtm(bond.code)
            if (premiumYtm == null) {
                continue
            }

            val approxYtm = BigDecimal.valueOf(approxOfz) + premiumYtm

            val approxBuyDirtyPrice =
                CalcYield.calcDirtyPriceFromYield(bond, settleDate, approxYtm, bond.generateCoupons())

            val nkdToPrice = (nkd[bond.code]!! * BigDecimal(100)).divide(bond.nominal, 12, HALF_UP)

            approxBID[bond.code] = (approxBuyDirtyPrice - nkdToPrice).setScale(6, HALF_UP)
        }

        for (bond in bonds) {
            if (approxBID[bond.code] == null) {
                continue
            }

            onQuote(bond.getAttrM(MoexClass), bond.code)
        }

    }

    fun onQuote(classCode: String, secCode: String) {
        val now = LocalDateTime.now()
        if (now.hour > 18 && now.minute > 40) {
            return
        }

        if (approxBID[secCode] == null) {
            return
        }

        val rpcClient = Connector.get()
        val stakan: GetQuoteLevel2.Result
        synchronized(rpcClient) {
            val args2 = GetQuoteLevel2.Args(classCode, secCode)
            stakan = rpcClient.qlua_getQuoteLevel2(args2)
        }

        if (stakan.bids.size == 0 || stakan.offers.size == 0) {
            return
        }

        val ask = BigDecimal(stakan.offers[0].price)

        if (ask + fireBuySpread < approxBID[secCode]) {
            log.info("Интересная заявка $secCode ${ask.toPlainString()} - ${stakan.offers[0].quantity} шт")

            val bond = LocalCache.getBond(secCode)
            val settleDate = BusinessCalendar.addDays(LocalDate.now(), 1)

            val nkdToPrice = (nkd[bond.code]!! * BigDecimal(100)).divide(bond.nominal, 12, HALF_UP)

            val askYTM = CalcYield.effectiveYTM(bond, settleDate, ask + nkdToPrice)
            val duration = CalcDuration.durationDays(bond, settleDate, askYTM, ask + nkdToPrice)

            if (duration > BigDecimal(3650)) {
                log.info("$secCode - отсекаю слишком большую дюрацию (дальний край ОФЗ)")
                return // не смотрим самые дальние - там кривая болтается туда-сюда, ложные сигналы
            }

            if (MoexStrazh.instance.isBuyApproved()) {
                val qty = limit(bond, BigDecimal(stakan.offers[0].quantity))
                log.info("Лимитированное колво = $qty")

                buy(bond, qty, ask, rpcClient)
            }

            val approxYtmBid = BigDecimal.valueOf(curveOFZ.approx(duration))
            val premiumYtm = YtmOfzDeltaService.getPremiumYtm(bond.code)!!
            val ytmDiff = (approxYtmBid + premiumYtm - askYTM)

            var text =
                "${bond.code};${LocalDateTime.now()};${ask.toPlainString()};${approxBID[bond.code]!!.toPlainString()};" +
                        "${duration};${askYTM.toPlainString()};${approxYtmBid.toPlainString()};" +
                        "${premiumYtm.toPlainString()};${ytmDiff.toPlainString()};${stakan.offers[0].quantity}\n"
            text = text.replace('.', ',')


            Files.writeString(signalPath, text, UTF_8, StandardOpenOption.APPEND)
        }

        //DEBUG
        if (ask < approxBID[secCode]) {
            val notificationKey = secCode + ask.toPlainString()
            if (!debugNotificationMap.containsKey(notificationKey)
                || debugNotificationMap[notificationKey]!!.plus(1, ChronoUnit.MINUTES) < LocalDateTime.now()
            ) {
                val bond = LocalCache.getBond(secCode)
                val settleDate = BusinessCalendar.addDays(LocalDate.now(), 1)

                val nkdToPrice = (nkd[bond.code]!! * BigDecimal(100)).divide(bond.nominal, 12, HALF_UP)

                val askYTM = CalcYield.effectiveYTM(bond, settleDate, ask + nkdToPrice)
                val duration = CalcDuration.durationDays(bond, settleDate, askYTM, ask + nkdToPrice)
                val approxYtmBid = BigDecimal.valueOf(curveOFZ.approx(duration))
                val premiumYtm = YtmOfzDeltaService.getPremiumYtm(bond.code)!!
                val ytmDiff = (approxYtmBid + premiumYtm - askYTM)

                var text =
                    "${bond.code};${LocalDateTime.now()};${ask.toPlainString()};${approxBID[bond.code]!!.toPlainString()};" +
                            "${duration};${askYTM.toPlainString()};${approxYtmBid.toPlainString()};" +
                            "${premiumYtm.toPlainString()};${ytmDiff.toPlainString()};${stakan.offers[0].quantity}\n"
                text = text.replace('.', ',')

                Files.writeString(signalDebugPath, text, UTF_8, StandardOpenOption.APPEND)
            }
        }

    }

    private fun buy(
        bond: Bond,
        qty: Int,
        ask: BigDecimal,
        rpcClient: ZmqTcpQluaRpcClient
    ) {
        val orderId = Orders.buyOrderDLL(
            bond.getAttrM(MoexClass),
            bond.code,
            qty,
            ask,
            rpcClient,
            name()
        )

        Thread.sleep(500)

        val orderInfo: GetOrderByNumber.Result
        synchronized(rpcClient) {
            orderInfo = rpcClient.qlua_getOrderByNumber(bond.getAttrM(MoexClass), orderId)
            if (orderInfo.isError) {
                throw Exception("Order $orderId state unknown error")
            }
        }

        val rest = orderInfo.order.balance.toBigDecimal().toInt()

        if (rest > 0) {
            log.info("Не все купил. Отменяю остаток $rest")
            Orders.cancelOrderDLL(bond.getAttrM(MoexClass), bond.code, orderId, name(), rpcClient)
        }

        val realizedBuy = qty - rest

        Telega.Holder.get().sendMessage("Орёл: куп ${bond.code} $realizedBuy шт")

        log.info("создаю робота на продажу")
        val sellRobotState = PolzuchiiSellState(
            bond.getAttrM(MoexClass),
            bond.code,
            realizedBuy,
            ask + sellPremium + BigDecimal.ONE,
            ask + sellPremium,
            realizedBuy / 4
        )
        val sellRobot = PolzuchiiSellRobot(sellRobotState)
        sellRobot.setParent(name())
        sellRobot.name = "orel " + bond.code + " " + LocalDateTime.now().format(dtmFormat)

        log.info("регистрация")
        Zavod.addRobot(sellRobot)

        log.info("пуск")
        sellRobot.start()

        log.info("готово")
    }

    @Synchronized
    private fun limit(bond: Bond, offerQty: BigDecimal): Int {
        var qty = offerQty

        var amount = bond.nominal * qty
        if (amount > limitBond[bond.code]) {
            qty = limitBond[bond.code]!!.divide(bond.nominal, 0, RoundingMode.FLOOR)
            amount = bond.nominal * qty
        }
        if (amount > limitEntity[bond.issuerId]) {
            qty = limitEntity[bond.issuerId]!!.divide(bond.nominal, 0, RoundingMode.FLOOR)
        }

        limitEntity[bond.issuerId!!] = limitEntity[bond.issuerId]!! - qty * bond.nominal
        limitBond[bond.code] = limitBond[bond.code]!! - qty * bond.nominal

        return qty.toInt()
    }

    private fun nkd(classCode: String, secCode: String, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
        val args = GetParamEx.Args(classCode, secCode, "ACCRUEDINT")
        val ex = rpcClient.qlua_getParamEx(args)
        return BigDecimal(ex.paramValue)
    }

    private fun duration(classCode: String, secCode: String, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
        val args = GetParamEx.Args(classCode, secCode, "DURATION")
        val ex = rpcClient.qlua_getParamEx(args)
        return BigDecimal(ex.paramValue)
    }

    private fun last(classCode: String, secCode: String, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
        val args = GetParamEx.Args(classCode, secCode, "LAST")
        val ex = rpcClient.qlua_getParamEx(args)
        return BigDecimal(ex.paramValue)
    }

    private fun yield(classCode: String, secCode: String, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
        val args = GetParamEx.Args(classCode, secCode, "YIELD")
        val ex = rpcClient.qlua_getParamEx(args)
        return BigDecimal(ex.paramValue)
    }

    @Synchronized
    fun refreshLimits() {
        initialLimits()
        reduceUsedLimits()
    }

    private fun initialLimits() {
        limitEntity[1] = BigDecimal(300000)
        limitEntity[2] = BigDecimal(200000)

        for (bond in bonds) {
            if (bond.issuerId == 1L) {
                limitBond[bond.code] = BigDecimal(100000)
            } else if (bond.issuerId == 2L) {
                limitBond[bond.code] = BigDecimal(100000)
            }
        }
    }

    private fun reduceUsedLimits() {
        val children = DBService.loadRobots("parentId='" + name() + "'")
        for (child in children) {
            if (child is PolzuchiiSell) {
                val bond = LocalCache.getBond(child.securityCode)

                val positionValue = BigDecimal(child.restQuantity) * bond.nominal
                limitBond[child.securityCode] = limitBond[child.securityCode]!! - positionValue
                limitEntity[bond.issuerId!!] = limitEntity[bond.issuerId]!! - positionValue
            }
        }
    }

    override fun setFinishCallback(function: (Robot) -> Unit) {
    }

    override fun stop() {
        if (!isRunning()) {
            return
        }

        if (handler != null) {
            Connector.unregisterEventHandler(handler!!)
            handler = null
        }

        super.stop()
    }
}