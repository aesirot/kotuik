package robot.strazh

import org.quartz.Job
import org.quartz.JobExecutionContext

class MaxMoneyStrazhJob: Job {

    override fun execute(context: JobExecutionContext?) {
        SpreadlerMaxMoneyStrazh.check()
    }

}