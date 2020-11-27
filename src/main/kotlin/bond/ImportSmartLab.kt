package bond

import common.HibernateUtil
import org.hibernate.Transaction
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter


fun main() {
    //ImportSmartLab.importBondDB("SU25083RMFS5")
    ImportSmartLab.importBondDB("SU26232RMFS7")

    HibernateUtil.getSessionFactory().close()
}

object ImportSmartLab {
    private val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")!!

    fun importBond(code: String, bond: Bond): Bond {
        var nominal: BigDecimal? = null
        var rate: BigDecimal? = null
        var nkd: BigDecimal? = null
        var frequency: Frequency? = null
        var issueDt: LocalDate? = null
        var maturityDt: LocalDate? = null
        var firstCouponDate: LocalDate? = LocalDate.of(2020, 4, 15)
        //var firstCouponDate: LocalDate? = null

        var nextCouponDate: LocalDate? = null
        var nextCouponAmount: BigDecimal? = null

        var earlyRedemption = false
        var earlyRedemptionDate: LocalDate? = null
        var last: BigDecimal? = null
        var yieldValue: BigDecimal? = null

        val doc = Jsoup.connect("https://smart-lab.ru/q/bonds/" + code).get()
        val myin = doc.getElementsByClass("simple-little-table bond")
        for (tr in myin.select("tr")) {
            val tds = tr.select("td")
            if (tds.size < 2) {
                continue
            }

            val th = tds[0]
            val td = tds[1]

            if ("Дата размещения" == th.text()) {
                issueDt = LocalDate.parse(td.text(), formatter)
            } else if ("Дата погашения" == th.text()) {
                maturityDt = LocalDate.parse(td.text(), formatter)
            } else if ("Номинал" == th.text()) {
                nominal = amount(td)
            } else if ("Дох. купона, годовых от ном" == th.text()) {
                rate = amount(td)
            } else if ("Дата след. выплаты" == th.text()) {
                nextCouponDate = LocalDate.parse(td.text(), formatter)
            } else if (th.text().startsWith("Купон")) {
                nextCouponAmount = amount(td)
            } else if (th.text().contains("Дата оферты")) {
                if (td.text().contains("2"))
                    earlyRedemptionDate = LocalDate.parse(td.text(), formatter)
            } else if (th.text().contains("НКД")) {
                nkd = amount(td)
            } else if (th.text().contains("Цена послед")) {
                last = amount(td)
            } else if (th.text().contains("Доходность")) {
                yieldValue = amount(td)
            } else if (th.text().contains("Выплата купона")) {
                if (td.text() == "91") {
                    frequency = Frequency.D_91
                } else if (td.text() == "182") {
                    frequency = Frequency.D_182
                } else {
                    throw Exception("Unknown frequency " + td.text())
                }
            }
        }

        val dayCount = DayCount.ACT_365


        bond.code = code
        bond.nominal = nominal!!
        bond.rate = rate!!
        bond.frequency = frequency!!
        bond.dayCount = dayCount
        bond.issueDt = issueDt!!
        bond.maturityDt = maturityDt!!
        if (firstCouponDate != null)
            bond.firstCouponDate = firstCouponDate

        if (earlyRedemptionDate != null) {
            val couponPeriodStart = bond.couponPeriodStart(earlyRedemptionDate)
            if (couponPeriodStart < earlyRedemptionDate) {
                bond.earlyRedemptionDate = couponPeriodStart
            }
        }
        bond.earlyRedemptionDate = earlyRedemptionDate


        val check = checkFrequency(bond, nextCouponDate)
        if (!check) {
            throw Exception("Wrong frequency")
        }

        val settleDt = BusinessCalendar.addDays(LocalDate.now(), 1)
        val calculatedAccrual = YieldCalculator.calcAccrual(bond, settleDt)
        if (calculatedAccrual.compareTo(nkd) != 0) {
            throw Exception("Wrong nkd $calculatedAccrual != $nkd")
        }

        val calculatedCounpon = YieldCalculator.calcAccrual(bond, nextCouponDate!!)
        if (calculatedCounpon.compareTo(nextCouponAmount) != 0) {
            throw Exception("Wrong next coupon $calculatedCounpon != $nextCouponAmount")
        }


        last!!
        val nkdToPrice = (calculatedAccrual * BigDecimal(100)).divide(bond.nominal, 12, RoundingMode.HALF_UP)
        println(YieldCalculator.effectiveYTM(bond, settleDt, last + nkdToPrice))
        println(YieldCalculator.effectiveYTMBinary(bond, settleDt, last + nkdToPrice))

        return bond
    }

    fun importBondDB(code: String) {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            var transaction: Transaction? = null
            try {
                val query = session.createQuery("from Bond where code = :code", Bond::class.java)
                query.setParameter("code", code)
                val list = query.list()

                val bond: Bond
                if (list.isEmpty()) {
                    bond = Bond()
                    bond.code = code
                } else {
                    bond = list.first()
                }

                importBond(code, bond)

                // start a transaction
                transaction = session.beginTransaction()
                // save the student objects
                session.save(bond)
                // commit transaction
                transaction.commit()
            } catch (e: java.lang.Exception) {
                if (transaction != null) {
                    transaction.rollback()
                }
                e.printStackTrace()
            }
        }

    }

    private fun checkFrequency(bond: Bond, nextCouponDate: LocalDate?): Boolean {
        var success = false
        val coupons = bond.generateCoupons(nextCouponDate!!)
        for (entry in coupons.entries) {
            if (entry.key == nextCouponDate!!) {
                return true
            }
        }

        return false
    }

    private fun amount(td: Element): BigDecimal {
        var value = td.text().replace(",", "")
        value = value.replace(" ", "")
        value = value.replace("%", "")
        value = value.replace("руб", "")
        return BigDecimal(value)
    }
}