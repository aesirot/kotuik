package robot.strazh

import org.quartz.Job
import org.quartz.JobExecutionContext

class MoneyLimitStrazhJob: Job {

    override fun execute(context: JobExecutionContext?) {
        MoneyLimitStrazh.check()
    }

}