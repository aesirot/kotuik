package backtest

import java.lang.Integer.max
import java.lang.Integer.min
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.ArrayList

fun main() {
    SpreadlerSimulation.securityCode = "RU000A0JXQ93"
    //SpreadlerSimulation.securityCode = "RU000A100V79"
    SpreadlerSimulation.fly()
}

object SpreadlerSimulation {
    var securityCode = ""
    var nominal = BigDecimal("1000")
    var spread = BigDecimal("0.3")

    fun fly() {
        val bars = CSVHistoryLoader().load("", securityCode)
        var point = 0
        for (i in 0..bars.size - 1) {
            if (bars[i].datetime > LocalDateTime.of(2019, 2, 1, 0, 0)) {
                point = i
                break
            }
        }

        val spreadler = SpreadlerState(true, 30, BigDecimal.ZERO)
        while (point < bars.size) {
            point = simulateDay(spreadler, bars, point)
        }

        var balance = 0
        if (spreadler.buy) {
            balance = spreadler.quantity - spreadler.restQuantity
        } else {
            balance = spreadler.restQuantity
        }
        println(spreadler.sellAmount - spreadler.buyAmount + BigDecimal(balance) * nominal * spreadler.buyPrice / BigDecimal(100))
        println("Fee")
        println(spreadler.sellAmount * BigDecimal("0.0005") + spreadler.buyAmount * BigDecimal("0.0005"))

    }

    class SpreadlerState(var buy: Boolean, var quantity: Int, var buyPrice: BigDecimal) {
        var restQuantity = quantity
        var buyAmount = BigDecimal.ZERO
        var sellAmount = BigDecimal.ZERO
        var sellPrice = BigDecimal.ZERO
    }

    private fun simulateDay(spreadler: SpreadlerState, bars: ArrayList<Bar>, startPoint: Int): Int {
        var day = bars[startPoint].datetime.truncatedTo(ChronoUnit.DAYS)

        var preparationPeriodStart = findDaysBeforeIndex(bars, startPoint, 2)

        val optimalMaxBuyPrice = Pastuh.optimalMaxBuyPrice(bars.subList(preparationPeriodStart, startPoint - 1), spread, 2)
        spreadler.buyPrice = optimalMaxBuyPrice
        if (!spreadler.buy && spreadler.sellPrice > optimalMaxBuyPrice+BigDecimal("0.8")) {
            spreadler.sellPrice -= BigDecimal("0.5")
        }

        var point = startPoint
        while (point < bars.size) {
            val bar = bars[point]
            if (bar.datetime > day.plusDays(1).truncatedTo(ChronoUnit.DAYS)) {
                return point
            }

            if (spreadler.buy) {
                if (bar.low < spreadler.buyPrice) {
                    val boughtVolume = boughtVolume(bar, spreadler)
                    println("${bar.datetime} buy ${spreadler.buyPrice} - ${min(boughtVolume, spreadler.restQuantity)}")
                    if (boughtVolume < spreadler.restQuantity) {
                        spreadler.restQuantity -= boughtVolume
                        spreadler.buyAmount += BigDecimal(boughtVolume) * spreadler.buyPrice * nominal / BigDecimal("100")
                    } else {
                        spreadler.buyAmount += BigDecimal(spreadler.restQuantity) * spreadler.buyPrice * nominal / BigDecimal("100")

                        spreadler.buy = false
                        spreadler.restQuantity = spreadler.quantity
                        spreadler.sellPrice = spreadler.buyPrice + spread
                    }
                }
            } else {
                if (bar.high > spreadler.sellPrice) {
                    val sellVolume = sellVolume(bar, spreadler)
                    println("${bar.datetime} sell ${spreadler.sellPrice} -  ${min(sellVolume, spreadler.restQuantity)}")
                    if (sellVolume < spreadler.restQuantity) {
                        spreadler.restQuantity -= sellVolume
                        spreadler.sellAmount += BigDecimal(sellVolume) * spreadler.sellPrice * nominal / BigDecimal("100")
                    } else {
                        spreadler.sellAmount += BigDecimal(spreadler.restQuantity) * spreadler.sellPrice * nominal / BigDecimal("100")

                        spreadler.buy = true
                        spreadler.restQuantity = spreadler.quantity
                    }
                }
            }
            point++
        }
        return point
    }

    private fun findDaysBeforeIndex(bars: ArrayList<Bar>, currentIndex: Int, dayLag: Int): Int {
        var periodStartIndex = 0
        var prevDay = bars[currentIndex].datetime.truncatedTo(ChronoUnit.DAYS)
        val minIdx = max(currentIndex - dayLag * 9 * 60, 0)
        var days = 0
        for (i in currentIndex - 1 downTo minIdx) {
            val d = bars[i].datetime.truncatedTo(ChronoUnit.DAYS)
            if (d < prevDay) {
                prevDay = d
                days++
            }
            if (days > dayLag) {
                periodStartIndex = i
                break
            }
        }
        return periodStartIndex
    }

    private fun boughtVolume(bar: Bar, spreadler: SpreadlerState): Int {
        var boughtVolume = 0
        if (bar.high < spreadler.buyPrice) {
            boughtVolume = bar.volume.toInt()
        } else {
            if (bar.open < spreadler.buyPrice) {
                boughtVolume += 1
            }
            if (bar.low < spreadler.buyPrice && bar.low != bar.open) {
                boughtVolume += 1
            }
            if (bar.close < spreadler.buyPrice) {
                boughtVolume += 1
            }
        }
        return boughtVolume
    }

    private fun sellVolume(bar: Bar, spreadler: SpreadlerState): Int {
        var volume = 0
        if (bar.low > spreadler.sellPrice) {
            volume = bar.volume.toInt()
        } else {
            if (bar.open > spreadler.sellPrice) {
                volume += 1
            }
            if (bar.high > spreadler.sellPrice && bar.high != bar.open) {
                volume += 1
            }
            if (bar.close > spreadler.sellPrice) {
                volume += 1
            }
        }
        return volume
    }
}