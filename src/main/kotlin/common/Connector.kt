package common

import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaEventProcessor
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import com.enfernuz.quik.lua.rpc.config.ClientConfiguration
import com.enfernuz.quik.lua.rpc.config.JsonClientConfigurationReader
import com.enfernuz.quik.lua.rpc.events.api.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

import java.util.concurrent.atomic.AtomicBoolean

import com.enfernuz.quik.lua.rpc.events.api.QluaEventProcessor.QluaEventProcessingException


object Connector {
    private const val filePath = "rpc-client-config.json"
    private val logger: Logger = LoggerFactory.getLogger("connector")

    private lateinit var rpcClient: ZmqTcpQluaRpcClient
    private lateinit var eventConnection: ZmqTcpQluaEventProcessor

    private var eventThread: Thread? = null

    private var stop = false

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

    fun registerEventHandler(handler: QluaEventHandler) {
        if (!this::eventConnection.isInitialized) {
            synchronized(this) {
                if (!this::eventConnection.isInitialized) {
                    initEventProcessingConnection()
                }
            }
        }

        logger.info("Регистрация обработчика событий...")
        eventConnection.register(handler) //CopyOnWriteArrayList
    }

    fun unregisterEventHandler(handler: QluaEventHandler) {
        if (!this::eventConnection.isInitialized) {
            return
        }

        logger.info("Отписка обработчика событий...")
        eventConnection.unregister(handler) //CopyOnWriteArrayList
    }

    private fun initEventProcessingConnection() {
        logger.info("Инициализация подписки событий из квика")

        val configFile: File = try {
            File("subscription-client-config.json")
        } catch (ex: Exception) {
            throw Exception("Не удалось прочитать файл '$filePath'.", ex)
        }

        logger.info("Чтение файла конфигурации...")
        val config: ClientConfiguration = try {
            JsonClientConfigurationReader.INSTANCE.read(configFile)
        } catch (ex: Exception) {
            throw Exception("Не удалось получить объект конфигурации из файла '$filePath'.")
        }


        try {
            val eventProcessor = ZmqTcpQluaEventProcessor.newInstance(config, PollingMode.BLOCKING)
            logger.info("Подписка на стакан...")
            eventProcessor.subscribe(QluaEvent.EventType.ON_QUOTE)

            logger.info("Соединение с RPC-сервисом...")
            eventProcessor.open()

            eventThread = Thread(EventProcessor(), "QuikEventProcessor")
            eventThread!!.start()

            this.eventConnection = eventProcessor
        } catch (ex: QluaEventProcessingException) {
            logger.error("Ошибка при обработке события.", ex)
        } catch (ex: java.lang.Exception) {
            logger.error("Не удалось начать обработку событий.", ex)
        }

    }

    @Synchronized
    fun close() {
        if (this::eventConnection.isInitialized) {
            stop = true
            if (eventThread != null) {
                eventThread!!.join(100)
                if (eventThread!!.isAlive) {
                    eventThread!!.interrupt()
                }
            }
            eventConnection.close()
        }

        if (this::rpcClient.isInitialized) {
            rpcClient.close()
        }

    }

    private class EventProcessor : Runnable {
        override fun run() {
            while (!stop) {
                try {
                    eventConnection.process()
                } catch (e: Exception) {
                    logger.error("EventProcessingError: " + e.message, e)
                }
            }
        }

    }
}