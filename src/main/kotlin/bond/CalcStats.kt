package bond

import java.math.BigDecimal
import java.math.RoundingMode

object CalcStats {

    fun analyze(nums: List<BigDecimal>): Pair<BigDecimal, BigDecimal> {
        val total = nums.fold(BigDecimal.ZERO) { acc, bd -> acc + bd }

        val matOzh = total.divide(BigDecimal(nums.size), 12, RoundingMode.HALF_UP)

        val totalDiff = nums.fold(BigDecimal.ZERO) { acc, bd ->
            val diff = bd - matOzh
            diff * diff
        }

        val dispercia = totalDiff.divide(BigDecimal(nums.size), 12, RoundingMode.HALF_UP)

        return Pair(matOzh, dispercia)
    }
}