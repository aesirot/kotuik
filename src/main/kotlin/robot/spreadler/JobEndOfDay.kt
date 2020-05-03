package robot.spreadler

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.TriggerKey
import pnl.PnL
import pnl.TradesFromQuik
import robot.SpreadlerRunner
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class JobEndOfDay: Job {

    override fun execute(p0: JobExecutionContext?) {
        SpreadlerRunner.stopAll()

        TradesFromQuik.load()
        PnL.calc()

        val today = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)
        PnL.sendResult(today, today.plusDays(1))

        p0!!.getScheduler().unscheduleJob(TriggerKey.triggerKey("triggerEOD", "spreadler"));
    }

}