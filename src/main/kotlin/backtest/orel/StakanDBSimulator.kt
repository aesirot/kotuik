package backtest.orel

import bond.CalcYield
import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.google.common.collect.Lists
import common.HibernateUtil
import common.StakanProvider
import model.BidAskLog
import model.Bond
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.function.Supplier

class StakanDBSimulator(val timeProvider: Supplier<LocalDateTime>) : StakanProvider() {

    override fun stakan(classCode: String, secCode: String): GetQuoteLevel2.Result {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val query = session.createQuery("from BidAskLog where code = :code and dtm=:dtm", BidAskLog::class.java)
            query.setParameter("code", secCode)
            query.setParameter("dtm", timeProvider.get())

            val log = query.singleResult

            val bid = GetQuoteLevel2.QuoteEntry(log.bid.toPlainString(), "1")
            val ask = GetQuoteLevel2.QuoteEntry(log.ask.toPlainString(), "1")
            return GetQuoteLevel2.Result("1", "1", Lists.newArrayList(bid), Lists.newArrayList(ask))
        }
    }

    override fun nkd(bond: Bond, settleDate: LocalDate): BigDecimal {
        return CalcYield.calcAccrual(bond, OrelFlightSimulator.currentTime.toLocalDate())
    }

}