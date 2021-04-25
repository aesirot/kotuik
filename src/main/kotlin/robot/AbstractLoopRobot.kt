package robot

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class AbstractLoopRobot : Robot {

    @Transient
    private var stop = false

    @Transient
    private lateinit var log: Logger

    @Transient
    protected var waitSeconds = 60L

    @Transient
    private val lock = ReentrantLock()

    @Transient
    private val lockCondition = lock.newCondition()

    @Transient
    private var thread: Thread? = null

    override fun init() {
        log = LoggerFactory.getLogger(this.javaClass)
    }

    override fun start() {
        if (isRunning()) {
            log.info(name() + " already running")
            return
        }

        thread = Thread(this, "Robot " + name())
        thread!!.start()
    }

    override fun isRunning(): Boolean {
        if (thread != null) {
            return !stop
        }
        return false
    }

    override fun run() {
        stop = false
        log.info("start " + name())

        try {
            while (!stop) {
                execute()

                if (!stop) {
                    lock.withLock {
                        lockCondition.await(waitSeconds, TimeUnit.SECONDS)
                    }
                }
            }
        } catch (e: Exception) {
            log.error(e.message, e)
            stop = true
        }
    }

    abstract fun execute()

    @Synchronized
    override fun stopSignal() {
        if (!isRunning()) {
            return
        }

        stop = true
    }

    @Synchronized
    override fun stop() {
        if (thread == null || !thread!!.isAlive) {
            return
        }

        thread!!.interrupt()
        
        stop = true
        lock.withLock {
            lockCondition.signalAll()
        }

        thread!!.join(120000)
    }

    override fun setFinishCallback(function: (Robot) -> Unit) {
    }

    override fun state(): Any? {
        return null
    }


}