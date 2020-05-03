package backtest

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.ArrayList
import java.time.format.DateTimeFormatter





class CSVHistoryLoader {
    //Delimiter used in CSV file
    private val NEW_LINE_SEPARATOR = "\n"

    //CSV file header
    private val FILE_HEADER = arrayOf<Any>("id", "firstName", "lastName", "gender", "age")

    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd HHmmss")!!

    fun load(classCode: String, securityCode: String): ArrayList<Bar> {
        val csvFormat = CSVFormat.newFormat(';').withFirstRecordAsHeader()
        Files.newBufferedReader(Paths.get("data/csv/$securityCode.csv")).use { reader ->
            CSVParser(reader, csvFormat).use { csvParser ->
                val bars = ArrayList<Bar>()
                for (csvRecord in csvParser) {
                    // Accessing Values by Column Index
                    val code = csvRecord[0]
                    val interval = csvRecord[1]
                    val date = csvRecord[2]
                    val time = csvRecord[3]
                    val open = csvRecord[4]
                    val high = csvRecord[5]
                    val low = csvRecord[6]
                    val close = csvRecord[7]
                    val volume = csvRecord[8].toLong()

                    val datetime = LocalDateTime.parse("$date $time", formatter)
                    bars.add(Bar(datetime, BigDecimal(open), BigDecimal(high), BigDecimal(low), BigDecimal(close), volume.toLong()))
                }
                return bars
            }
        }

    }
}