package robot.strazh

import common.Connector
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import robot.Telega
import java.util.concurrent.*

class ConnectionStrazh : Job {
    val log = LoggerFactory.getLogger(this::class.java)
    var connected: Boolean = true

    override fun execute(context: JobExecutionContext?) {
        val task: FutureTask<Boolean> = FutureTask {
            val rpcClient = Connector.get()
            synchronized(rpcClient) {
                try {
                    val answer = rpcClient.qlua_isConnected()
                    return@FutureTask (answer == 1)
                } catch (e: Exception) {
                    return@FutureTask  false
                }
            }
        }

        Thread(task).start()

        var currentConnected: Boolean
        try {
            currentConnected = task.get(1, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            log.error("timeout")
            currentConnected = false
        }

        if (connected && !currentConnected) {
            Telega.Holder.get().sendMessage("соединение квика потеряно")
        } else if (!connected && currentConnected) {
            Telega.Holder.get().sendMessage("соединение восстановлено")
        }
        connected = currentConnected

    }
}
