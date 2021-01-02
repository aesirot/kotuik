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

object CalcDuration {
    private val durationCache = CacheBuilder.newBuilder()
            .expireAfterWrite(9, TimeUnit.HOURS)
            .softValues()
            .build<String, BigDecimal>()


    fun durationDays(bond: Bond, dt: LocalDate, effectiveRate: BigDecimal, dirtyPrice: BigDecimal): BigDecimal {
        return durationCache.get(bond.code + ":" + dt.toString() + ":" + effectiveRate.toPlainString() + ":" + dirtyPrice.toPlainString(),
                Callable { return@Callable calcDurationDays(bond, dt,effectiveRate,  dirtyPrice) })
    }

    fun calcDurationDays(bond: Bond, dt: LocalDate, effectiveRate: BigDecimal, dirtyPrice: BigDecimal): BigDecimal {
        val maturityDate = bond.calculationEffectiveMaturityDate(dt)

        var amount = BigDecimal.ZERO

        for (coupon in bond.generateCoupons()) {
            if (coupon.key <= dt) {
                continue
            }
            if (coupon.key > maturityDate) {
                continue
            }

            val days = ChronoUnit.DAYS.between(dt, coupon.key)

            var divizor = BigDecimal.ONE + effectiveRate
            divizor = BigDecimalMath.pow(divizor, BigDecimal(days).divide(BigDecimal(365), 12, HALF_UP))

            val value = coupon.value.divide(divizor, 12, HALF_UP).multiply(BigDecimal(days))
            amount += value
        }

        val days = ChronoUnit.DAYS.between(dt, maturityDate)

        var divizor = BigDecimal.ONE + effectiveRate
        divizor = BigDecimalMath.pow(divizor, BigDecimal(days).divide(BigDecimal(365), 12, HALF_UP))

        val value = bond.nominal.divide(divizor, 12, HALF_UP).multiply(BigDecimal(days))
        amount += value

        return (amount * BigDecimal(100)).divide(dirtyPrice * bond.nominal, 12, HALF_UP)
    }

    fun clearCache() {
        durationCache.invalidateAll()
    }
}