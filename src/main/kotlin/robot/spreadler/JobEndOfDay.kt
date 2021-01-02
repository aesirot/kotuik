package robot.spreadler

import common.DBConnector
import common.HibernateUtil
import org.h2.tools.Backup
import org.quartz.Job
import org.quartz.JobExecutionContext
import pnl.PnL
import pnl.TradesFromQuik
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class JobEndOfDay: Job {

    override fun execute(p0: JobExecutionContext?) {
        SpreadlerRunner.stopDay()

        TradesFromQuik.load()
        PnL.calc()

        val today = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)
        PnL.sendResult(today, today.plusDays(1))

        synchronized(DBConnector) {
            DBConnector.close()
            HibernateUtil.shutdown()
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val backupName = "./logs/_bk_kotuik${today.format(formatter)}.zip"
            Backup.execute(backupName, ".", "kotuik", false)
        }
    }

}