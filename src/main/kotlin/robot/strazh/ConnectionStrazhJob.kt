package robot.strazh

import org.quartz.Job
import org.quartz.JobExecutionContext

class ConnectionStrazhJob : Job {
    override fun execute(context: JobExecutionContext?) {
        ConnectionStrazh.check()
    }
}