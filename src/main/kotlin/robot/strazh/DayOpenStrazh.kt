package robot.strazh

import common.Connector
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import robot.Telega

class DayOpenStrazh : Job {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext?) {
        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            val numberOf = rpcClient.qlua_getNumberOf("orders")
            if (numberOf > 0) {
                val msg = "День открыт, удачной охоты!"
                log.info(msg)
                Telega.Holder.get().sendMessage(msg)//потом убрать, лишнее сообщение
            } else {
                val msg = "День не открыт, заявок нет"
                log.error(msg)
                Telega.Holder.get().sendMessage(msg)
            }
        }
    }
}