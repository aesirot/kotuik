package bond

import common.HibernateUtil
import model.Bond
import model.QuoteType
import org.slf4j.LoggerFactory

object CurveHolder {
    private val log = LoggerFactory.getLogger(this::class.simpleName)

    private var curveOFZ: Curve? = null

    @Synchronized
    fun curveOFZ(): Curve {
        if (curveOFZ != null) {
            return curveOFZ!!
        }

        HibernateUtil.getSessionFactory().openSession().use { session ->
            val query = session.createQuery("from Bond where issuer_id = 1", Bond::class.java)
            val list = query.list()

            curveOFZ = Curve(list, QuoteType.BID)
            return curveOFZ()
        }
    }

    fun createCurveSystema(): Curve {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val query = session.createQuery("from Bond where issuer_id = 2", Bond::class.java)
            val list = query.list()

            return Curve(list, QuoteType.BID)
        }
    }

}