package robot.orel

import bond.*
import com.enfernuz.quik.lua.rpc.api.messages.GetParamEx
import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import com.google.common.io.Files
import common.Connector
import common.StakanSubscriber
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import robot.AbstractLoopRobot
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class OrelOFZ : AbstractLoopRobot() {

    @Transient
    var nkd = HashMap<String, BigDecimal>()

    @Transient
    var approxBID = HashMap<String, BigDecimal>()

    @Transient
    val notifMap = HashMap<String, LocalDateTime>()

    @Transient
    lateinit var curve: Curve

    @Transient
    lateinit var log: Logger

    lateinit var file: File

    override fun name(): String = "OrelOFZ"

    override fun init() {
        super.init()
        log = LoggerFactory.getLogger(this::class.java)
        curve = CurveHolder.curveOFZ()

        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            for (bond in curve.bonds) {
                nkd[bond.code] = nkd("TQOB", bond.code, rpcClient)

                StakanSubscriber.subscribe("TQOB", bond.code)
            }
        }

        file = File("OrelOFZ.log")
        if (!file.exists()) {
            file.createNewFile()
            Files.append("code;time;ask;approxBid;duration;ytm;approxYtm;ytmDiff\n", file, StandardCharsets.UTF_8)
        }
    }

    override fun execute() {
        val settleDate = BusinessCalendar.addDays(LocalDate.now(), 1)
        CurveBuilder.stakanBuilder().build(curve, settleDate)

        for (bond in curve.bonds) {
            val calculationMaturityDate = bond.calculationEffectiveMaturityDate(settleDate)

            if (settleDate.plusDays(182) > calculationMaturityDate) {
                continue
            }

            //val days = ChronoUnit.DAYS.between(settleDate, calculationMaturityDate)
            val approx = curve.approx(curve.durations[bond.code]!!)

            var coupons: Map<LocalDate, BigDecimal> = bond.generateCoupons()
            coupons = coupons.filter { bond.isKnowsCoupon(it, settleDate) }

            val ytd = CalcYield.calcDirtyPriceFromYield(bond, settleDate, BigDecimal.valueOf(approx), coupons)
            val nkdToPrice = nkd[bond.code]!!.divide(bond.nominal, 8, RoundingMode.HALF_UP) * BigDecimal(100)
            approxBID[bond.code] = (ytd - nkdToPrice).setScale(6, RoundingMode.HALF_UP)
        }

        for (bond in curve.bonds) {
            val calculationMaturityDate = bond.calculationEffectiveMaturityDate(settleDate)
            if (settleDate.plusDays(182) > calculationMaturityDate) {
                continue
            }

            val rpcClient = Connector.get()
            val stakan: GetQuoteLevel2.Result
            synchronized(rpcClient) {
                val args2 = GetQuoteLevel2.Args("TQOB", bond.code)
                stakan = rpcClient.qlua_getQuoteLevel2(args2)
            }

            if (stakan.bids.size == 0 || stakan.offers.size == 0) {
                continue
            }

            val ask = BigDecimal(stakan.offers[0].price)

            if (ask + BigDecimal(0.2) < approxBID[bond.code]) {
                if (!notifMap.containsKey(bond.code)
                    || notifMap[bond.code]!!.plus(4, ChronoUnit.HOURS) < LocalDateTime.now()
                ) {
                    notifMap[bond.code] = LocalDateTime.now()

                    val nkdToPrice = (nkd[bond.code]!! * BigDecimal(100)).divide(bond.nominal, 12, RoundingMode.HALF_UP)

                    val askYTM =
                        CalcYield.effectiveYTM(bond, settleDate, ask + nkdToPrice).setScale(6, RoundingMode.HALF_UP)
                    val approxYtmBid =
                        BigDecimal.valueOf(curve.approx(curve.durations[bond.code]!!)).setScale(6, RoundingMode.HALF_UP)
                    val ytmDiff = (approxYtmBid - askYTM)

                    val duration = curve.durations[bond.code]!!.toInt()
                    val text =
                        "${bond.code};${LocalDateTime.now()};${ask.toPlainString()};${approxBID[bond.code]!!.toPlainString()};" +
                                "${duration};${askYTM.toPlainString()};${approxYtmBid.toPlainString()};${ytmDiff.toPlainString()}\n"

                    Files.append(text, file, StandardCharsets.UTF_8)
                }
            }

        }
    }

    private fun nkd(classCode: String, secCode: String, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
        val args = GetParamEx.Args(classCode, secCode, "ACCRUEDINT")
        val ex = rpcClient.qlua_getParamEx(args)
        return BigDecimal(ex.paramValue)
    }

}