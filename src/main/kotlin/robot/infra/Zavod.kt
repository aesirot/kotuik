package robot.infra

import common.DBService
import org.slf4j.LoggerFactory
import robot.Robot

object Zavod {
    private val log = LoggerFactory.getLogger(this::class.java)!!

    private val robots = HashMap<String, Robot>()

    fun reloadAllRobots() {
        log.info("reload robots")
        if (robots.size > 0) {
            for (robot in robots.values) {
                if (robot.isRunning()) {
                    throw Exception("Робот ${robot.name()} уже запущен")
                }
            }
        }
        robots.clear()

        for (robot in DBService.loadAllRobots()) {
            robots[robot.name()] = robot
        }
    }

    fun startAll() {
        log.info("start all")
        if (robots.isEmpty()) {
            reloadAllRobots()
        }

        for (entry in robots.entries) {
            if (entry.value.isRunning()) {
                continue
            }
            if (entry.key == "Orel") {
                continue // запускать отдельно (чтобы не запускался в 10:00)
            }

            entry.value.init()
            entry.value.setFinishCallback { finish(it) }
            entry.value.start()
        }
    }

    private fun finish(robot: Robot) {
        remove(robot.name())
    }

    fun stopAll() {
        for (entry in robots.entries) {
            entry.value.stopSignal()
        }
        for (entry in robots.entries) {
            entry.value.stop()
        }
    }

    fun stop(name: String) {
        if (!robots.contains(name)) {
            log.error("No robot $name")
            return
        }
        robots[name]!!.stopSignal()
        robots[name]!!.stop()
    }

    fun remove(name: String) {
        log.info("remove $name")
        if (!robots.contains(name)) {
            log.error("No robot $name")
            return
        }
        robots.remove(name)
    }

    fun addRobot(robot: Robot) {
        if (robots.containsKey(robot.name())) {
            log.error("Robot $robot ALREADY EXISTS")
            return
        }

        DBService.saveNewRobot(robot)
        robots[robot.name()] = robot
    }

    fun startRobot(id: String) {
        if (!robots.containsKey(id)) {
            throw Exception("No robot $id")
        }
        if (robots[id]!!.isRunning()) {
            log.info("Robot $id already running")
        }
        robots[id]!!.init()
        robots[id]!!.start()
    }

}