package backtest.orel

import backtest.Bar
import bond.*
import common.DBService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.system.exitProcess

fun main() {
    val start = LocalDate.of(2020, 1, 1)
    val end = LocalDate.of(2020, 5, 1)

    OrelFlightSimulator.init()

    val curveOFZ = CurveHolder.curveOFZ()
    for (bond in curveOFZ.bonds) {
        OrelFlightSimulator.fly(bond.code, start, end, BigDecimal("0.2"))
    }

    println("pnl=${OrelFlightSimulator.pnl}")
    println("done")

    exitProcess(0)
}

object OrelFlightSimulator {
    var currentTime = LocalDateTime.now()
    var pnl = BigDecimal.ZERO

    fun fly(code: String, start: LocalDate, end: LocalDate, ovchinka: BigDecimal) {
        val curveBuilder = CurveBuilder(StakanCSVSimulator())

        val curveOFZ = CurveHolder.curveOFZ()

        QuoteCSVService.load(code)
        val bond = DBService.getBond(code)

        currentTime = start.atTime(10, 0)
        val stopTime = end.atTime(19, 0)
        while (currentTime < stopTime) {
            val bar = QuoteCSVService.get(code, currentTime)

            if (bar != null) {
                val settleDt = BusinessCalendar.addDays(currentTime.toLocalDate(), 1)
                curveBuilder.build(curveOFZ, settleDt)

                val nkd = CalcYield.calcAccrual(bond, settleDt)
                val nkdToPrice = (nkd * BigDecimal(100)).divide(bond.nominal, 12, RoundingMode.HALF_UP)

                val dirtyPriceSell = bar.high + nkdToPrice
                val ytm = CalcYield.effectiveYTM(bond, settleDt, dirtyPriceSell)
                val durationDays = CalcDuration.durationDays(bond, settleDt, ytm, dirtyPriceSell)

                val approx = curveOFZ.approx(durationDays)

                val approxDirtyPrice = CalcYield.calcDirtyPriceFromYield(bond, settleDt, BigDecimal(approx), bond.generateCoupons())
                val delta = approxDirtyPrice - dirtyPriceSell
                val deltaYTM = (BigDecimal.valueOf(approx) - ytm).setScale(5, RoundingMode.HALF_UP)

                if (delta > ovchinka) {
                    val buyDtm = currentTime
                    val success = goUntilSuccess(bar.high, bar.high + ovchinka, code, stopTime)

                    val durYears = durationDays.divide(BigDecimal(365), 2, RoundingMode.HALF_UP)
                    val daysInTrade = BusinessCalendar.daysBetween(buyDtm.toLocalDate(), currentTime.toLocalDate())
                    println("$code;$buyDtm;$currentTime;$success;${delta.setScale(2, RoundingMode.HALF_UP).toPlainString()};$deltaYTM;$durYears;$daysInTrade")
                    pnl += success - BigDecimal("0.06")
                }
            }

            var nextTime = addMinute(currentTime)

            if (nextTime.toLocalDate() > currentTime.toLocalDate()) {
                CalcDuration.clearCache()
                CalcYield.clearCache()
            }
            currentTime = nextTime
        }

    }

    private fun goUntilSuccess(buyPrice: BigDecimal, sellPrice: BigDecimal, code: String, stopTime: LocalDateTime): BigDecimal {
        val dt = currentTime.toLocalDate()
        var descreaseDate = BusinessCalendar.addDays(dt, 3)
        var currentSellPrice = sellPrice
        var lastBar: Bar? = null

        while (currentTime < stopTime) {
            currentTime = addMinute(currentTime)

            if (currentTime.hour == 10 && currentTime.minute == 0) {
                if (currentTime.toLocalDate() == descreaseDate) {
                    currentSellPrice = sellPrice - BigDecimal("0.1")
                    descreaseDate = BusinessCalendar.addDays(descreaseDate, 3)
                }
            }

            val bar = QuoteCSVService.get(code, currentTime)

            if (bar != null) {
                lastBar = bar
                if (bar.high >= currentSellPrice) {
                    return currentSellPrice - buyPrice
                }
            }
        }

        return lastBar!!.close - buyPrice
    }

    private fun addMinute(dtm: LocalDateTime): LocalDateTime {
        var nextTime = dtm.plus(1, ChronoUnit.MINUTES)
        if (nextTime.hour == 19 && nextTime.minute == 0) {
            nextTime = nextTime.plus(15, ChronoUnit.HOURS)
        }
        return nextTime
    }

    fun init() {
        val curveOFZ = CurveHolder.curveOFZ()
        for (bond in curveOFZ.bonds) {
            QuoteCSVService.load(bond.code)
        }

    }
}