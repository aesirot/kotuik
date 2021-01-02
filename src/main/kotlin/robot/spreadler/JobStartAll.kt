package robot.spreadler

import org.quartz.Job
import org.quartz.JobExecutionContext
import robot.infra.Zavod

class JobStartAll: Job {

    override fun execute(p0: JobExecutionContext?) {
        SpreadlerRunner.startAll()

        Zavod.reloadAllRobots()
        Zavod.startAll()
    }

}