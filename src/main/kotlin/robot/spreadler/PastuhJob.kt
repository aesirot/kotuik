package robot.spreadler

import org.quartz.Job
import org.quartz.JobExecutionContext

class PastuhJob: Job {
    override fun execute(context: JobExecutionContext?) {
        SpreadlerRunner.pastuh()
    }
}