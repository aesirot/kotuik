package model

import bond.BusinessCalendar
import bond.CalcYield
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
    var name: String = ""

    @Column
    var nominal: BigDecimal = BigDecimal.ZERO

    @Column(precision = 20, scale = 8)
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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "PRODUCT_ATTR", joinColumns = [JoinColumn(name = "PRODUCT_ID")])
    @MapKeyColumn(name = "attr_name")
    @Column(name = "attr_value")
    var attrs: MutableMap<String, String> = HashMap()

    @Column(name = "issuer_id")
    var issuerId: Long? = null

    @Transient
    var couponCache: MutableMap<LocalDate, BigDecimal>? = null

    fun ratePct(): BigDecimal {
        return rate.divide(BigDecimal("100"), 12, RoundingMode.HALF_UP)
    }

    fun couponPeriodEnd(dt: LocalDate): LocalDate {
        for (schedule in generateCouponSchedule()) {
            if (schedule.first < dt && schedule.second >= dt) {
                return schedule.second
            }
        }

        return maturityDt
    }

    fun calcRegistrationDate(couponPeriodEnd: LocalDate): LocalDate {
        return BusinessCalendar.minusDays(couponPeriodEnd, 1)
    }

    fun couponPeriodStart(dt: LocalDate): LocalDate {
        for (schedule in generateCouponSchedule()) {
            if (schedule.first < dt && schedule.second >= dt) {
                return schedule.first
            }
        }

        return issueDt
    }


    fun generateCoupons(): Map<LocalDate, BigDecimal> {
        if (couponCache != null) {
            return couponCache!!
        }

        val coupons = HashMap<LocalDate, BigDecimal>()
        val schedule = generateCouponSchedule()
        for (period in schedule) {
            coupons[period.second] = CalcYield.calcAccrual(this, period.first, period.second)
        }

        couponCache = coupons
        return coupons
    }

    fun isKnowsCoupon(coupon: Map.Entry<LocalDate, BigDecimal>, valueDate: LocalDate): Boolean {
        return coupon.key > valueDate && coupon.key <= calculationEffectiveMaturityDate(valueDate)
    }

    fun generateCouponSchedule(): List<Pair<LocalDate, LocalDate>> {
        val coupons = ArrayList<Pair<LocalDate, LocalDate>>()

        var dt = this.issueDt
        var fixedDate = this.firstCouponDate != null

        while (true) {
            var dt2: LocalDate
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

    fun calculationEffectiveMaturityDate(calcDate: LocalDate): LocalDate {
        if (earlyRedemptionDate == null) {
            return maturityDt
        }

        //https://bcs-express.ru/novosti-i-analitika/oferta-po-obligatsiiam-chto-nuzhno-znat-investoru-ob-etom
        //я смещаю при импорте на дату выплаты купона
        return if (earlyRedemptionDate!! >= calcDate) earlyRedemptionDate!! else maturityDt
    }

    fun getAttr(attr: SecAttr): String? {
        return attrs[attr.name]
    }

    fun getAttrM(attr: SecAttr): String {
        return attrs[attr.name]!!
    }


    fun setAttr(attr: SecAttr, value: String?) {
        if (value == null) {
            attrs.remove(attr.name)
        }

        attrs[attr.name] = value!!
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Bond

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id
    }


}