package robot.spreadler

import model.Bond
import bond.CurveHolder
import common.Util
import org.slf4j.LoggerFactory
import robot.StakanLogger
import robot.strazh.*
import java.math.BigDecimal
import kotlin.NoSuchElementException
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.system.exitProcess

object SpreadlerRunner {
    private val log = LoggerFactory.getLogger(this::class.java)
    val threads = HashMap<String, Thread>()

    fun console() {

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
                        log.info("exit spreadler console")
                        //exitProcess(0)
                        break
                    } else if (line == "pastuh") {
                        pastuh()
                    } else if (line == "start all") {
                        startAll()
                    } else if (line == "sync all") {
                        syncAll()
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
                    } else if (line.startsWith("sync")) {
                        val id = line.substring(5).trim()
                        sync(id)
                    }
                }
            } catch (e: Exception) {
                log.error(e.message, e)
            }
        }
    }


    private fun update(id: String) {
        try {
            val spreadler = SpreadlerConfigurator.config.spreadlers.first { it.id == id }
            println("modify spreadler $id ${spreadler.securityCode}")

            if (threads.containsKey(id)) {
                println("spreadler $id ${spreadler.securityCode} is running. Stop it? (y/n)")
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
        if (threads.size > 0) {
            println("Stop all spreadlers to run pastuh")
            return
        }
        SpreadlerConfigurator.config.spreadlers.forEach { Pastuh.adjustToday(it) }
    }

    fun startAll() {
        val moexStrazh = MoexStrazh.instance
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
            if (threads[spreadler.id]!!.isAlive) {
                log.info("Spreader ${spreadler.securityCode} is already running")
                return
            }
        }
        val thread = Thread(spreadler)
        thread.name = "Spreader ${spreadler.id}"
        threads.put(spreadler.id, thread)
        thread.start()
    }

    private fun syncAll() {
        for (spreadler in SpreadlerConfigurator.config.spreadlers) {
            sync(spreadler.id)
        }
    }

    private fun sync(id: String) {
        try {
            stop(id)
            val spreadler = SpreadlerConfigurator.config.spreadlers.first { it.id == id }
            spreadler.syncWithLimit()
        } catch (e: Exception) {
            log.error(e.message, e)
        }
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

    fun stopDay() {
        stopAll()
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

    fun stopBuy(currency: String) {
        log.info("stop buy spreadlers $currency")
        val stopping = ArrayList<String>()
        for (spreadler in SpreadlerConfigurator.config.spreadlers) {
            if (spreadler.buyStage && threads.containsKey(spreadler.id) && currency == Util.currency(spreadler)) {
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
