package robot.strazh

import backtest.Bar
import com.enfernuz.quik.lua.rpc.api.messages.GetParamEx
import com.enfernuz.quik.lua.rpc.api.messages.datasource.CreateDataSource
import com.enfernuz.quik.lua.rpc.api.messages.datasource.Size
import com.enfernuz.quik.lua.rpc.api.messages.datasource.T
import common.Connector
import common.Util
import org.slf4j.LoggerFactory
import robot.SpreadlerRunner
import robot.Telega
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class MoexStrazh {
    object holder {
        val instance = MoexStrazh()
    }

    private val log = LoggerFactory.getLogger(this::class.java)
    private var initDay: LocalDate? = null
    private var dataSource: CreateDataSource.Result? = null

    private var buyBunLevel = BigDecimal.ZERO
    private var buyApproved = true

    @Synchronized
    fun initToday() {
        val rpcClient = Connector.get();
        synchronized(rpcClient) {
            dataSource = Util.dataSource("INDX", "IMOEX", CreateDataSource.Interval.INTERVAL_D1, rpcClient)

            val size = rpcClient.datasource_Size(Size.Args(dataSource!!.datasourceUUID))
            val dataSourceTime = rpcClient.datasource_T(T.Args(dataSource!!.datasourceUUID, size))
            initDay = LocalDate.now()

            val bars = Util.toBars(dataSource!!)

            buyBunLevel = calculateDangerLevel(bars)

            buyApproved = checkLastIndexValue(bars)
        }
    }

    private fun checkLastIndexValue(bars: ArrayList<Bar>) =
            bars[bars.size - 1].close > buyBunLevel

    fun calculateDangerLevel(bars: ArrayList<Bar>): BigDecimal {
        var highestAvg = BigDecimal.ZERO
        val daysDepth = 5
        val avgHighDays = 5
        for (i in 0..daysDepth - 1) {
            var s = BigDecimal.ZERO
            for (j in 0..avgHighDays - 1) {
                s += bars[bars.size - (i + j) - 1].high
            }
            val avg = s / BigDecimal(avgHighDays)

            if (avg > highestAvg) {
                highestAvg = avg
            }
        }

        val yesterdayClose: BigDecimal = yesterdayClose(bars)

        val periodLevel = highestAvg * BigDecimal("0.96")
        val todayLevel = yesterdayClose * BigDecimal("0.98")

        log.info("закрытие вчера $yesterdayClose")
        log.info("максимальный средний уровень максимума дня $highestAvg")

        val max = periodLevel.max(todayLevel)
        log.info("опасный уровень унижения $max")
        return max
    }

    private fun yesterdayClose(bars: ArrayList<Bar>): BigDecimal {
        val yesterdayClose: BigDecimal
        if (bars.last().datetime > LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)) {
            yesterdayClose = bars[bars.size - 2].close //предпоследний день, если сейчас больше 10, то это вчера
        } else {
            yesterdayClose = bars.last().close
        }
        return yesterdayClose
    }

    fun isDayOpen(): Boolean {
        return initDay != null && initDay!! == LocalDate.now()
    }

    fun isBuyApproved(): Boolean {
        return isDayOpen() && buyApproved
    }

    fun check() {
        if (!isDayOpen()) {
            initToday()
        }

        if (!buyApproved) {
            return
        }

        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            val ex = rpcClient.qlua_getParamEx(GetParamEx.Args("INDX", "IMOEX", "CURRENTVALUE"))
            val d = BigDecimal(ex.paramValue)
            buyApproved = (d > buyBunLevel)
        }
/*
        val bars = Util.toBars(dataSource!!)
        buyApproved = checkLastIndexValue(bars)
*/

        if (!buyApproved) {
            val msg = "MOEX упал, снимаю покупки"
            Telega.Holder.get().sendMessage(msg)
            log.error(msg)

            //TODO конвертация в продажи... надо подумать
            SpreadlerRunner.stopBuy()
        }
    }
}