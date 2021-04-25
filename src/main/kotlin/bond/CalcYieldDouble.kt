package bond

import com.google.common.cache.CacheBuilder
import model.Bond
import java.math.BigDecimal
import java.math.RoundingMode.HALF_UP
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object CalcYieldDouble {
    private val ytmCache = CacheBuilder.newBuilder()
        .expireAfterWrite(9, TimeUnit.HOURS)
        .softValues()
        .build<String, Double>()

    fun calcAccrual(bond: Bond, dt: LocalDate): Double {
        val startDt: LocalDate = bond.couponPeriodStart(dt)

        val end: LocalDate = bond.couponPeriodEnd(dt)
        val registrationDate = bond.calcRegistrationDate(end);
        if (dt > registrationDate) {
            return 0.0
        }

        return calcAccrual(bond, startDt, dt)
    }

    fun calcAccrual(bond: Bond, from: LocalDate, to: LocalDate): Double {
        return (bond.dayCount.yearDif(from, to) * bond.nominal * bond.ratePct()).setScale(2, HALF_UP).toDouble()
    }


    private val TOLERANCE = 0.001

    //https://investprofit.info/bond-yields/
/*
    fun effectiveYTM_StepAlgo(bond: Bond, dt: LocalDate, dirtyPrice: BigDecimal): BigDecimal {
        var rate = bond.ratePct()
        var step = BigDecimal.ONE

        var cnt = 1

        val ytmMaturityDate = bond.earlyRedemptionDate ?: bond.maturityDt
        val coupons = bond.generateCoupons()

        var calcPrice = calcDirtyPriceFromYield(bond, dt, rate, coupons)

        if (dirtyPrice > calcPrice) {
            step = BigDecimal("-0.01") //если текущ цена > вычисл, значит эф дох должна быть меньше
        } else {
            step = TOLERANCE
        }

        while ((calcPrice - dirtyPrice).abs() > TOLERANCE) {
            cnt++
            rate += step
            val calcPrice2 = calcDirtyPriceFromYield(bond, dt, rate, coupons)

            if ((calcPrice - dirtyPrice).signum() != (calcPrice2 - dirtyPrice).signum()) {
                //пора обратно
                step = step.divide(BigDecimal(-10), 10, HALF_UP)
            }
            calcPrice = calcPrice2

            if (cnt > 300) {
                throw Exception("Too deep!!!")
            }
        }

        return rate
    }
*/

    fun effectiveYTM(bond: Bond, dt: LocalDate, dirtyPrice: BigDecimal): BigDecimal {
        return BigDecimal.valueOf(
                ytmCache.get(bond.code + ":" + dt.toString() + ":" + dirtyPrice.toPlainString(),
                    Callable { return@Callable effectiveYTMBinary(bond, dt, dirtyPrice.toDouble()) }))
    }

    //https://investprofit.info/bond-yields/
    fun effectiveYTMBinary(bond: Bond, dt: LocalDate, dirtyPrice: Double): Double {
        var rate = bond.ratePct().toDouble()
        var step : Double

        var cnt = 1

        val ytmMaturityDate = bond.calculationEffectiveMaturityDate(dt)
        var coupons: Map<LocalDate, BigDecimal> = bond.generateCoupons()
        coupons = coupons.filter { bond.isKnowsCoupon(it, dt) }

        var calcPrice = calcDirtyPriceFromYield(bond, dt, rate, coupons)

        if (dirtyPrice > calcPrice) {
            step = -0.01 //если текущ цена > вычисл, значит эф дох должна быть меньше
        } else {
            step = 0.01
        }


        while (Math.abs(calcPrice - dirtyPrice) > TOLERANCE) {
            val oldRate = rate
            rate += step
            val calcPrice2 = calcDirtyPriceFromYield(bond, dt, rate, coupons)

            if (calcPrice < dirtyPrice && calcPrice2 > dirtyPrice) {
                return binarySearch(bond, coupons, dt, dirtyPrice, Pair(oldRate, calcPrice), Pair(rate, calcPrice2))
            } else if (calcPrice > dirtyPrice && calcPrice2 < dirtyPrice) {
                return binarySearch(bond, coupons, dt, dirtyPrice, Pair(rate, calcPrice2), Pair(oldRate, calcPrice))
            }

            calcPrice = calcPrice2

            if (++cnt > 200) {
                throw Exception("Too deep!!!")
            }
        }

        return rate
    }

    private fun binarySearch(
        bond: Bond,
        coupons: Map<LocalDate, BigDecimal>,
        dt: LocalDate,
        price: Double,
        left: Pair<Double, Double>,
        right: Pair<Double, Double>
    ): Double {
        //берем ставку исходя из пропорции удаленности
        val testRate = left.first - (left.first - right.first) * (price - left.second) / (right.second - left.second)
        val calcPrice = calcDirtyPriceFromYield(bond, dt, testRate, coupons)

        if (abs(calcPrice - price) < TOLERANCE) {
            return testRate
        }

        if (calcPrice < price) {
            return binarySearch(bond, coupons, dt, price, Pair(testRate, calcPrice), right)
        } else {
            return binarySearch(bond, coupons, dt, price, left, Pair(testRate, calcPrice))
        }
    }

    fun calcDirtyPriceFromYield(
        bond: Bond,
        dt: LocalDate,
        rate: Double,
        coupons: Map<LocalDate, BigDecimal>
    ): Double {
        val ytmMaturityDate = bond.calculationEffectiveMaturityDate(dt)

        var total : Double = 0.0
        for (coupon in coupons.entries) {
            if (!bond.isKnowsCoupon(coupon, dt)) {
                continue
            }

            val years = ChronoUnit.DAYS.between(dt, coupon.key) / 365.0

            val yearFactor = Math.pow(1.0 + rate, years)
            total += coupon.value.toDouble() / yearFactor
        }

        val years = ChronoUnit.DAYS.between(dt, ytmMaturityDate) / 365.0
        val yearFactor = Math.pow(1.0 + rate, years)
        total += bond.nominal.toDouble() / yearFactor

        return (total * 100.0) / bond.nominal.toDouble()
    }

    fun clearCache() {
        ytmCache.invalidateAll()
    }

}