package common

import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.boot.registry.internal.StandardServiceRegistryImpl

object HibernateUtil {
    private var registry: StandardServiceRegistry? = null
    private var sessionFactory: SessionFactory? = null

    @Synchronized
    fun getSessionFactory(): SessionFactory {
        if (sessionFactory == null || !(registry as StandardServiceRegistryImpl).isActive) {
            try {
                // Create registry
                registry = StandardServiceRegistryBuilder().configure().build()

                // Create MetadataSources
                val sources = MetadataSources(registry)

                // Create Metadata
                val metadata = sources.metadataBuilder.build()

                // Create SessionFactory
                sessionFactory = metadata.sessionFactoryBuilder.build()
            } catch (e: Exception) {
                e.printStackTrace()
                if (registry != null) {
                    StandardServiceRegistryBuilder.destroy(registry)
                }
            }
        }
        return sessionFactory!!
    }

    fun shutdown() {
        if (registry != null) {
            StandardServiceRegistryBuilder.destroy(registry)
        }
    }

}