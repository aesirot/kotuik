package bond

import robot.strazh.MoneyLimitStrazh
import java.math.BigDecimal

object DefaultProbabilityProvider {

    // https://raexpert.ru/about/disclosure/
    val map3years = HashMap<String, BigDecimal>()
    init {
        map3years["AAA"] = BigDecimal.ZERO;
        map3years["AA"] = BigDecimal("1.11");
        map3years["A"] = BigDecimal("3.37");
        map3years["BBB"] = BigDecimal("8.65");
        map3years["BB"] = BigDecimal("14.26");
        map3years["B"] = BigDecimal("21.22");
        map3years["CCC"] = BigDecimal("32.31");
        map3years["CC"] = BigDecimal("70");

    }


}