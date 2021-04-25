package robot.strazh

import common.Connector
import common.Telega
import org.slf4j.LoggerFactory
import java.util.concurrent.*

object ConnectionStrazh {
    val log = LoggerFactory.getLogger(this::class.java)
    var connected: Boolean = true

    fun check() {
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
            currentConnected = task.get(60, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            log.error("timeout")
            currentConnected = false
        }

        if (connected && !currentConnected) {
            val message = "соединение квика потеряно"
            log.error(message)
            Telega.Holder.get().sendMessage(message)
        } else if (!connected && currentConnected) {
            val message = "соединение восстановлено"
            log.info(message)
            Telega.Holder.get().sendMessage(message)
        }
        connected = currentConnected

    }
}
