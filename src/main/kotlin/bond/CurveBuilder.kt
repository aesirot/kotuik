package bond

import common.Connector
import common.StakanProvider
import model.QuoteType
import org.apache.commons.math3.fitting.PolynomialCurveFitter
import org.apache.commons.math3.fitting.WeightedObservedPoints
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class CurveBuilder(private val stakanProvider: StakanProvider) {
    private val log = LoggerFactory.getLogger(this::class.simpleName)

    companion object {
        fun stakanBuilder(): CurveBuilder {
            return CurveBuilder(StakanProvider())
        }
    }

    fun build(curve: Curve, settleDate: LocalDate) {
        val prices = HashMap<String, BigDecimal>()

        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            for (bond in curve.bonds) {
                val stakan = stakanProvider.stakan("TQOB", bond.code)

                if (stakan.offers.isEmpty() || stakan.bids.isEmpty()) {
                    log.error("Cant rebuild curve, stakan ${bond.code} is empty")
                    return
                } else {
                    val stakanPrice: BigDecimal
                    //лучший bid последний, лучший offer первый
                    if (curve.quoteType == QuoteType.BID) {
                        stakanPrice = BigDecimal(stakan.bids[stakan.bids.size - 1].price)
                    } else if (curve.quoteType == QuoteType.MID) {
                        stakanPrice = (BigDecimal(stakan.bids[stakan.bids.size - 1].price) + BigDecimal(stakan.offers[0].price))
                                .divide(BigDecimal(2), 8, RoundingMode.HALF_UP)
                    } else {
                        stakanPrice = BigDecimal(stakan.offers[0].price)
                    }

                    val nkd = stakanProvider.nkd(bond)

                    val dirtyPrice = stakanPrice + nkd.divide(bond.nominal, 6, RoundingMode.HALF_UP) * BigDecimal(100)
                    prices[bond.code] = dirtyPrice
                }
            }
        }

        calculateApproximation(curve, prices, settleDate)
    }

    private fun calculateApproximation(curve: Curve, prices: Map<String, BigDecimal>, settleDt: LocalDate) {
        if (curve.prices == prices) {
            return
        }
        val durations = HashMap<String, BigDecimal>()

        val obs = WeightedObservedPoints()
        for (bond in curve.bonds) {
            val price = prices[bond.code]
            //?: throw Exception("No price!")
                    ?: continue

            val ytm = CalcYield.effectiveYTM(bond, settleDt, price)

            //val days = ChronoUnit.DAYS.between(settleDt, bond.earlyRedemptionDate ?: bond.maturityDt)
            val durationDays = CalcDuration.durationDays(bond, settleDt, ytm, price)
            durations[bond.code] = durationDays

            obs.add(durationDays.toDouble(), ytm.toDouble())
        }

        val fitter: PolynomialCurveFitter = PolynomialCurveFitter.create(3)

        // Retrieve fitted parameters (coefficients of the polynomial function).
        val coeff: DoubleArray = fitter.fit(obs.toList())

        curve.setCalculationResult(prices, durations, coeff)
    }

}