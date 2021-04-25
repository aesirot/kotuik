package backtest.msci

import backtest.Bar
import backtest.CSVHistoryLoader2
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

fun main() {
    MSCISimulator.load()

    for (i in 5L..20L) {
        print("$i;")

        var triggerDelta = BigDecimal("0.01")
        while (triggerDelta <= BigDecimal("0.05")) {
            val pnl = MSCISimulator.run(triggerDelta, i)
            print("${pnl.toPlainString()};")

            triggerDelta += BigDecimal("0.01")
        }

        print("\n")
    }
}

object MSCISimulator {

    private val debug = false

    private lateinit var triggerDelta: BigDecimal
    private var daysShift = 0L

    private lateinit var secBars: TreeMap<LocalDateTime, Bar>
    private lateinit var imoexBars: TreeMap<LocalDateTime, Bar>

    fun load() {
        //secBars = CSVHistoryLoader2().load("", "SBER_140101_190801")
        secBars = CSVHistoryLoader2().load("", "GAZP_140101_190801")
        imoexBars = CSVHistoryLoader2().load("", "RI.IMOEX_140101_190801")
    }

    fun run(triggerDelta: BigDecimal, daysShift: Long): BigDecimal {
        this.triggerDelta = triggerDelta
        this.daysShift = daysShift

        var totalPnL = BigDecimal.ZERO
        var rebalanceDate = LocalDate.of(2014, 11, 1)
        while (rebalanceDate < LocalDate.of(2019, 8, 1)) {
            val pnl = exec(rebalanceDate, imoexBars, secBars)
            if (debug) {
                println(pnl.toPlainString())
            }

            totalPnL += pnl
            rebalanceDate = rebalanceDate.plus(3, ChronoUnit.MONTHS)
        }

        return totalPnL
    }

    private fun exec(
        rebalanceDate: LocalDate,
        imoexBars: TreeMap<LocalDateTime, Bar>,
        secBars: TreeMap<LocalDateTime, Bar>
    ): BigDecimal {
        val quoteDate = rebalanceDate.minusDays(daysShift)
        val periodStart = rebalanceDate.minus(3, ChronoUnit.MONTHS)

        val imoexStart = getClose(periodStart, imoexBars)
        val imoexQuote = getClose(quoteDate, imoexBars)

        val secStart = getClose(periodStart, secBars)
        val secQuote = getClose(quoteDate, secBars)

        val imoexChange = (imoexQuote - imoexStart).divide(imoexStart, 12, RoundingMode.HALF_UP)
        val secChange = (secQuote - secStart).divide(secStart, 12, RoundingMode.HALF_UP)

        val qty = BigDecimal(100000).divide(secQuote, 2, RoundingMode.HALF_UP)
        if (secChange - imoexChange > triggerDelta) {
            return qty * (getBar(rebalanceDate, secBars).low - secQuote)
        } else if (secChange - imoexChange < -triggerDelta) {
            return qty * (secQuote - getBar(rebalanceDate, secBars).high)
        }

        return BigDecimal.ZERO
    }

    private fun getClose(start: LocalDate, bars: TreeMap<LocalDateTime, Bar>): BigDecimal {
        return getBar(start, bars).close
    }

    private fun getBar(
        start: LocalDate,
        bars: TreeMap<LocalDateTime, Bar>
    ): Bar {
        val key = bars.floorKey(start.atStartOfDay())
        return bars[key]!!
    }

}