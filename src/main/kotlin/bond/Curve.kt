package bond

import model.Bond
import model.QuoteType
import java.math.BigDecimal

class Curve(val bonds: List<Bond>, val quoteType: QuoteType) {
    var prices: Map<String, BigDecimal> = HashMap()
    var durations: Map<String, BigDecimal> = HashMap()

    var curveCoeff: DoubleArray? = null

    @Synchronized
    fun approx(days: BigDecimal): Double {
        if (curveCoeff == null) {
            throw Exception("Curve isn't ready")
        }

        return curveCoeff!![0] + curveCoeff!![1] * days.toDouble() + curveCoeff!![2] * days.pow(2).toDouble() + curveCoeff!![3] * days.pow(3).toDouble()
    }

    @Synchronized
    fun setCalculationResult(prices: Map<String, BigDecimal>, durations: HashMap<String, BigDecimal>, coeff: DoubleArray) {
        this.prices = prices
        this.durations = durations
        this.curveCoeff = coeff
    }

    @Synchronized
    fun clearCalculation() {
        this.prices = HashMap()
        this.durations = HashMap()
        this.curveCoeff = null
    }

    @Synchronized
    fun isCalculated(): Boolean {
        return curveCoeff != null
    }

}