package bond

import com.google.common.collect.Lists
import com.google.common.collect.Sets
import java.time.DayOfWeek
import java.time.LocalDate

object BusinessCalendar {


    fun isBusiness(dt: LocalDate): Boolean {
        val holidays = Sets.newHashSet(LocalDate.of(2021, 1, 1))

        if (holidays.contains(dt)) {
            return false
        }

        if (DayOfWeek.SATURDAY == dt.dayOfWeek && DayOfWeek.SUNDAY == dt.dayOfWeek) {
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
            next = dt.plusDays(1)
            if (isBusiness(next)) {
                i++
            }
        }

        return next
    }
}