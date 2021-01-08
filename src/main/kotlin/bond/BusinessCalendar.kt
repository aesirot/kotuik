package bond

import com.google.common.collect.Sets
import java.time.DayOfWeek
import java.time.LocalDate

object BusinessCalendar {

    val holidays = Sets.newHashSet<LocalDate>()
    init {
        holidays.add(LocalDate.of(2019, 1, 1))
        holidays.add(LocalDate.of(2019, 1, 2))
        holidays.add(LocalDate.of(2019, 1, 7))
        holidays.add(LocalDate.of(2019, 3, 8))
        holidays.add(LocalDate.of(2019, 5, 9))
        holidays.add(LocalDate.of(2019, 6, 12))
        holidays.add(LocalDate.of(2019, 11, 4))

        holidays.add(LocalDate.of(2020, 1, 1))
        holidays.add(LocalDate.of(2020, 1, 2))
        holidays.add(LocalDate.of(2020, 1, 7))
        holidays.add(LocalDate.of(2020, 2, 24))
        holidays.add(LocalDate.of(2020, 3, 9))
        holidays.add(LocalDate.of(2020, 5, 11))
        holidays.add(LocalDate.of(2020, 6, 12))
        holidays.add(LocalDate.of(2020, 6, 24))
        holidays.add(LocalDate.of(2020, 7, 1))
        holidays.add(LocalDate.of(2020, 11, 4))
        holidays.add(LocalDate.of(2020, 12, 31))

        holidays.add(LocalDate.of(2021, 1, 1))
        holidays.add(LocalDate.of(2021, 1, 7))
        holidays.add(LocalDate.of(2021, 1, 8))
        holidays.add(LocalDate.of(2021, 2, 23))
        holidays.add(LocalDate.of(2021, 3, 8))
        holidays.add(LocalDate.of(2021, 5, 3))
        holidays.add(LocalDate.of(2021, 5, 10))
        holidays.add(LocalDate.of(2021, 6, 14))
        holidays.add(LocalDate.of(2021, 11, 4))
        holidays.add(LocalDate.of(2021, 12, 31))

    }

    fun isBusiness(dt: LocalDate): Boolean {
        if (holidays.contains(dt)) {
            return false
        }

        if (DayOfWeek.SATURDAY == dt.dayOfWeek || DayOfWeek.SUNDAY == dt.dayOfWeek) {
            return false
        }

        return true
    }

    fun addDays(dt: LocalDate, days: Int): LocalDate {
        if (days == 0) {
            if (isBusiness(dt)) {
                return dt
            }
            return addDays(dt, 1)
        }

        var next = dt
        var i = 0
        while (i < days) {
            next = next.plusDays(1)
            if (isBusiness(next)) {
                i++
            }
        }

        return next
    }

    fun minusDays(dt: LocalDate, days: Int): LocalDate {
        if (days == 0) {
            if (isBusiness(dt)) {
                return dt
            }
            return minusDays(dt, 1)
        }

        var next = dt
        var i = 0
        while (i < days) {
            next = next.plusDays(-1)
            if (isBusiness(next)) {
                i++
            }
        }

        return next
    }

    fun daysBetween(dt1: LocalDate, dt2: LocalDate): Int {
        if (dt1 == dt2) {
            return 0
        }
        if (dt1 > dt2) {
            throw Exception("$dt1 after $dt2")
        }

        var dt1New = addDays(dt1, 1)
        var days = 1
        while (dt1New < dt2) {
            dt1New = addDays(dt1New, 1)
            days++
        }

        return days
    }
}