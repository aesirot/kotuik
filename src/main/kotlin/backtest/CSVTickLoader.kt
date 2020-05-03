package backtest

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.ArrayList
import java.time.format.DateTimeFormatter





class CSVTickLoader {
    //Delimiter used in CSV file
    private val NEW_LINE_SEPARATOR = "\n"

    //CSV file header
    private val FILE_HEADER = arrayOf<Any>("id", "firstName", "lastName", "gender", "age")

    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd HHmmss")!!
    //private val formatter = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss")!!

    fun load(classCode: String, securityCode: String): ArrayList<Tick> {
        val csvFormat = CSVFormat.newFormat(';').withFirstRecordAsHeader()
        Files.newBufferedReader(Paths.get("data/csv/${securityCode}t.csv")).use { reader ->
            CSVParser(reader, csvFormat).use { csvParser ->
                val ticks = ArrayList<Tick>()
                for (csvRecord in csvParser) {
                    // Accessing Values by Column Index
                    val code = csvRecord[0]
                    val interval = csvRecord[1]
                    val date = csvRecord[2]
                    val time = csvRecord[3]
                    val last = csvRecord[4]
                    val volume = csvRecord[5]

                    val datetime = LocalDateTime.parse("$date $time", formatter)
                    ticks.add(Tick(datetime, BigDecimal(last), volume.toInt()))
                }
                return ticks
            }
        }

    }
}