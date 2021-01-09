package backtest.orel

import backtest.Bar
import bond.BusinessCalendar
import bond.CalcYield
import com.enfernuz.quik.lua.rpc.api.messages.datasource.CreateDataSource
import com.google.common.io.Files
import common.Connector
import common.LocalCache
import common.Util
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.min
import kotlin.system.exitProcess
import java.text.NumberFormat
import java.text.DecimalFormatSymbols


fun main() {
    val analyser = OrelDebugSignalAnalyser()
    analyser.readFile()

/*
    val pnl = analyser.analyze(BigDecimal("0.29"), BigDecimal("0.24"))
    println("${pnl.toPlainString()}")
*/

    printOptimumTable(analyser)

    exitProcess(0)
}

private fun printOptimumTable(analyser: OrelDebugSignalAnalyser) {
    Connector.get() // just log before table

    val f = getDecimalFormat()

    var buySpread = BigDecimal("0.1")
    var profitDelta = BigDecimal("0.08")

    //header
    var column = profitDelta
    while (column <= BigDecimal("0.4")) {
        print(";${f.format(column)}")
        column += BigDecimal("0.01")
    }
    print("\n")


    while (buySpread <= BigDecimal("0.3")) {
        print("${f.format(buySpread)};")
        var profitDelta = BigDecimal("0.08")

        while (profitDelta <= BigDecimal("0.4")) {
            val pnl = analyser.analyze(buySpread, profitDelta)
            print("${f.format(pnl)};")

            profitDelta += BigDecimal("0.01")
        }

        print("\n")
        buySpread += BigDecimal("0.01")
    }
    print("\n")
}

private fun getDecimalFormat(): DecimalFormat {
    val decimalFormatSymbols = DecimalFormatSymbols()
    decimalFormatSymbols.decimalSeparator = ','
    return DecimalFormat("###0.00", decimalFormatSymbols)
}

private class OrelSignal
    (
    val code: String,
    val buyPrice: BigDecimal,
    val quantity: Int,
    val approxBid: BigDecimal
) {
}

class OrelDebugSignalAnalyser() {

    private val cache = HashMap<String, List<Bar>>()
    private val signals = HashMap<String, TreeMap<LocalDateTime, OrelSignal>>()

    fun readFile() {
        val lines = Files.readLines(File("logs/OrelDebug.log"), StandardCharsets.UTF_8)

        for (i in 1..lines.size - 1) {
            val line = lines[i]
            val split = line.split(";")
            val code = split[0]
            val time = LocalDateTime.parse(split[1])
            val buyPrice = BigDecimal(split[2])
            val approxBid = BigDecimal(split[3])
            val quantity = split[9].toInt()

            if (!signals.containsKey(code)) {
                signals[code] = TreeMap()
            }

            signals[code]!![time] = OrelSignal(code, buyPrice, min(quantity,1000), approxBid)
        }
    }

    fun analyze(
        buySpread: BigDecimal,
        profitDelta: BigDecimal
    ): BigDecimal {
        var totalPnl = BigDecimal.ZERO

        for (entry in signals.entries) {
            val signals = entry.value

            val classCode = getClass(entry.key)

            if (entry.key == "SU26230RMFS1") {
                continue
            }

            val bars: List<Bar> = getBars(entry.key, classCode)

            var simTime = signals.firstEntry().key
            var secPnl = BigDecimal.ZERO

            while (true) {
                val signal = signals[simTime]!!

                if (isFired(signal, buySpread)) {
                    val sellPrice = signal.buyPrice + profitDelta
                    val (sellTime, sellAmount) = sell(bars, simTime, sellPrice, signal.quantity)
                    simTime = sellTime

                    val buyAmount = signal.buyPrice * BigDecimal(signal.quantity) * BigDecimal(10)//*1000/100 упрощенно не учитываю реальный номинал
                    val feeAmount = BigDecimal("0.0003") * (buyAmount + sellAmount)
                    secPnl += sellAmount - buyAmount - feeAmount
                }
                simTime = simTime.plus(1, ChronoUnit.MINUTES)

                if (simTime > signals.lastEntry().key || simTime >= bars.last().datetime) {
                    break
                }
                simTime = signals.ceilingKey(simTime)
            }

            //println("${entry.key};${secPnl.toPlainString()}")
            totalPnl += secPnl
        }

        return totalPnl
    }

    private fun getClass(key: String): String {
        if (key.startsWith("SU")) {
            return "TQOB"
        } else if (key.startsWith("RU")) {
            return "TQCB"
        }
        throw Exception("Unknown class")
    }

    private fun sell(
        bars: List<Bar>,
        simTime: LocalDateTime,
        initialSellPrice: BigDecimal,
        quantity: Int
    ): Pair<LocalDateTime, BigDecimal> {
        var restQuantity = quantity
        var sellPrice = initialSellPrice
        var sellAmount = BigDecimal.ZERO

        var downDay = BusinessCalendar.addDays(simTime.toLocalDate(), 3)

        for (bar in bars) {
            if (bar.datetime <= simTime) {
                continue;
            }

            if (bar.datetime.toLocalDate() > downDay) {
                downDay = BusinessCalendar.addDays(downDay, 3)
                sellPrice -= BigDecimal("0.05")
            }

            if (bar.low > initialSellPrice) {
                val q = min(restQuantity, bar.volume.toInt())
                restQuantity -= q
                sellAmount += BigDecimal(q) * sellPrice * BigDecimal(10)
            } else if (bar.high > initialSellPrice) {
                restQuantity -= 1
                sellAmount += sellPrice * BigDecimal(10)
            }

            if (restQuantity <= 0) {
                return Pair(bar.datetime, sellAmount)
            }
        }

        val penaltySellPrice = bars[bars.size - 1].low.min(sellPrice) - BigDecimal("0.5")
        val penaltySellAmount = (penaltySellPrice * BigDecimal(restQuantity)) * BigDecimal(10) + sellAmount
        return Pair(bars[bars.size - 1].datetime, penaltySellAmount)
    }

    private fun isFired(signal: OrelSignal, buySpread: BigDecimal): Boolean {
        return signal.approxBid - signal.buyPrice > buySpread
    }

    private fun getBars(code: String, classCode: String): List<Bar> {
        val bars: List<Bar>
        if (cache.containsKey(code)) {
            bars = cache[code]!!
        } else {
            val rpcClient = Connector.get()
            val dataSource = Util.dataSource(
                classCode,
                code,
                CreateDataSource.Interval.INTERVAL_M1,
                rpcClient
            )
            bars = Util.toBars(dataSource)

            cache[code] = bars
        }
        return bars
    }
}