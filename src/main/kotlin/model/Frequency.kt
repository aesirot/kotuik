package model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Frequency {
    SemiAnnual {
        override fun next(d: LocalDate): LocalDate {
            return d.plus(6, ChronoUnit.MONTHS);
        }

        override fun prev(d: LocalDate): LocalDate {
            return d.plus(-6, ChronoUnit.MONTHS);
        }
    },
    Quarter {
        override fun next(d: LocalDate): LocalDate {
            return d.plus(3, ChronoUnit.MONTHS);
        }

        override fun prev(d: LocalDate): LocalDate {
            return d.plus(-3, ChronoUnit.MONTHS);
        }
    },
    D_91 {
        override fun next(d: LocalDate): LocalDate {
            return d.plus(91, ChronoUnit.DAYS);
        }

        override fun prev(d: LocalDate): LocalDate {
            return d.plus(-91, ChronoUnit.DAYS);
        }
    },
    D_182 {
        override fun next(d: LocalDate): LocalDate {
            return d.plus(182, ChronoUnit.DAYS);
        }

        override fun prev(d: LocalDate): LocalDate {
            return d.plus(-182, ChronoUnit.DAYS);
        }
    },
    ;

    abstract fun next(d: LocalDate): LocalDate;

    abstract fun prev(d: LocalDate): LocalDate;
}