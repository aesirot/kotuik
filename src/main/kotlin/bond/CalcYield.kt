package bond

import com.google.common.cache.CacheBuilder
import model.Bond
import org.nevec.rjm.BigDecimalMath
import java.math.BigDecimal
import java.math.RoundingMode.HALF_UP
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

object CalcYield {
    private val ytmCache = CacheBuilder.newBuilder()
        .expireAfterWrite(9, TimeUnit.HOURS)
        .softValues()
        .build<String, BigDecimal>()

    fun calcAccrual(bond: Bond, dt: LocalDate): BigDecimal {
        val startDt: LocalDate = bond.couponPeriodStart(dt)

        val end: LocalDate = bond.couponPeriodEnd(dt)
        val registrationDate = bond.calcRegistrationDate(end);
        if (dt > registrationDate) {
            return BigDecimal.ZERO
        }

        return calcAccrual(bond, startDt, dt)
    }

    fun calcAccrual(bond: Bond, from: LocalDate, to: LocalDate): BigDecimal {
        return (bond.dayCount.yearDif(from, to) * bond.nominal * bond.ratePct()).setScale(2, HALF_UP)
    }


    private val TOLERANCE = BigDecimal("0.001")

    //https://investprofit.info/bond-yields/
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

    fun effectiveYTM(bond: Bond, dt: LocalDate, dirtyPrice: BigDecimal): BigDecimal {
        return ytmCache.get(bond.code + ":" + dt.toString() + ":" + dirtyPrice.toPlainString(),
            Callable { return@Callable effectiveYTMBinary(bond, dt, dirtyPrice) })
    }

    //https://investprofit.info/bond-yields/
    fun effectiveYTMBinary(bond: Bond, dt: LocalDate, dirtyPrice: BigDecimal): BigDecimal {
        var rate = bond.ratePct()
        var step = BigDecimal.ONE

        var cnt = 1

        val ytmMaturityDate = bond.calculationEffectiveMaturityDate(dt)
        var coupons: Map<LocalDate, BigDecimal> = bond.generateCoupons()
        coupons = coupons.filter { bond.isKnowsCoupon(it, dt) }

        var calcPrice = calcDirtyPriceFromYield(bond, dt, rate, coupons)

        if (dirtyPrice > calcPrice) {
            step = BigDecimal("-0.01") //если текущ цена > вычисл, значит эф дох должна быть меньше
        } else {
            step = BigDecimal("0.01")
        }


        while ((calcPrice - dirtyPrice).abs() > TOLERANCE) {
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
        price: BigDecimal,
        left: Pair<BigDecimal, BigDecimal>,
        right: Pair<BigDecimal, BigDecimal>
    ): BigDecimal {
        //берем ставку исходя из пропорции удаленности
        val testRate = left.first - (left.first - right.first) * (price - left.second).divide(
            right.second - left.second,
            12,
            HALF_UP
        )
        val calcPrice = calcDirtyPriceFromYield(bond, dt, testRate, coupons)

        if ((calcPrice - price).abs() < TOLERANCE) {
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
        rate: BigDecimal,
        coupons: Map<LocalDate, BigDecimal>
    ): BigDecimal {
        val ytmMaturityDate = bond.calculationEffectiveMaturityDate(dt)

        var total = BigDecimal.ZERO
        for (coupon in coupons.entries) {
            if (!bond.isKnowsCoupon(coupon, dt)) {
                continue
            }

            val years = BigDecimal(ChronoUnit.DAYS.between(dt, coupon.key)).divide(BigDecimal(365), 16, HALF_UP)
            val yearFactor = BigDecimalMath.pow((BigDecimal.ONE + rate).setScale(12, HALF_UP), years)
            total += coupon.value.divide(yearFactor, 12, HALF_UP)
        }

        val years = BigDecimal(ChronoUnit.DAYS.between(dt, ytmMaturityDate)).divide(BigDecimal(365), 16, HALF_UP)
        val yearFactor = BigDecimalMath.pow((BigDecimal.ONE + rate).setScale(12, HALF_UP), years)
        total += bond.nominal.divide(yearFactor, 12, HALF_UP)

        return (total * BigDecimal(100)).divide(bond.nominal, 8, HALF_UP)
    }

    fun clearCache() {
        ytmCache.invalidateAll()
    }

}