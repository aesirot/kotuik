package db.script

import org.h2.jdbc.JdbcResultSet
import org.h2.tools.Recover
import org.h2.tools.Restore
import java.io.BufferedReader
import java.io.InputStreamReader
import java.sql.Connection
import java.sql.DriverManager

fun main() {
    try {
        Report("trade_hist.sql").execute()
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
            val resultSet = st.executeQuery(sql) as JdbcResultSet

            var h = ""
            for (i in 1..(resultSet.metaData.columnCount)) {
                h += "${resultSet.metaData.getColumnName(i)};"
            }
            println(h)

            while (resultSet.next()) {
                var r = ""
                for (i in 1..(resultSet as JdbcResultSet).metaData.columnCount) {
                    r += "${resultSet.getObject(i)};"
                }
                println(r)
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
