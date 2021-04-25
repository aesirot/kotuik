package robot.spreadler

import org.quartz.Job
import org.quartz.JobExecutionContext
import pnl.PnL
import pnl.TradesFromQuik
import robot.infra.Zavod
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class JobEndOfDay: Job {

    override fun execute(p0: JobExecutionContext?) {
        SpreadlerRunner.stopDay()
        Zavod.stopAll()

        TradesFromQuik.load()
        PnL.calc()

        val today = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)
        PnL.sendResult(today, today.plusDays(1))

/*
        synchronized(DBConnector) {
            DBConnector.close()
            HibernateUtil.shutdown()
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val backupName = "./logs/_bk_kotuik${today.format(formatter)}.zip"
            Backup.execute(backupName, ".", "kotuik", false)
        }
*/
    }

}