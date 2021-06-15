package backtest

import robot.spreadler.Pastuh
import java.lang.Integer.min
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

fun main() {
    //SpreadlerTickSimulation.securityCode = "RU000A0JXN21"
    //SpreadlerTickSimulation.securityCode = "RU000A101H43"
    SpreadlerTickSimulation.securityCode = "RU000A100JH0"
    //SpreadlerTickSimulation.securityCode = "RU000A0JXQ93"
    //SpreadlerTickSimulation.securityCode = "RU000A100V79"
    SpreadlerTickSimulation.fly()
}

object SpreadlerTickSimulation {
    var securityCode = ""
    var nominal = BigDecimal("1000")
    var spread = BigDecimal("0.3")

    var debug = false

    fun fly() {
        println(securityCode)
        val bars = CSVHistoryLoader().load("", securityCode)
        val imoexBars = CSVHistoryLoader().load("", "IMOEX")
        val ticks = CSVTickLoader().load("", securityCode)
        val startDay = LocalDateTime.of(2019, 10, 1, 0, 0)

        val resultMap = TreeMap<BigDecimal, TreeMap<BigDecimal, BigDecimal>>()

        var sellPriceTooFar = BigDecimal("1.5")
        while (sellPriceTooFar > BigDecimal.ZERO) {
            var sellPriceDescreaseStep = sellPriceTooFar
            while (sellPriceDescreaseStep > BigDecimal.ZERO) {
                val spreadler = SpreadlerState(true, 30, BigDecimal.ZERO)

                flyWithParams(ticks, startDay, spreadler, bars, imoexBars, sellPriceTooFar, sellPriceDescreaseStep)

                val pnl = pnl(spreadler) - calcFee(spreadler) + spreadler.nkd
                println("$pnl ($sellPriceTooFar, $sellPriceDescreaseStep)")
                if (!resultMap.containsKey(sellPriceTooFar)) {
                    resultMap.put(sellPriceTooFar, TreeMap())
                }
                resultMap[sellPriceTooFar]!![sellPriceDescreaseStep] = pnl

                sellPriceDescreaseStep -= BigDecimal("0.02")
            }
            sellPriceTooFar -= BigDecimal("0.1")
        }

        printResultMapCSV(resultMap)
    }

    private fun printResultMapCSV(resultMap: java.util.TreeMap<BigDecimal, java.util.TreeMap<BigDecimal, BigDecimal>>) {
        var text = "0;"
        val first = resultMap.keys.last()
        val sorted = resultMap[first]!!.keys.sorted()
        val format = DecimalFormat("0.00")
        format.decimalFormatSymbols.decimalSeparator = ','
        text += sorted.joinToString(";") { format.format(it) }
        text += "\n"

        for (entry in resultMap.entries) {
            text += format.format(entry.key) + ";"
            for (key in sorted) {
                if (entry.value.containsKey(key)) {
                    text += format.format(entry.value[key]!!) + ";"
                } else {
                    text += ";"
                }
            }
            text += "\n"
        }

        println(text)
    }

    private fun calcFee(spreadler: SpreadlerState) =
            spreadler.sellAmount * BigDecimal("0.0002") + spreadler.buyAmount * BigDecimal("0.0002")

    private fun flyWithParams(ticks: ArrayList<Tick>, startDay: LocalDateTime, spreadler: SpreadlerState, bars: ArrayList<Bar>, imoexBars: ArrayList<Bar>, sellPriceTooFar: BigDecimal, sellPriceDescreaseStep: BigDecimal) {
        var tickIdx = 0
        tickIdx = moveTickPointer(ticks, tickIdx, startDay)

        val stopDate = LocalDateTime.of(2020, 4, 10, 0, 0)
        while (ticks[tickIdx].datetime < stopDate) {
            tickIdx = simulateDay(spreadler, bars, ticks, imoexBars, tickIdx, sellPriceTooFar, sellPriceDescreaseStep)
        }
    }

    private fun pnl(spreadler: SpreadlerState): BigDecimal {
        var balance = getBalance(spreadler)
        val balanceValue = BigDecimal(balance) * nominal * spreadler.buyPrice / BigDecimal(100)
        val pnl = spreadler.sellAmount - spreadler.buyAmount + balanceValue
        return pnl
    }

    private fun getBalance(spreadler: SpreadlerState): Int {
        if (spreadler.buy) {
            return spreadler.quantity - spreadler.restQuantity
        } else {
            return spreadler.restQuantity
        }
    }

    private fun moveTickPointer(ticks: ArrayList<Tick>, tickIdx: Int, localDateTime: LocalDateTime): Int {
        for (i in tickIdx..ticks.size - 1) {
            if (ticks[i].datetime > localDateTime) {
                return i
            }
        }
        throw Exception("cant find")
    }

    private fun moveBarPointer(bars: ArrayList<Bar>, point: Int, localDateTime: LocalDateTime): Int {
        for (i in point..bars.size - 1) {
            if (bars[i].datetime > localDateTime) {
                return i
            }
        }
        throw Exception("cant find")
    }

    class SpreadlerState(var buy: Boolean, var quantity: Int, var buyPrice: BigDecimal) {
        var restQuantity = quantity
        var buyAmount = BigDecimal.ZERO
        var sellAmount = BigDecimal.ZERO
        var sellPrice = BigDecimal.ZERO
        var nkd = BigDecimal.ZERO
    }

    private fun simulateDay(spreadler: SpreadlerState, bars: ArrayList<Bar>, ticks: ArrayList<Tick>, imoexBars: ArrayList<Bar>, startTickIdx: Int, sellPriceTooFar: BigDecimal, sellPriceDescreaseStep: BigDecimal): Int {
        var day = ticks[startTickIdx].datetime.truncatedTo(ChronoUnit.DAYS)
        var barTodayStartIdx = moveBarPointer(bars, 0, day)

        val (barStart, barEnd) = Pastuh.findPeriodOfDaysBefore(bars, 2, barTodayStartIdx) //barEnd = barTodayStartIdx -1

        val optimalMaxBuyPrice = Pastuh.optimalMaxBuyPrice(bars.subList(barStart, barEnd + 1), spread, 2)
        spreadler.buyPrice = optimalMaxBuyPrice
        if (!spreadler.buy && (spreadler.sellPrice > optimalMaxBuyPrice + sellPriceTooFar + spread)) {
            spreadler.sellPrice -= sellPriceDescreaseStep
        }

        //var moexDayBars: ArrayList<Bar> = aggregateToDayBars(imoexBars, day, 11)
        val moexStraz = MoexStraz()
        moexStraz.init(imoexBars, day)

        var idx = startTickIdx
        var buyBun = false
        var moexIdx = 0
        while (idx < ticks.size) {
            val tick = ticks[idx]
            if (tick.datetime > day.plusDays(1).truncatedTo(ChronoUnit.DAYS)) {
                break
            }

            if (spreadler.buy && !buyBun) {
                moexIdx = moveBarPointer(imoexBars, moexIdx, tick.datetime.minus(1, ChronoUnit.MINUTES))
                if (imoexBars[moexIdx].open < moexStraz.buyBunLevel) {
                    if (debug) println("${tick.datetime} BUY BUN")
                    buyBun = true

                    convertToSale(spreadler)
                }
            }

            if (spreadler.buy && !buyBun) {
                if (tick.last < spreadler.buyPrice) {
                    val boughtVolume = min(tick.volume, spreadler.restQuantity)
                    if (boughtVolume < spreadler.restQuantity) {
                        spreadler.restQuantity -= boughtVolume
                        spreadler.buyAmount += BigDecimal(boughtVolume) * spreadler.buyPrice * nominal / BigDecimal("100")
                    } else {
                        spreadler.buyAmount += BigDecimal(spreadler.restQuantity) * spreadler.buyPrice * nominal / BigDecimal("100")

                        spreadler.buy = false
                        spreadler.restQuantity = spreadler.quantity
                        spreadler.sellPrice = spreadler.buyPrice + spread
                    }
                    val pnl = pnl(spreadler)
                    if (debug) println("${tick.datetime} pnl $pnl    : buy  ${spreadler.buyPrice} - $boughtVolume")
                }
            } else if (!spreadler.buy) {
                if (tick.last > spreadler.sellPrice) {
                    val sellVolume = min(tick.volume, spreadler.restQuantity)
                    if (sellVolume < spreadler.restQuantity) {
                        spreadler.restQuantity -= sellVolume
                        spreadler.sellAmount += BigDecimal(sellVolume) * spreadler.sellPrice * nominal / BigDecimal("100")
                    } else {
                        spreadler.sellAmount += BigDecimal(spreadler.restQuantity) * spreadler.sellPrice * nominal / BigDecimal("100")

                        spreadler.buy = true
                        spreadler.restQuantity = spreadler.quantity
                    }
                    val pnl = pnl(spreadler)
                    if (debug) println("${tick.datetime} pnl $pnl    : sell ${spreadler.sellPrice} - $sellVolume")
                }
            }

            idx++
        }

        //240 усреднил число торг дней
        val nkd = BigDecimal(getBalance(spreadler)) * nominal * BigDecimal("0.07") / BigDecimal(240)
        spreadler.nkd += nkd

        return idx
    }

    private fun aggregateToDayBars(imoexBars: ArrayList<Bar>, day: LocalDateTime, days: Int): ArrayList<Bar> {
        val result = ArrayList<Bar>()
        val moveBarPointer = moveBarPointer(imoexBars, 0, day.minusDays((days + 1).toLong()))



        while (moveBarPointer < imoexBars.size) {
            val bar = imoexBars[moveBarPointer]
            if (bar.datetime > day) {
                return result;
            }
        }
        TODO("Not yet implemented")
    }

    private fun convertToSale(spreadler: SpreadlerState) {
        if (spreadler.restQuantity < spreadler.quantity) {
            spreadler.restQuantity = spreadler.quantity - spreadler.restQuantity
            spreadler.sellPrice = spreadler.buyPrice
            spreadler.buy = false
        }
    }


    private fun boughtVolume(bar: Bar, spreadler: SpreadlerState): Long {
        var boughtVolume = 0L
        if (bar.high < spreadler.buyPrice) {
            boughtVolume = bar.volume
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

    private fun sellVolume(bar: Bar, spreadler: SpreadlerState): Long {
        var volume = 0L
        if (bar.low > spreadler.sellPrice) {
            volume = bar.volume
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