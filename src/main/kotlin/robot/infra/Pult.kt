package robot.infra

import bond.CurveHolder
import bond.CurveVisualization
import common.Connector
import common.DBService
import org.slf4j.LoggerFactory
import robot.spreadler.SpreadlerRunner
import kotlin.system.exitProcess

class Pult {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun console() {
        while (true) {
            try {
                val line = readLine()?.trim()
                if (line != null) {
                    if (line == "exit") {
                        if (!confirm()) continue

                        log.info("полная остановка")
                        SpreadlerRunner.stopAll()
                        Zavod.stopAll()
                        Connector.close()

                        log.info("EXIT")
                        exitProcess(0)
                    } else if (line == "spreadler") {
                        SpreadlerRunner.console()
                    } else if (line == "cancel orders") {
                        val rpcClient = Connector.get()
                        synchronized(rpcClient) {
                            //TODO rpcClient.getP
                        }
                    } else if (line == "stop all") {
                        Zavod.stopAll()
                    } else if (line.startsWith("stop")) {
                        val id = line.split(" ")[1]
                        Zavod.stop(id)
                    } else if (line == "start all") {
                        Zavod.startAll()
                    } else if (line.startsWith("start")) {
                        val id = line.split(" ")[1]
                        Zavod.startRobot(id)
                    } else if (line.startsWith("delete")) {
                        if (!confirm()) continue

                        val id = line.split(" ")[1]
                        Zavod.stop(id)
                        DBService.deleteRobot(id)
                        Zavod.remove(id)
                    } else if (line == "spec") {
                        spec()
                    } else if (line == "show curve") {
                        showCurve()
                    }
                }
            } catch (e: Exception) {
                log.error(e.message, e)
            }

        }
    }

    private fun showCurve() {
        val curveOFZ = CurveHolder.curveOFZ()
        CurveVisualization.visualize(curveOFZ)
    }

    private fun confirm(): Boolean {
        println("уверен (y)?")

        val answer = readLine()?.trim()
        return (answer != null && answer.toLowerCase() == "y")
    }

    private fun spec() {
        //do what you want

        try {
            Zavod.reloadAllRobots()
            Zavod.startAll()
        } catch (e: Exception) {
            log.error(e.message, e)
        }

    }
}