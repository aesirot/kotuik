package backtest

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class MoexStraz() {

    val avgHighDays = 5
    val daysDepth = 5

    var buyBunLevel = BigDecimal.ZERO

    fun init(imoexBars: List<Bar>, time: LocalDateTime) {
        var timeIdx = moveToIdxBefore(imoexBars, time)


        var idx = timeIdx
        var highList = Array<BigDecimal?>(avgHighDays + daysDepth - 1) { null }
        var high = imoexBars[idx].high
        var day = imoexBars[idx].datetime.truncatedTo(ChronoUnit.DAYS)
        var dayIdx = 0
        while (idx > 0) {
            val bar = imoexBars[idx]

            if (bar.datetime.truncatedTo(ChronoUnit.DAYS) < day) {
                highList[dayIdx] = high
                high = BigDecimal.ZERO

                day = bar.datetime.truncatedTo(ChronoUnit.DAYS)
                dayIdx++
                if (dayIdx >= avgHighDays + daysDepth - 1) {
                    break
                }
            }

            if (high < bar.high) {
                high = bar.high
            }

            idx--
        }

        highList = highList.reversed().toTypedArray()

        var highestAvg = BigDecimal.ZERO
        for (i in 0..daysDepth-1) {
            var s = BigDecimal.ZERO
            for (j in 0..avgHighDays-1) {
                s += highList[highList.size-(i+j)-1]!!
            }
            var avg = s / BigDecimal(avgHighDays)

            if (avg > highestAvg) {
                highestAvg = avg
            }
        }

        var yesterdayCloseIdx = moveToIdxBefore(imoexBars, time.truncatedTo(ChronoUnit.DAYS))
        var yesterdayClose = imoexBars[yesterdayCloseIdx].close

        val periodLevel = highestAvg * BigDecimal("0.96")
        val todayLevel = yesterdayClose * BigDecimal("0.98")

        buyBunLevel = periodLevel.max(todayLevel)
    }

    private fun moveToIdxBefore(imoexBars: List<Bar>, time: LocalDateTime): Int {
        var timeIdx = 0
        while (timeIdx < imoexBars.size && imoexBars[timeIdx].datetime < time) {
            timeIdx++
        }
        timeIdx--
        return timeIdx
    }

}