package backtest.orel

import backtest.Bar
import bond.BusinessCalendar
import com.enfernuz.quik.lua.rpc.api.messages.datasource.CreateDataSource
import com.google.common.io.Files
import common.Connector
import common.Util
import java.io.File
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import kotlin.math.max

fun main() {
    val lines = Files.readLines(File("OrelDebug.log"), StandardCharsets.UTF_8)

    val delta = BigDecimal("0.2")
    val file = File("OrelDebugAnal.csv")
    Files.append("code;time;ask;approxBid;duration;ytm;approxYtm;premium;ytmDiff;vol\n", file, StandardCharsets.UTF_8)

    for (i in 1..lines.size - 1) {
        val line = lines[i]
        val split = line.split(";")
        val code = split[0]
        val time = LocalDateTime.parse(split[1])
        val buyPrice = BigDecimal(split[2])

        val classCode = if (code.startsWith("SU")) "TQOB" else "TQCB"

        val result = OrelSignalAnalyser.analyze(classCode, code, time, buyPrice, delta)

        Files.append("$line;${result.first};${result.second}\n", file, StandardCharsets.UTF_8)

    }
}

object OrelSignalAnalyser {

    val cache = HashMap<String, List<Bar>>()

    fun analyze(
        classCode: String,
        code: String,
        time: LocalDateTime,
        buyPrice: BigDecimal,
        delta: BigDecimal
    ): Pair<BigDecimal, Int> {
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

        var sellPrice = buyPrice + delta
        var downDay = BusinessCalendar.addDays(time.toLocalDate(), 3)
        var lastPrice: BigDecimal? = null

        for (bar in bars) {
            if (bar.datetime <= time) {
                continue;
            }

            if (bar.datetime.toLocalDate() > downDay) {
                downDay = BusinessCalendar.addDays(downDay, 3)
                sellPrice -= BigDecimal("0.05")
            }


            if (bar.high > sellPrice) {
                return Pair(
                    sellPrice - buyPrice,
                    BusinessCalendar.daysBetween(time.toLocalDate(), bar.datetime.toLocalDate())
                )
            }

            lastPrice = bar.low
        }

        if (lastPrice == null) {
            return Pair(BigDecimal.ZERO.min(sellPrice - buyPrice), -1)
        }
        return Pair(lastPrice - buyPrice, -1)
    }
}