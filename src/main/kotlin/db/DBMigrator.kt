package db

import model.Trade
import org.hibernate.SessionFactory
import org.hibernate.Transaction
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder

fun main() {
    DBMigrator.migrate()
}

object DBMigrator {

    fun migrate() {
        try {
            val list: List<Any> = load()

            upload(list)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun load(): List<Any> {
        val list: List<Any>

        val sourceSessionFactory = getSessionFactory("hibernate_h2.cfg.xml")
        sourceSessionFactory.openSession().use { session ->
            val query = session.createQuery(
                "from Trade",
                Trade::class.java
            )
            //                query.setParameter("code", bond.code)
            list = query.list()
        }
        sourceSessionFactory.close()
        return list
    }

    private fun upload(list: List<Any>) {
        val targetSessionFactory = getSessionFactory("hibernate.cfg.xml")
        targetSessionFactory.openSession().use { session ->
            var transaction: Transaction? = null
            try {
                transaction = session.beginTransaction()
                session.clear()

                list.forEach { session.merge(it) }

                transaction.commit()
            } catch (e: java.lang.Exception) {
                transaction?.rollback()
                e.printStackTrace()
            }
        }
        targetSessionFactory.close()
    }


    fun getSessionFactory(config: String): SessionFactory {
        try {
            val registry = StandardServiceRegistryBuilder().configure(config).build()

            val sources = MetadataSources(registry)

            val metadata = sources.metadataBuilder.build()

            return metadata.sessionFactoryBuilder.build()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

}