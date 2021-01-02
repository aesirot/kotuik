package backtest.orel

import bond.*
import common.HibernateUtil
import model.BidAskLog
import model.Bond
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.*

fun main() {
    val systema = CurveHolder.createCurveSystema()
    for (bond in systema.bonds) {
        OrelRadar.scan(bond)
    }
}

object OrelRadar {

    fun scan(bond: Bond) {
        val curveOFZ = CurveHolder.curveOFZ()

        HibernateUtil.getSessionFactory().openSession().use { session ->
            val query = session.createQuery(
                "from BidAskLog where code = :code and ask = (select min(ask) from BidAskLog where code = :code)",
                BidAskLog::class.java
            )
            query.setParameter("code", bond.code)
            val list = query.list()

            for (log in list) {
                val date = log.dtm.toLocalDate()

                if (date < LocalDate.of(2020,12,18)) {
                    continue
                }

                val start = BusinessCalendar.minusDays(date, 11)
                val settleDate = BusinessCalendar.addDays(date, 1)

                YtmOfzDeltaService.init(bond, start, date)
                val premiumYtm = YtmOfzDeltaService.getPremiumYtm(bond.code)

                val stakanProvider = StakanDBSimulator { log.dtm }
                CurveBuilder(stakanProvider).build(curveOFZ, settleDate)

                val nkd = CalcYield.calcAccrual(bond, settleDate)
                val nkdToPrice = (nkd * BigDecimal(100)).divide(bond.nominal, 12, RoundingMode.HALF_UP)

                val dirtyPrice = log.ask + nkdToPrice
                val ytm = CalcYield.effectiveYTM(bond, settleDate, dirtyPrice)
                val durationDays = CalcDuration.durationDays(bond, settleDate, ytm, dirtyPrice)

                val approx = curveOFZ.approx(durationDays)

                val approxDirtyBid =
                    CalcYield.calcDirtyPriceFromYield(bond, settleDate, BigDecimal.valueOf(approx) + premiumYtm!!, bond.generateCoupons())
                val approxBid = approxDirtyBid - nkdToPrice

                println("${bond.code};${log.dtm};${log.ask};${approxBid}")
            }
        }

    }


}