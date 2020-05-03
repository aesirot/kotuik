package robot.spreadler

import org.quartz.Job
import org.quartz.JobExecutionContext
import robot.SpreadlerRunner

class PastuhJob: Job {
    override fun execute(context: JobExecutionContext?) {
        SpreadlerRunner.pastuh()
    }
}