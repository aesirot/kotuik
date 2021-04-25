package robot.strazh

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import robot.infra.Zavod

class UniversalJob: Job {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext) {
        val exec = context.trigger.jobDataMap.get("exec")
        if (exec == null) {
            log.error("Invalid trigger")
        }

        if (exec == "startOrel") {
            Zavod.startRobot("Orel")
        }
    }
}