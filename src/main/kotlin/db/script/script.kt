package db.script

import org.h2.tools.Recover
import org.h2.tools.Restore
import org.h2.tools.Script
import java.io.BufferedReader
import java.io.InputStreamReader
import java.sql.Connection
import java.sql.DriverManager

fun main() {
    try {
        //Script.process("jdbc:h2:C:/projects/IdeaProjects/kotuik/kotuik", "sa", "", "saveme", "compression", "zip")
        //Restore.execute()
      //  Script("drop_trade.sql").execute()
        //Script("trade.sql").execute()
        Script("flush_pnl.sql").execute()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

class Script(val script: String) {
    fun execute() {
        Class.forName("org.h2.Driver").newInstance()
        val conn: Connection = DriverManager.getConnection("jdbc:h2:C:/projects/IdeaProjects/kotuik/kotuik", "sa", "")!!
        try {
            val sql = readSQL(script)

            val st = conn.createStatement()
            st.execute(sql)

            conn.commit()
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
