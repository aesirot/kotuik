package bond

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.persistence.*

@Entity
@Table(name = "product_bond")
class Bond {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "s_product")
    @Column(name = "product_id")
    var id = 0

    @Column
    var code: String = ""

    @Column
    var nominal: BigDecimal = BigDecimal.ZERO

    @Column
    var rate: BigDecimal = BigDecimal.ZERO

    @Column
    @Enumerated(EnumType.STRING)
    var frequency: Frequency = Frequency.SemiAnnual

    @Column(name = "daycount")
    @Enumerated(EnumType.STRING)
    var dayCount: DayCount = DayCount.ACT_365

    @Column
    var issueDt: LocalDate = LocalDate.now()

    @Column
    var maturityDt: LocalDate = LocalDate.now()

    @Column
    var firstCouponDate: LocalDate? = null

    @Column
    var earlyRedemptionDate: LocalDate? = null


    fun ratePct(): BigDecimal {
        return rate.divide(BigDecimal("100"), 12, RoundingMode.HALF_UP)
    }

    fun couponPeriodEnd(dt: LocalDate): LocalDate {
        for (schedule in generateCouponSchedule()) {
            if (schedule.first<dt && schedule.second>=dt) {
                return schedule.second
            }
        }

        return maturityDt
    }

    fun couponPeriodStart(dt: LocalDate): LocalDate {
        for (schedule in generateCouponSchedule()) {
            if (schedule.first<dt && schedule.second>=dt) {
                return schedule.first
            }
        }

        return issueDt
    }


    fun generateCoupons(ytmMaturityDate: LocalDate): HashMap<LocalDate, BigDecimal> {
        val coupons = HashMap<LocalDate, BigDecimal>()
        val schedule = generateCouponSchedule()
        for (period in schedule) {
            coupons[period.second] = YieldCalculator.calcAccrual(this, period.first, period.second)
        }

        return coupons
    }

    fun generateCouponSchedule(): List<Pair<LocalDate, LocalDate>> {
        val coupons = ArrayList<Pair<LocalDate, LocalDate>>()

        var dt = this.issueDt
        var fixedDate = this.firstCouponDate != null

        while (true) {
            var dt2 : LocalDate
            if (fixedDate) {
                dt2 = firstCouponDate!!
                fixedDate = false
            } else {
                dt2 = frequency.next(dt)
            }

            if (dt2 > maturityDt) {
                break
            }

            coupons.add(Pair(dt, dt2))
            dt = dt2
        }

        return coupons
    }

}