import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import com.enfernuz.quik.lua.rpc.config.ClientConfiguration
import com.enfernuz.quik.lua.rpc.config.JsonClientConfigurationReader
import com.enfernuz.quik.lua.rpc.events.api.LoggingEventHandler
import common.Connector
import common.Orders
import java.io.File
import java.math.BigDecimal

fun main() {
    val rpcClient = Connector.get()
    Connector.registerEventHandler(LoggingEventHandler.INSTANCE)

    Thread.sleep(30_000)

    Connector.close()
}