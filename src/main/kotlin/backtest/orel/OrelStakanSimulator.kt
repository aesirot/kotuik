package backtest.orel

import bond.*
import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.google.common.collect.Lists
import common.HibernateUtil
import common.StakanProvider
import model.BidAskLog
import model.Bond
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.ArrayList
import kotlin.system.exitProcess

fun main() {
    val start = LocalDate.of(2020, 12, 11)
    val end = LocalDate.of(2020, 12, 12)

    val curveOFZ = CurveHolder.curveOFZ()
    OrelStakanSimulator.init(curveOFZ, start, end)

    for (bond in curveOFZ.bonds) {
        OrelStakanSimulator.fly(bond)
    }

    val curveAFK = CurveHolder.createCurveSystema()
    for (bond in curveAFK.bonds) {
        OrelStakanSimulator.fly(bond)
    }

    HibernateUtil.shutdown()

    println("pnl=${OrelFlightSimulator.pnl}")
    println("done")

    exitProcess(0)
}

object OrelStakanSimulator {
    private val map = TreeMap<LocalDateTime, MutableMap<String, BidAskLog>>()
    private var currentDtm: LocalDateTime = LocalDateTime.now()
    private lateinit var curve: Curve

    private val log = LoggerFactory.getLogger(this::class.simpleName)

    fun init(curve: Curve, start: LocalDate, end: LocalDate) {
        this.curve = curve

        HibernateUtil.getSessionFactory().openSession().use { session ->
            val query = session.createQuery("from BidAskLog where dtm >= :s and dtm <= :e"
                    , BidAskLog::class.java)
            query.setParameter("s", start.atStartOfDay())
            query.setParameter("e", end.atStartOfDay())
            val list = query.list()

            for (log in list) {
                if (!map.containsKey(log.dtm)) {
                    map[log.dtm] = TreeMap()
                }

                map[log.dtm]!![log.code] = log
            }
        }
    }

    fun fly(bond: Bond) {
        val curveBuilder = CurveBuilder(StakanSimulator())
        val diffs = ArrayList<BigDecimal>()

        for (entry in map.entries) {
            currentDtm = entry.key
            if (currentDtm.hour == 18 && currentDtm.minute > 40) {
                continue
            }
            val settleDt = BusinessCalendar.addDays(currentDtm.toLocalDate(), 1)

            val nkd = CalcYield.calcAccrual(bond, settleDt)
            val nkdToPrice = (nkd * BigDecimal(100)).divide(bond.nominal, 12, RoundingMode.HALF_UP)

            curveBuilder.build(curve, settleDt)

            val dirtyPrice = entry.value[bond.code]!!.bid + nkdToPrice
            val ytm = CalcYield.effectiveYTM(bond, settleDt, dirtyPrice)
            val durationDays = CalcDuration.durationDays(bond, settleDt, ytm, dirtyPrice)

            val approxYtm = curve.approx(durationDays)

            diffs.add((ytm - BigDecimal(approxYtm)) * BigDecimal(100))
        }

        val (matOzh, dispercia) = CalcStats.analyze(diffs)
        log.info("${bond.code} mo=${matOzh.toPlainString()} дисп=${dispercia.toPlainString()}")

        var last = LocalDate.of(2020, 1, 1)

        for (entry in map.entries) {
            currentDtm = entry.key
            if (currentDtm.hour == 18 && currentDtm.minute > 40) {
                continue
            }
            val settleDt = BusinessCalendar.addDays(currentDtm.toLocalDate(), 1)

            val nkd = CalcYield.calcAccrual(bond, settleDt)
            val nkdToPrice = (nkd * BigDecimal(100)).divide(bond.nominal, 12, RoundingMode.HALF_UP)

            curveBuilder.build(curve, settleDt)

            val dirtyPrice = entry.value[bond.code]!!.bid + nkdToPrice
            val ytm = CalcYield.effectiveYTM(bond, settleDt, dirtyPrice)
            val durationDays = CalcDuration.durationDays(bond, settleDt, ytm, dirtyPrice)

            val approxYtm = curve.approx(durationDays)

            val correctedYTM = BigDecimal(approxYtm) + matOzh.divide(BigDecimal(100), 18, RoundingMode.HALF_UP)

            val correctedApproxDirtyBID = CalcYield.calcDirtyPriceFromYield(bond, settleDt, correctedYTM, bond.generateCoupons())
            if (entry.key.toLocalDate() > last) {
                if (entry.value[bond.code]!!.ask < correctedApproxDirtyBID - nkdToPrice - BigDecimal("0.01")) {
                    log.info("${entry.key} ${bond.code} ask price ${entry.value[bond.code]!!.ask}")
                }
                last = entry.key.toLocalDate()
            }
        }

    }


    private class StakanSimulator : StakanProvider() {
        override fun stakan(classCode: String, secCode: String): GetQuoteLevel2.Result {
            val bidAskLog = OrelStakanSimulator.map[OrelStakanSimulator.currentDtm]!![secCode]!!

            val bid = GetQuoteLevel2.QuoteEntry(bidAskLog.bid.toPlainString(), "1")
            val ask = GetQuoteLevel2.QuoteEntry(bidAskLog.ask.toPlainString(), "1")

            return GetQuoteLevel2.Result("1", "1", Lists.newArrayList(bid), Lists.newArrayList(ask))
        }

        override fun nkd(bond: Bond): BigDecimal {
            val settleDt = BusinessCalendar.addDays(currentDtm.toLocalDate(), 1)
            return CalcYield.calcAccrual(bond, settleDt)
        }
    }
}

