package db.script

import bond.*
import common.DBService
import common.HibernateUtil
import model.Bond
import org.hibernate.Transaction
import robot.StakanLogger
import robot.orel.Orel
import robot.orel.OrelOFZ
import kotlin.system.exitProcess

fun main() {
    val curve = CurveHolder.createCurveSystema()

    HibernateUtil.getSessionFactory().getCurrentSession().use { session ->
        var transaction: Transaction? = null
        try {
            //transaction = session.beginTransaction()
            //session.clear()

            DBService.saveNewRobot(Orel())
            DBService.saveNewRobot(OrelOFZ())
            DBService.saveNewRobot(StakanLogger())


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