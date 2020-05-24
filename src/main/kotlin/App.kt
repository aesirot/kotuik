import com.enfernuz.quik.lua.rpc.api.messages.GetParamEx
import com.enfernuz.quik.lua.rpc.api.messages.Message
import com.enfernuz.quik.lua.rpc.api.messages.datasource.Bars
import com.enfernuz.quik.lua.rpc.api.messages.datasource.CreateDataSource
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import com.enfernuz.quik.lua.rpc.application.RpcExampleApplication
import com.enfernuz.quik.lua.rpc.config.ClientConfiguration
import com.enfernuz.quik.lua.rpc.config.JsonClientConfigurationReader
import common.Util
import java.io.File
import java.math.BigDecimal

fun main() {
    val filePath = "rpc-client-config.json"
    if (filePath == null) {
        println("Не задан путь до файла конфигурации.")
        return
    }

    val configFile: File
    configFile = try {
        File(filePath)
    } catch (ex: Exception) {
        println("Не удалось прочитать файл '$filePath'.")
        ex.printStackTrace()
        return
    }

    println("Чтение файла конфигурации...")
    val config: ClientConfiguration
    config = try {
        JsonClientConfigurationReader.INSTANCE.read(configFile)
    } catch (ex: Exception) {
        println(String.format("Не удалось получить объект конфигурации из файла '%s'.", filePath))
        ex.printStackTrace()
        return
    }

    println("Инициализация клиента...")
    var rpcClient: ZmqTcpQluaRpcClient? = null
    try {
        rpcClient = ZmqTcpQluaRpcClient.newInstance(config)
        println("Соединение с RPC-сервисом...")
        rpcClient.open()

        val connected = rpcClient.qlua_isConnected()
        if (connected != 1) {
            println("Quik is not connected $connected")
            return
        }

        val dataSource = Util.dataSource("INDX", "IMOEX", CreateDataSource.Interval.INTERVAL_D1, rpcClient)
        val bars = rpcClient.datasource_Bars(Bars.Args(dataSource.datasourceUUID, 0))
        bars.size

/*
        println("Выполнение удалённой процедуры 'message' на терминале QUIK...")
        val result: Int? = rpcClient.qlua_message("Hello, world! Kotlin", Message.IconType.WARNING)
        if (result == null) {
            println("Удалённая процедура 'message' выполнилась с ошибкой.")
        } else {
            println("Результат выполнения удалённой процедуры 'message': {$result}.")
        }

*/


        val thread = Thread(StakanLogger(rpcClient))
        thread.start()
        thread.join()

        println("Выход из программы...")
    } catch (ex: Exception) {
        println("Не удалось выполнить удалённый вызов процедуры.")
        ex.printStackTrace()
    } finally {
        rpcClient?.close()
    }

}