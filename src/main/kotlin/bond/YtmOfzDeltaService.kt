package bond

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
import kotlin.collections.HashMap

fun main () {
    YtmOfzDeltaService.initAll()
}

object YtmOfzDeltaService {
    private val map = HashMap<String, BigDecimal>()

    private var currentDtm: LocalDateTime = LocalDateTime.now()
    private lateinit var curve: Curve
    private lateinit var allBonds: List<Bond>
    private var initializationEndDate: LocalDate? = null

    private val log = LoggerFactory.getLogger(this::class.simpleName)

    fun getPremiumYtm(secCode: String): BigDecimal? {
        return map[secCode]
    }

    @Synchronized
    fun initAll() {
        log.info("start")
        val start = BusinessCalendar.minusDays(LocalDate.now(), 11)
        val end = LocalDate.now()

        if (initializationEndDate != null && initializationEndDate == end) {
            log.info("already initialized")
            return
        }

        map.clear()

        val bidAskStory = loadStakan(start, end)

        calculateYtmOfzDelta(bidAskStory)

        initializationEndDate = end
        log.info("end")
    }

    fun init(bond: Bond, start: LocalDate, end: LocalDate) {
        log.info("start")

        map.clear()

        val bidAskStory = loadStakan(start, end)
        log.info("stakan loaded")

        allBonds = Lists.newArrayList(bond) //quick calc, for test

        calculateYtmOfzDelta(bidAskStory)
        log.info("end")
    }

    private fun loadStakan(start: LocalDate, end: LocalDate): TreeMap<LocalDateTime, MutableMap<String, BidAskLog>> {
        val bidAskStory = TreeMap<LocalDateTime, MutableMap<String, BidAskLog>>()

        HibernateUtil.getSessionFactory().openSession().use { session ->
            var sql = "from BidAskLog where dtm >= :s and dtm < :e"

            val query = session.createQuery(sql, BidAskLog::class.java)
            query.setParameter("s", start.atStartOfDay())
            query.setParameter("e", end.atStartOfDay())

            val list = query.list()

            for (log in list) {
                if (!bidAskStory.containsKey(log.dtm)) {
                    bidAskStory[log.dtm] = TreeMap()
                }

                bidAskStory[log.dtm]!![log.code] = log
            }

            val bondQuery = session.createQuery("from Bond", Bond::class.java)
            allBonds = bondQuery.list()
                .filter { it.maturityDt > LocalDate.now() }
        }

        return bidAskStory
    }

    private fun calculateYtmOfzDelta(bidAskStory: TreeMap<LocalDateTime, MutableMap<String, BidAskLog>>) {
        val diffs = HashMap<String, ArrayList<BigDecimal>>()
        for (bond in allBonds) {
            diffs[bond.code] = ArrayList()
        }

        this.curve = CurveHolder.curveOFZ()

        var prevDay: LocalDate? = null

        val curveBuilder = CurveBuilder(StakanSimulator(bidAskStory))

        val nkdCache = HashMap<String, BigDecimal>()

        for (entry in bidAskStory.entries) {
            currentDtm = entry.key


            if (currentDtm.hour < 11) {
                continue// утром большой спред, это искажает
            }

            val settleDt = BusinessCalendar.addDays(currentDtm.toLocalDate(), 1)

            if (prevDay == null || prevDay != currentDtm.toLocalDate()) {
                prevDay = currentDtm.toLocalDate()

                nkdCache.clear()

                for (bond in allBonds) {
                    val nkd = CalcYield.calcAccrual(bond, settleDt)
                    val nkdToPrice = (nkd * BigDecimal(100)).divide(bond.nominal, 12, RoundingMode.HALF_UP)

                    nkdCache[bond.code] = nkdToPrice
                }
            }

            curveBuilder.build(curve, settleDt)

            for (bond in allBonds) {
                if (!entry.value.containsKey(bond.code)) {
                    continue
                }

                val dirtyPrice = entry.value[bond.code]!!.bid + nkdCache[bond.code]!!
                val ytm = CalcYieldDouble.effectiveYTM(bond, settleDt, dirtyPrice)
                val durationDays = CalcDuration.durationDays(bond, settleDt, ytm, dirtyPrice)

                val ofzCurveYtm = curve.approx(durationDays)

                diffs[bond.code]!!.add((ytm - BigDecimal(ofzCurveYtm)))
            }
        }

        for (bond in allBonds) {
            if (diffs[bond.code]!!.size == 0) {
                continue
            }

            val (matOzh, dispercia) = CalcStats.analyze(diffs[bond.code]!!)
            log.info("${bond.code} mo=${matOzh.toPlainString()} дисп=${dispercia.toPlainString()}")

            map[bond.code] = matOzh
        }

        curve.clearCalculation()
    }

    private class StakanSimulator(val bidAskStory: TreeMap<LocalDateTime, MutableMap<String, BidAskLog>>) : StakanProvider() {

        override fun stakan(classCode: String, secCode: String): GetQuoteLevel2.Result {
            if (!bidAskStory.containsKey(currentDtm) || !bidAskStory[currentDtm]!!.containsKey(secCode)) {
                return GetQuoteLevel2.Result("0", "0", Lists.newArrayList(), Lists.newArrayList())
            }

            val bidAskLog = bidAskStory[currentDtm]!![secCode]!!

            val bid = GetQuoteLevel2.QuoteEntry(bidAskLog.bid.toPlainString(), "1")
            val ask = GetQuoteLevel2.QuoteEntry(bidAskLog.ask.toPlainString(), "1")

            return GetQuoteLevel2.Result("1", "1", Lists.newArrayList(bid), Lists.newArrayList(ask))
        }

        override fun nkd(bond: Bond, settleDate: LocalDate): BigDecimal {
            return CalcYield.calcAccrual(bond, settleDate)
        }
    }

}