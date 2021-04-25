package db.dao

import common.HibernateUtil
import model.Trade
import org.hibernate.Transaction

object TradeDAO {

    fun save(trade: Trade) {
        HibernateUtil.getSessionFactory().currentSession.use { session ->
            var transaction: Transaction? = null
            transaction = session.beginTransaction()
            session.clear()

            session.save(trade)

            transaction.commit()
        }
    }

    fun select(where: String): List<Trade> {
        return select(where, null)
    }

    fun select(where: String, orderBy: String?): List<Trade> {
        HibernateUtil.getSessionFactory().currentSession.use { session ->
            var select = "from Trade where $where "
            if (orderBy != null) {
                select += " order by $orderBy"
            }
            val query = session.createQuery(select, Trade::class.java)

            return query.list()
        }
    }

}