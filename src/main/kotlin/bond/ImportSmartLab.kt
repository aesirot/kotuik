package bond

import common.HibernateUtil
import model.Bond
import model.DayCount
import model.Frequency
import org.hibernate.Transaction
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter


fun main() {
    //ImportSmartLab.importBondDB("SU25083RMFS5")
    ImportSmartLab.importBondDB("RU000A1023K1", "сист15")

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
        //var firstCouponDate: LocalDate? = LocalDate.of(2020, 10, 7)
        //var firstCouponDate: LocalDate? = LocalDate.parse("09.10.2019", DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        var firstCouponDate: LocalDate? = null

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

            var value = td.text()

            if ("Дата размещения" == th.text()) {
                issueDt = LocalDate.parse(value, formatter)
            } else if ("Дата погашения" == th.text()) {
                maturityDt = LocalDate.parse(value, formatter)
            } else if ("Номинал" == th.text()) {
                nominal = amount(td)
            } else if ("Дох. купона, годовых от ном" == th.text()) {
                rate = amount(td)
            } else if ("Дата след. выплаты" == th.text()) {
                nextCouponDate = LocalDate.parse(value, formatter)
            } else if (th.text().startsWith("Купон")) {
                nextCouponAmount = amount(td)
            } else if (th.text().contains("Дата оферты")) {
                if (value.contains("2"))
                    earlyRedemptionDate = LocalDate.parse(value, formatter)
            } else if (th.text().contains("НКД")) {
                nkd = amount(td)
            } else if (th.text().contains("Цена послед")) {
                last = amount(td)
            } else if (th.text().contains("Доходность")) {
                yieldValue = amount(td)
            } else if (th.text().contains("Выплата купона")) {
                //value="182"
                if (value == "91") {
                    frequency = Frequency.D_91
                } else if (value == "182") {
                    frequency = Frequency.D_182
                } else {
                    throw Exception("Unknown frequency " + value)
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

        //earlyRedemptionDate = LocalDate.parse("09.11.2023", DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        if (earlyRedemptionDate != null) {
            val couponPeriodStart = bond.couponPeriodStart(earlyRedemptionDate)
            if (couponPeriodStart < earlyRedemptionDate) {
                bond.earlyRedemptionDate = couponPeriodStart
            }
        }


        val check = checkFrequency(bond, nextCouponDate)
        if (!check) {
            throw Exception("Wrong frequency")
        }

        val settleDt = BusinessCalendar.addDays(LocalDate.now(), 0)
        val calculatedAccrual = CalcYield.calcAccrual(bond, settleDt)
        if (calculatedAccrual.compareTo(nkd) != 0) {
            throw Exception("Wrong nkd $calculatedAccrual != $nkd")
        }

        val calculatedCounpon = CalcYield.calcAccrual(bond, nextCouponDate!!)
        if (calculatedCounpon.compareTo(nextCouponAmount) != 0) {
            throw Exception("Wrong next coupon $calculatedCounpon != $nextCouponAmount")
        }


        last!!
        val nkdToPrice = (calculatedAccrual * BigDecimal(100)).divide(bond.nominal, 12, RoundingMode.HALF_UP)
        println(CalcYield.effectiveYTM_StepAlgo(bond, settleDt, last + nkdToPrice))
        var calcYTM = CalcYield.effectiveYTMBinary(bond, settleDt, last + nkdToPrice)
        println(calcYTM)
        calcYTM = calcYTM.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
        if (yieldValue!!.compareTo(calcYTM) != 0) {
            throw Exception("YTM break exp=$yieldValue calculated=$calcYTM")
        }
        println("\nУСПЕХ\n")

        return bond
    }

    fun importBondDB(code: String, name: String) {
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
                bond.name = name
                bond.issuerId = 2

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
        val coupons = bond.generateCoupons()
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