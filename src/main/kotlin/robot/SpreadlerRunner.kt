package robot

import robot.spreadler.Pastuh
import org.quartz.*
import org.quartz.TriggerBuilder.newTrigger
import org.quartz.TriggerKey.triggerKey
import org.quartz.impl.StdSchedulerFactory
import org.slf4j.LoggerFactory
import robot.spreadler.JobEndOfDay
import robot.spreadler.JobStartAll
import robot.spreadler.PastuhJob
import robot.strazh.MoexStrazh
import robot.strazh.MoexStrazhJob
import robot.strazh.MoneyLimitStrazh
import java.math.BigDecimal
import kotlin.NoSuchElementException
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.system.exitProcess


fun main() {
    SpreadlerRunner.run()
}

object SpreadlerRunner {
    val log = LoggerFactory.getLogger(this::class.java)
    val threads = HashMap<String, Thread>()

    fun run() {
/*
        val s = BondSpreadler(Constants.CLASS_CODE_BOND, Constants.PIK_BO_P02, 1, BigDecimal("100.5")
                , 1, 20, BigDecimal("0.2"))
        SpreadlerConfigurator.add(s)
        SpreadlerConfigurator.save()
*/

//        startAll()
        schedule()

        while (true) {
            try {
                val line = readLine()?.trim()
                if (line != null) {
                    if (line == "stop all") {
                        stopAll()
                    } else if (line == "stop buy") {
                        stopBuy()
                    } else if (line.startsWith("stop")) {
                        val id = line.substring(5).trim()
                        stop(id)
                    } else if (line == "exit") {
                        stopAll()
                        log.info("exit")
                        exitProcess(0)
                    } else if (line == "pastuh") {
                        pastuh()
                    } else if (line == "start all") {
                        startAll()
                    } else if (line.startsWith("start")) {
                        val id = line.substring(6).trim()
                        start(id)
                    } else if (line.startsWith("remove")) {
                        val id = line.substring(7).trim()
                        remove(id)
                    } else if (line.startsWith("add")) {
                        val serialized = line.substring(4)
                        add(serialized)
                    } else if (line.startsWith("set")) {
                        val id = line.substring(4).trim()
                        update(id)
                    }
                }
            } catch (e: Exception) {
                log.error(e.message, e)
            }
        }
    }

    private fun schedule() {
        val schedFact: SchedulerFactory = StdSchedulerFactory()
        val scheduler: Scheduler = schedFact.scheduler
        scheduler.start()

        val pastuh = JobBuilder.newJob(PastuhJob::class.java).build()
        val triggerPastuh: Trigger = newTrigger()
                .withIdentity(triggerKey("pastuh", "spreadler"))
                .withSchedule(CronScheduleBuilder.cronSchedule("0 57 9 ? * 1-5"))
                .build()
        scheduler.scheduleJob(pastuh, triggerPastuh)

        val startAll = JobBuilder.newJob(JobStartAll::class.java).build()
        val triggerStart: Trigger = newTrigger()
                .withIdentity(triggerKey("triggerStart", "spreadler"))
                .withSchedule(CronScheduleBuilder.cronSchedule("10 0 10 ? * 1-5"))
                .build()
        scheduler.scheduleJob(startAll, triggerStart)

        val endOfDay = JobBuilder.newJob(JobEndOfDay::class.java).build()
        val triggerEOD: Trigger = newTrigger()
                .withIdentity(triggerKey("triggerEOD", "spreadler"))
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 19 ? * 1-5"))
                .build()
        scheduler.scheduleJob(endOfDay, triggerEOD)

        val moneyLimit = JobBuilder.newJob(MoneyLimitStrazh::class.java).build()
        val triggerMoneyLimit: Trigger = newTrigger()
                .withIdentity(triggerKey("triggerMoneyLimit", "spreadler"))
                .withSchedule(DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule()
                        .startingDailyAt(TimeOfDay.hourMinuteAndSecondOfDay(10, 1, 0))
                        .endingDailyAt(TimeOfDay.hourMinuteAndSecondOfDay(19, 0, 0))
                        .withInterval(1, DateBuilder.IntervalUnit.MINUTE)
                        .onMondayThroughFriday())
                .build()
        scheduler.scheduleJob(moneyLimit, triggerMoneyLimit)

        val moexStrazhJob = JobBuilder.newJob(MoexStrazhJob::class.java).build()
        val moexTrigger: Trigger = newTrigger()
                .withIdentity(triggerKey("triggerMoexStrazh", "spreadler"))
                .withSchedule(DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule()
                        .startingDailyAt(TimeOfDay.hourMinuteAndSecondOfDay(10, 2, 10))
                        .endingDailyAt(TimeOfDay.hourMinuteAndSecondOfDay(19, 0, 0))
                        .withInterval(1, DateBuilder.IntervalUnit.MINUTE)
                        .onMondayThroughFriday())
                .build()
        scheduler.scheduleJob(moexStrazhJob, moexTrigger)
    }

    private fun update(id: String) {
        try {
            val spreadler = SpreadlerConfigurator.config.spreadlers.first { it.id == id }
            System.out.println("modify spreadler $id ${spreadler.securityCode}")

            if (threads.containsKey(id)) {
                System.err.println("spreadler $id ${spreadler.securityCode} is running. Stop it? (y/n)")
                val line = readLine()!!.trim()
                if (line.toLowerCase() == "y") {
                    stop(id)
                } else {
                    return
                }
            }

            while (true) {
                val line = readLine()!!.trim()
                if (line.startsWith("price")) {
                    spreadler.maxBuyPrice = BigDecimal(line.substring(6))
                } else if (line.startsWith("shift")) {
                    spreadler.maxShift = line.substring(6).toInt()
                } else if (line.startsWith("quantity")) {
                    val newQuantity = line.substring(9).toInt()
                    if (spreadler.buyStage) {
                        if (newQuantity >= spreadler.quantity) {
                            spreadler.restQuantity = spreadler.restQuantity + (newQuantity - spreadler.quantity)
                            spreadler.quantity = newQuantity
                        } else {
                            if (spreadler.restQuantity < (spreadler.quantity - newQuantity)) {
                                throw Exception("Can't descrease restQuantity bellow 0")
                            }
                            spreadler.restQuantity = spreadler.restQuantity + (newQuantity - spreadler.quantity)
                            spreadler.quantity = newQuantity
                        }
                    } else {
                        spreadler.quantity = newQuantity
                    }
                } else if (line == "done") {
                    break
                } else if (line == "start") {
                    start(id)
                    break
                }
            }
        } catch (e: NoSuchElementException) {
            log.error("Spreadler $id not found")
        }
    }

    private fun add(serialized: String) {
        SpreadlerConfigurator.add(serialized)
        SpreadlerConfigurator.save()
    }

    private fun remove(id: String) {
        stop(id)
        SpreadlerConfigurator.remove(id)
        SpreadlerConfigurator.save()
    }

    fun pastuh() {
        log.info("pastuh")
        if (threads.size>0) {
            println("Stop all spreadlers to run pastuh")
            return
        }
        SpreadlerConfigurator.config.spreadlers.forEach { Pastuh.adjustToday(it) }
    }

    fun startAll() {
        val moexStrazh = MoexStrazh.holder.instance
        if (!moexStrazh.isDayOpen()) {
            moexStrazh.initToday()
        }

        for (spreadler in SpreadlerConfigurator.config.spreadlers) {
            if (!spreadler.buyStage || moexStrazh.isBuyApproved()) {
                start(spreadler)
            }
        }
    }

    private fun start(id: String) {
        try {
            val first = SpreadlerConfigurator.config.spreadlers.first { it.id == id }
            start(first)
        } catch (e: NoSuchElementException) {
            log.error("Spreadler $id not found")
        }
    }

    private fun start(spreadler: SpreadlerBond) {
        if (threads.containsKey(spreadler.id)) {
            log.info("Spreader ${spreadler.securityCode} is already running")
            return
        }
        val thread = Thread(spreadler)
        thread.name = "Spreader ${spreadler.id}"
        threads.put(spreadler.id, thread)
        thread.start()
    }

    private fun stop(id: String) {
        try {
            val spreadler = SpreadlerConfigurator.config.spreadlers.first { it.id == id }
            spreadler.stop()
            if (!threads.containsKey(id)) {
                log.info("Spreadler $id ${spreadler.securityCode} is not running")
                return
            }
            val thread = threads[id]!!
            log.info("Stop ${thread.name}")
            thread.join()
            log.info("    stopped")
            threads.remove(id)
        } catch (e: NoSuchElementException) {
            log.error("Spreadler $id not found")
        }
    }

    fun stopAll() {
        for (spreadler in SpreadlerConfigurator.config.spreadlers) {
            spreadler.stop()
        }
        for (thread in threads.values) {
            log.info("Stop ${thread.name}")
            thread.join()
            log.info("    stopped")
        }
        threads.clear()
    }

    fun stopBuy() {
        log.info("stop buy spreadlers")
        val stopping = ArrayList<String>()
        for (spreadler in SpreadlerConfigurator.config.spreadlers) {
            if (spreadler.buyStage && threads.containsKey(spreadler.id)) {
                spreadler.stop()
                stopping.add(spreadler.id)
            }
        }
        for (id in stopping) {
            val thread = threads.get(id)!!
            log.info("Stop ${thread.name}")
            thread.join()
            log.info("    stopped")
            threads.remove(id)
        }
    }

}
