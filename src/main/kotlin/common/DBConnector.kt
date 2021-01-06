package common

import java.sql.Connection
import java.sql.DriverManager

object DBConnector {

    private var connection: Connection? = null

    fun connection(): Connection {
        if (connection != null) {
            return connection!!
        }

        synchronized(this) {
            if (connection != null) {
                return connection!!
            }
            Class.forName("org.h2.Driver").getDeclaredConstructor().newInstance()
            connection = DriverManager.getConnection("jdbc:h2:C:/projects/IdeaProjects/kotuik/kotuik", "sa", "")
        }

        return connection!!
    }

    fun close() {
        if (connection != null) {
            connection!!.close()
            connection = null
        }
    }

}