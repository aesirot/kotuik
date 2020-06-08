package db.script

import org.h2.tools.Recover
import org.h2.tools.Restore
import java.io.BufferedReader
import java.io.InputStreamReader
import java.sql.Connection
import java.sql.DriverManager

fun main() {
    try {
        Report("security_pnl.sql").execute()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

class Report(val script: String) {
    fun execute() {
        Class.forName("org.h2.Driver").newInstance()
        val conn: Connection = DriverManager.getConnection("jdbc:h2:C:/projects/IdeaProjects/kotuik/kotuik", "sa", "")!!
        try {
            val sql = readSQL(script)

            val st = conn.createStatement()
            val resultSet = st.executeQuery(sql)
            while (resultSet.next()) {
                println("${resultSet.getObject(1)};${resultSet.getObject(2)}")
            }
        } finally {
            conn.close()
        }
    }

    private fun readSQL(script: String): String {
        val stream = this.javaClass.getResourceAsStream(script)
        stream.use {
            val reader = BufferedReader(InputStreamReader(stream))
            reader.use {
                return reader.readText()
            }
        }
    }
}
