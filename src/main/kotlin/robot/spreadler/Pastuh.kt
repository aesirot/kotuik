package robot.spreadler

import backtest.Bar
import com.enfernuz.quik.lua.rpc.api.messages.datasource.CreateDataSource
import common.Connector
import common.Util
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

object Pastuh {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun adjustToday(spreadler: SpreadlerBond) {
        val rpcClient = Connector.get()
        val dataSource = Util.dataSource(spreadler.classCode, spreadler.securityCode, CreateDataSource.Interval.INTERVAL_M1, rpcClient)
        val bars = Util.toBars(dataSource)

        val (startIdx, endIdx) = findPeriodOfDaysBefore(bars, 2)

        val calcBuyPrice = optimalMaxBuyPrice(bars.subList(startIdx, endIdx + 1), spreadler.minSellSpread, 2) //to index не включительно

        if (spreadler.maxBuyPrice.compareTo(calcBuyPrice) != 0) {
            log.info("${spreadler.id} двигаю цену с ${spreadler.maxBuyPrice} на $calcBuyPrice")
            spreadler.maxBuyPrice = calcBuyPrice

            if (!spreadler.buyStage) {
                if (spreadler.buyPrice + spreadler.minSellSpread > calcBuyPrice + BigDecimal("0.9")) {
                    log.warn("${spreadler.id} СНИЖАЮ ЦЕНУ ПРОДАЖИ  ${spreadler.buyPrice} на $calcBuyPrice")
                    spreadler.buyPrice -= BigDecimal("0.6")
                }
            }

            SpreadlerConfigurator.save()
        } else {
            log.info("${spreadler.id} цена прежняя")
        }
    }


    fun optimalMaxBuyPrice(bars: List<Bar>, delta: BigDecimal, scale: Int): BigDecimal {

        val sellAmount = TreeMap<BigDecimal, Int>(Collections.reverseOrder())
        val buyAmount = TreeMap<BigDecimal, Int>()

        //алгоритм пытается работать на минутках, тиковые данные его бы улучшили
        val integral4SellCalc = TreeMap<BigDecimal, Int>(Collections.reverseOrder()) //по этим уровням ТОЧНО купили бы столько
        val integral4BuyCalc = TreeMap<BigDecimal, Int>() //по этим уровням ТОЧНО продали бы столько
        for (bar in bars) {
            if (bar.volume > 1) {
                add(sellAmount, bar.open, 1)
                add(buyAmount, bar.open, 1)

                add(sellAmount, bar.close, 1)
                add(buyAmount, bar.close, 1)

                var filledVol = 2
                if (bar.low != bar.open && bar.low != bar.close) {
                    add(sellAmount, bar.low, 1)
                    add(buyAmount, bar.low, 1)
                    filledVol++
                }

                if (bar.high != bar.open && bar.high != bar.close) {
                    add(sellAmount, bar.high, 1)
                    add(buyAmount, bar.high, 1)
                    filledVol++
                }

                // sellAmount - при анализе верхней границы (по которой планируем продавать, занижаем, те
                // интерпретируем с бар с пессимистичной точки зрения)
                add(sellAmount, bar.low, bar.volume.toInt() - filledVol)
                // buyAmount - при анализе нижней границы (по которой планируем покупать, завышаем, те
                // интерпретируем с бар с пессимистичной точки зрения)
                add(buyAmount, bar.high, bar.volume.toInt() - filledVol)
            } else {
                // все равно только 1 шт, так что бар слипся
                add(sellAmount, bar.high, bar.volume.toInt())
                add(buyAmount, bar.low, bar.volume.toInt())
            }
        }

        val step = step(scale)
        integral(sellAmount, integral4SellCalc, -step) // c минимальной цены суммируем объем
        integral(buyAmount, integral4BuyCalc, step) // c максимальной цены суммируем объем

        // ищем верхнюю границу выгодного разрыва. Это цена А
        // А - дельта = цена Б , нижняя граница разрыва
        // ищем такие А и Б, чтобы объем по цене больше А, был наиболее близок к объему ниже Б
        // это условие максимизирует А и Б и позволяет встать "посередине"
        // при этом очевидно, что большой объем А, при маленьком Б (и наоборот) совершенно не выгодны

        // ищем тупым перебором. я опасаюсь, что там будет несколько локальных минимумов
        val min = buyAmount.firstKey()
        val max = sellAmount.firstKey()
        val optimum = find(integral4BuyCalc, integral4SellCalc, delta, scale, min, max)

        return optimum - delta
    }

    private fun find(integral4BuyCalc: TreeMap<BigDecimal, Int>,
                     integral4SellCalc: TreeMap<BigDecimal, Int>,
                     delta: BigDecimal,
                     scale: Int, min: BigDecimal, max: BigDecimal): BigDecimal {
        val step = step(scale)
        var current = max
        var maxFuncValue: Int? = null
        var optimumPriceMin: BigDecimal? = null
        var optimumPriceMax: BigDecimal? = null

        //выколотый случай - вообще не было такого широкого спреда - отступаем от минимума спред и не играем
        //почему от минимума - на случай если на продажу просто плита, которая 1 раз отступила, а на покупку 0
        if (max < min + delta) {
            return min - delta
        }

        while (current >= min + delta) {
            val functionValue = priceFunction(integral4BuyCalc, current, integral4SellCalc, delta)
            if (maxFuncValue == null || functionValue >= maxFuncValue) {
                if (maxFuncValue == null || functionValue > maxFuncValue) {
                    optimumPriceMax = current
                }
                optimumPriceMin = current

                maxFuncValue = functionValue
            }

            current -= step
        }

        val optimumPriceDownFromMax = optimumPriceMax!! - delta
        if (optimumPriceDownFromMax > optimumPriceMin!!) {
            log.info("широкий разрыв оптимума от ${optimumPriceMin.toPlainString()}" +
                    " до ${optimumPriceMax.toPlainString()} - берем ${optimumPriceDownFromMax.toPlainString()}")
            return optimumPriceDownFromMax
        }

        return optimumPriceMin!!
    }

    private fun step(scale: Int): BigDecimal {
        val step = BigDecimal("0.1").pow(scale)
        return step
    }

    private fun priceFunction(integral4BuyCalc: TreeMap<BigDecimal, Int>, price: BigDecimal, integral4SellCalc: TreeMap<BigDecimal, Int>, delta: BigDecimal): Int {
        val sellKey = integral4SellCalc.floorKey(price);
        val sell = if (sellKey != null) integral4SellCalc[sellKey]!! else 0

        val buyKey = integral4BuyCalc.floorKey(price - delta);
        val buy = if (buyKey != null) integral4BuyCalc[buyKey]!! else 0

        return Math.min(buy, sell)
    }

    private fun integral(amount: TreeMap<BigDecimal, Int>, integralAmount: TreeMap<BigDecimal, Int>, step: BigDecimal) {
        var integral = 0
        for (entry in amount) {
            integral += entry.value
            // смещение нужно, т.к. при точном равенстве цен, считаем, что сработает чужая заявка
            integralAmount[entry.key + step] = integral
        }
    }

    private fun add(priceMap: TreeMap<BigDecimal, Int>, price: BigDecimal, volume: Int) {
        if (priceMap.containsKey(price)) {
            priceMap.put(price, priceMap.get(price)!! + volume)
        } else {
            priceMap.put(price, volume)
        }
    }

    /**
     * возвращает индексы периода - первый бар за dayLag до сегодня (или дня currentIndex) и последний бар за вчера (или currentIndex)
     *
     * Примеры результата
     * dayLag 1 - (первый бар вчера, последний бар вчера)
     * dayLag 2 - (первый бар позавчера, последний бар вчера)
     */
    fun findPeriodOfDaysBefore(bars: List<Bar>, dayLag: Int, todayStartIdx: Int = -1): Pair<Int, Int> {
        var day: LocalDateTime

        var idx: Int
        if (todayStartIdx == -1) {
            //находим последний бар вчерашнего дня
            day = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)
            idx = 0
            for (i in bars.size - 1 downTo 0) {
                if (bars[i].datetime < day) {
                    idx = i
                    break
                }
            }
        } else {
            day = bars[todayStartIdx].datetime.truncatedTo(ChronoUnit.DAYS)
            idx = todayStartIdx - 1
        }

        var days = 0
        for (i in idx - 1 downTo 0) {
            val d = bars[i].datetime.truncatedTo(ChronoUnit.DAYS)
            if (d < day) {
                day = d
                days++
            }
            if (days > dayLag) {
                return Pair(i + 1, idx)
            }
        }

        return Pair(0, idx)
    }

}