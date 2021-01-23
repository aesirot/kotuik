package db.script

import bond.BusinessCalendar
import bond.CurveHolder
import common.DBService
import common.HibernateUtil
import model.robot.PolzuchiiSellState
import org.hibernate.Transaction
import robot.PolzuchiiSellRobot
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

fun main() {
    val curve = CurveHolder.createCurveSystema()

    HibernateUtil.getSessionFactory().getCurrentSession().use { session ->
        var transaction: Transaction? = null
        try {
            //transaction = session.beginTransaction()
            //session.clear()

            val reduceDate = BusinessCalendar.minusDays(LocalDate.now(), 3)
            val sqlReduceDate = DateTimeFormatter.ISO_LOCAL_DATE.format(reduceDate)

            val children = DBService.loadRobots("parentId='Orel' and updated< '$sqlReduceDate'")

            for (child in children) {
                if (child is PolzuchiiSellRobot) {
                    println("Reduce sell price ${child.name}")

                    val state = child.state() as PolzuchiiSellState
                    state.minPrice -= BigDecimal("0.05")

                    DBService.updateRobot(child)
                }
            }


            //val bond : Bond = session.get(Bond::class.java, 1702)
//            bond.firstCouponDate= LocalDate.of(2011,10, 26)//26.10.2011
//            session.update(bond)
/*            for (bond in curve.bonds) {
                bond.setAttr(SecAttr.MoexClass, "TQCB")
                session.update(bond)
            }*/
            //transaction.commit()
        } catch (e: java.lang.Exception) {
            if (transaction != null) {
                transaction.rollback()
            }
            e.printStackTrace()
        }
    }

    exitProcess(0)
}


class HibScript {

}