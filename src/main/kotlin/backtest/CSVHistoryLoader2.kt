package backtest

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class CSVHistoryLoader2 {
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd HHmmss")!!

    fun load(classCode: String, securityCode: String): TreeMap<LocalDateTime, Bar> {
        println("load $securityCode")
        val csvFormat = CSVFormat.newFormat(';').withFirstRecordAsHeader()
        try {
            Files.newBufferedReader(Paths.get("data/csv/$securityCode.csv")).use { reader ->
                CSVParser(reader, csvFormat).use { csvParser ->
                    val bars = TreeMap<LocalDateTime, Bar>()
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
                        bars[datetime] = Bar(datetime, BigDecimal(open), BigDecimal(high), BigDecimal(low), BigDecimal(close), volume)

                        if (datetime.toLocalDate() > LocalDate.of(2020,5, 1)) { //TODO
                            break
                        }
                    }
                    return bars
                }
            }
        } catch (e: NoSuchFileException) {
            logger.error("No File for $securityCode")

            return TreeMap<LocalDateTime, Bar>()
        }
    }
}