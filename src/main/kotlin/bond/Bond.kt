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
    var frequency: Frequency = Frequency.SemiAnnual

    @Column(name = "daycount")
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
        var i = issueDt
        while (i <= maturityDt) {
            i = frequency.next(i)
            if (i > dt) {
                return i
            }
        }

        return maturityDt
    }

    fun couponPeriodStart(dt: LocalDate): LocalDate {
        var i = issueDt
        var j = issueDt

        while (i <= maturityDt) {
            if (i >= dt) {
                return j
            }
            j = i
            i = frequency.next(i)
        }

        return maturityDt
    }


    fun generateCoupons(bond: Bond, ytmMaturityDate: LocalDate): HashMap<LocalDate, BigDecimal> {
        var coupons = HashMap<LocalDate, BigDecimal>()
        var dt = bond.issueDt
        while (true) {
            dt = bond.frequency.next(dt)
            if (dt > ytmMaturityDate) {
                break
            }

            coupons[dt] = YieldCalculator.calcAccrual(bond, dt)
        }

        return coupons
    }

}