package robot.strazh

import org.quartz.Job
import org.quartz.JobExecutionContext

class MoexStrazhJob: Job {
    override fun execute(context: JobExecutionContext?) {
        MoexStrazh.instance.check()
    }
}