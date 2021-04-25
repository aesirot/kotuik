package common

import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import com.enfernuz.quik.lua.rpc.config.ClientConfiguration
import com.enfernuz.quik.lua.rpc.config.JsonClientConfigurationReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File


object ConnectorRpc2 {
    private const val filePath = "rpc-client-config2.json"
    private val logger: Logger = LoggerFactory.getLogger("connector2")

    private lateinit var rpcClient: ZmqTcpQluaRpcClient

    fun get(): ZmqTcpQluaRpcClient {
        if (this::rpcClient.isInitialized) {
            return rpcClient;
        }

        synchronized(this) {
            if (this::rpcClient.isInitialized) {
                return rpcClient;
            }

            init()
        }

        return rpcClient
    }

    private fun init() {
        val configFile: File = try {
            File(filePath)
        } catch (ex: Exception) {
            throw Exception("Не удалось прочитать файл '$filePath'.", ex)
        }

        logger.info("Чтение файла конфигурации...")
        val config: ClientConfiguration = try {
            JsonClientConfigurationReader.INSTANCE.read(configFile)
        } catch (ex: Exception) {
            throw Exception("Не удалось получить объект конфигурации из файла '$filePath'.")
        }

        logger.info("Инициализация клиента...")
        val rpcClient = ZmqTcpQluaRpcClient.newInstance(config)
        logger.info("Соединение с RPC-сервисом...")
        rpcClient.open()

        val connected = rpcClient.qlua_isConnected()
        if (connected != 1) {
            throw Exception("Quik is not connected $connected")
        }

        this.rpcClient = rpcClient
    }

}