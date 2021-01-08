package robot.spreadler

import bond.YtmOfzDeltaService
import org.quartz.Job
import org.quartz.JobExecutionContext

class PastuhJob: Job {
    override fun execute(context: JobExecutionContext?) {
        SpreadlerRunner.pastuh()
        YtmOfzDeltaService.initAll()
    }
}