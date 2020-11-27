package bond

import org.nevec.rjm.BigDecimalMath
import java.math.BigDecimal
import java.math.RoundingMode.HALF_UP
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object YieldCalculator {


    fun calcAccrual(bond: Bond, dt: LocalDate): BigDecimal {
        val startDt: LocalDate = bond.couponPeriodStart(dt)
        return (bond.dayCount.yearDif(startDt, dt) * bond.nominal * bond.ratePct()).setScale(2, HALF_UP)
    }


    private val TOLERANCE = BigDecimal("0.001")

    //https://investprofit.info/bond-yields/
    fun effectiveYTM(bond: Bond, dt: LocalDate, dirtyPrice: BigDecimal): BigDecimal {
        var rate = bond.ratePct()
        var step = BigDecimal.ONE

        var cnt = 1

        val ytmMaturityDate = bond.earlyRedemptionDate ?: bond.maturityDt
        val coupons = bond.generateCoupons(bond, ytmMaturityDate)

        var calcPrice = calcDirtyPriceFromYield(bond, dt, rate, coupons)

        if (dirtyPrice > calcPrice) {
            step = BigDecimal("-0.01") //если текущ цена > вычисл, значит эф дох должна быть меньше
        } else {
            step = TOLERANCE
        }

        while((calcPrice - dirtyPrice).abs() > TOLERANCE) {
            cnt++
            rate += step
            val calcPrice2 = calcDirtyPriceFromYield(bond, dt, rate, coupons)

            if ((calcPrice-dirtyPrice).signum() != (calcPrice2-dirtyPrice).signum()) {
                //пора обратно
                step = step.divide(BigDecimal(-10), 10, HALF_UP)
            }
            calcPrice = calcPrice2

            if (cnt > 300) {
                throw Exception("Too deep!!!")
            }
        }

        println(cnt)
        return rate
    }

    //https://investprofit.info/bond-yields/
    fun effectiveYTMBinary(bond: Bond, dt: LocalDate, dirtyPrice: BigDecimal): BigDecimal {
        var rate = bond.ratePct()
        var step = BigDecimal.ONE

        var cnt = 1

        val ytmMaturityDate = bond.earlyRedemptionDate ?: bond.maturityDt
        val coupons = bond.generateCoupons(bond, ytmMaturityDate)

        var calcPrice = calcDirtyPriceFromYield(bond, dt, rate, coupons)

        if (dirtyPrice > calcPrice) {
            step = BigDecimal("-0.01") //если текущ цена > вычисл, значит эф дох должна быть меньше
        } else {
            step = BigDecimal("0.01")
        }


        while((calcPrice - dirtyPrice).abs() > TOLERANCE) {
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

        println(cnt)
        return rate
    }

    private fun binarySearch(bond: Bond, coupons: HashMap<LocalDate, BigDecimal>, dt: LocalDate, price: BigDecimal, left: Pair<BigDecimal, BigDecimal>, right: Pair<BigDecimal, BigDecimal>): BigDecimal {
        //берем ставку исходя из пропорции удаленности
        val testRate = left.first - (left.first - right.first) * (price - left.second).divide(right.second - left.second, 12, HALF_UP)
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

    private fun calcDirtyPriceFromYield(bond: Bond, dt: LocalDate, rate: BigDecimal, coupons: HashMap<LocalDate, BigDecimal>): BigDecimal {
        val ytmMaturityDate = bond.earlyRedemptionDate ?: bond.maturityDt

        var total = BigDecimal.ZERO
        for (entry in coupons.entries) {
            if (entry.key < dt) {
                continue
            }
            val years = BigDecimal(ChronoUnit.DAYS.between(dt, entry.key)).divide(BigDecimal(365), 16, HALF_UP)
            val yearFactor = BigDecimalMath.pow((BigDecimal.ONE + rate).setScale(12, HALF_UP), years)
            total += entry.value.divide(yearFactor, 12, HALF_UP)
        }

        val years = BigDecimal(ChronoUnit.DAYS.between(dt, ytmMaturityDate)).divide(BigDecimal(365), 16, HALF_UP)
        val yearFactor = BigDecimalMath.pow((BigDecimal.ONE + rate).setScale(12, HALF_UP), years)
        total += bond.nominal.divide(yearFactor, 12, HALF_UP)

        return (total * BigDecimal(100)).divide(bond.nominal, 8, HALF_UP)
    }

}