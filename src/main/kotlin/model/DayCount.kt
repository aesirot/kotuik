package model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class DayCount {
    ACT_360 {
        override fun yearDif(dt1: LocalDate, dt2: LocalDate): BigDecimal {
            return yearDifACT(dt1, dt2, 360);
        }
    },
    ACT_365 {
        override fun yearDif(dt1: LocalDate, dt2: LocalDate): BigDecimal {
            return yearDifACT(dt1, dt2, 365);
        }
    },
    ;

    abstract fun yearDif(dt1: LocalDate, dt2: LocalDate): BigDecimal;

    protected fun yearDifACT(dt1: LocalDate, dt2: LocalDate, yearLen: Long): BigDecimal {
        val days = ChronoUnit.DAYS.between(dt1, dt2)
        return BigDecimal.valueOf(days).divide(BigDecimal.valueOf(yearLen), 12, RoundingMode.HALF_UP);
    }
}