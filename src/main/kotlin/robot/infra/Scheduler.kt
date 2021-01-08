package robot.infra

import org.quartz.*
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import robot.spreadler.JobEndOfDay
import robot.spreadler.JobStartAll
import robot.spreadler.PastuhJob
import robot.strazh.*
import java.util.*

class Scheduler {

    fun schedule() {
        val props = Properties()
        props["org.quartz.scheduler.instanceName"] = "scheduler"
        val schedFact: SchedulerFactory = StdSchedulerFactory(props)
        val scheduler: Scheduler = schedFact.scheduler
        scheduler.start()

        val pastuh = JobBuilder.newJob(PastuhJob::class.java).build()
        val triggerPastuh: Trigger = TriggerBuilder.newTrigger()
            .withIdentity(TriggerKey.triggerKey("pastuh", "spreadler"))
            .withSchedule(CronScheduleBuilder.cronSchedule("0 57 9 ? * 2-6"))
            .build()
        scheduler.scheduleJob(pastuh, triggerPastuh)

        val startAll = JobBuilder.newJob(JobStartAll::class.java).build()
        val triggerStart: Trigger = TriggerBuilder.newTrigger()
            .withIdentity(TriggerKey.triggerKey("triggerStart", "spreadler"))
            .withSchedule(CronScheduleBuilder.cronSchedule("10 0 10 ? * 2-6"))
            .build()
        scheduler.scheduleJob(startAll, triggerStart)

        val dayOpenJob = JobBuilder.newJob(DayOpenStrazh::class.java).build()
        val triggerDayOpen: Trigger = TriggerBuilder.newTrigger()
            .withIdentity(TriggerKey.triggerKey("dayOpen", "g3"))
            .withSchedule(CronScheduleBuilder.cronSchedule("10 1 10 ? * 2-6"))
            .build()
        scheduler.scheduleJob(dayOpenJob, triggerDayOpen)

        val endOfDay = JobBuilder.newJob(JobEndOfDay::class.java).build()
        val triggerEOD: Trigger = TriggerBuilder.newTrigger()
            .withIdentity(TriggerKey.triggerKey("triggerEOD", "spreadler"))
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 19 ? * 2-6"))
            .build()
        scheduler.scheduleJob(endOfDay, triggerEOD)

        val moneyLimit = JobBuilder.newJob(MoneyLimitStrazhJob::class.java).build()
        val triggerMoneyLimit: Trigger = TriggerBuilder.newTrigger()
            .withIdentity(TriggerKey.triggerKey("triggerMoneyLimit", "spreadler"))
            .withSchedule(
                DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule()
                    .startingDailyAt(TimeOfDay.hourMinuteAndSecondOfDay(10, 1, 0))
                    .endingDailyAt(TimeOfDay.hourMinuteAndSecondOfDay(19, 0, 0))
                    .withInterval(1, DateBuilder.IntervalUnit.MINUTE)
                    .onMondayThroughFriday()
            )
            .build()
        scheduler.scheduleJob(moneyLimit, triggerMoneyLimit)

        val connectionJob = JobBuilder.newJob(ConnectionStrazhJob::class.java).build()
        val connectionTrigger: Trigger = TriggerBuilder.newTrigger()
            .withIdentity(TriggerKey.triggerKey("connectionStrazh", "s2"))
            .withSchedule(
                DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule()
                    .startingDailyAt(TimeOfDay.hourMinuteAndSecondOfDay(9, 55, 0))
                    .endingDailyAt(TimeOfDay.hourMinuteAndSecondOfDay(19, 0, 0))
                    .withInterval(1, DateBuilder.IntervalUnit.MINUTE)
                    .onMondayThroughFriday()
            )
            .build()
        scheduler.scheduleJob(connectionJob, connectionTrigger)

        val moexStrazhJob = JobBuilder.newJob(MoexStrazhJob::class.java).build()
        val moexTrigger: Trigger = TriggerBuilder.newTrigger()
            .withIdentity(TriggerKey.triggerKey("triggerMoexStrazh", "s3"))
            .withSchedule(
                DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule()
                    .startingDailyAt(TimeOfDay.hourMinuteAndSecondOfDay(10, 2, 10))
                    .endingDailyAt(TimeOfDay.hourMinuteAndSecondOfDay(19, 0, 0))
                    .withInterval(1, DateBuilder.IntervalUnit.MINUTE)
                    .onMondayThroughFriday()
            )
            .build()
        scheduler.scheduleJob(moexStrazhJob, moexTrigger)

        val maxMoneyStrazhJob = JobBuilder.newJob(MaxMoneyStrazhJob::class.java).build()
        val maxMoneyTrigger: Trigger = TriggerBuilder.newTrigger()
            .withIdentity(TriggerKey.triggerKey("triggerMaxMoneyStrazh", "s3"))
            .withSchedule(
                DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule()
                    .startingDailyAt(TimeOfDay.hourMinuteAndSecondOfDay(10, 2, 10))
                    .endingDailyAt(TimeOfDay.hourMinuteAndSecondOfDay(19, 0, 0))
                    .withInterval(10, DateBuilder.IntervalUnit.MINUTE)
                    .onMondayThroughFriday()
            )
            .build()
        scheduler.scheduleJob(maxMoneyStrazhJob, maxMoneyTrigger)
    }

}