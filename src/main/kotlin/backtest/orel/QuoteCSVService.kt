package backtest.orel

import backtest.Bar
import backtest.CSVHistoryLoader2
import bond.BusinessCalendar
import bond.CurveBuilder
import bond.YtmOfzDeltaService
import common.HibernateUtil
import model.BidAskLog
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.HashMap

object QuoteCSVService {
    val quotes: HashMap<String, Map<LocalDateTime, Bar>> = HashMap()

    fun load(code:String) {
        if (quotes.containsKey(code)) {
            return
        }

        quotes[code] = CSVHistoryLoader2().load("", code)
    }

    fun get(code:String, dtm: LocalDateTime) : Bar? {
       return quotes[code]!![dtm]
    }

    fun getOrPrev(code:String, dtm: LocalDateTime) : Bar? {
        val bar = quotes[code]!![dtm]
        if (bar != null) {
            return bar
        }

        var prevDtm = dtm
        for(i in 0..59) {
            prevDtm = prevDtm.minus(1, ChronoUnit.MINUTES)

            if (quotes[code]!![prevDtm] != null)
                return quotes[code]!![prevDtm]
        }

        return null
    }
}