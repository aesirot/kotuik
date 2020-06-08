package robot.spreadler

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.TriggerKey
import robot.SpreadlerRunner

class JobStartAll: Job {

    override fun execute(p0: JobExecutionContext?) {
        SpreadlerRunner.startAll()
    }

}