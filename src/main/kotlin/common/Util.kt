package common

import backtest.Bar
import com.enfernuz.quik.lua.rpc.api.messages.datasource.*
import com.enfernuz.quik.lua.rpc.api.structures.DataSourceTime
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDateTime

object Util {

    val log = LoggerFactory.getLogger(this::class.simpleName)

    fun dataSource(classCode: String, securityCode: String, interval: CreateDataSource.Interval, rpcClient: ZmqTcpQluaRpcClient): CreateDataSource.Result {
        val args = CreateDataSource.Args(classCode, securityCode, interval, "")
        val dataSource: CreateDataSource.Result
        synchronized(rpcClient) {
            dataSource = rpcClient.datasource_CreateDataSource(args)
        }

        var size = 0
        for (i in 0..99) {
            synchronized(rpcClient) {
                size = rpcClient.datasource_Size(Size.Args(dataSource.datasourceUUID))
                if (size > 0) {
                    return dataSource
                }
            }
            Thread.sleep(100)
        }

        log.error("datasource $securityCode is empty $size")
        return dataSource
    }

    fun format(time: DataSourceTime): String =
            "${time.day}.${time.month}.${time.year} ${time.hour}:${time.min}"

    fun toLocalDateTime(time: DataSourceTime): LocalDateTime =
            LocalDateTime.of(time.year, time.month, time.day, time.hour, time.min, time.sec, time.ms * 1000000)

    fun toBars(dataSource: CreateDataSource.Result): ArrayList<Bar> {
        val result = ArrayList<Bar>()
        val rpcClient = Connector.get()
        //log.info("start")
        synchronized(rpcClient) {
            val size = rpcClient.datasource_Size(Size.Args(dataSource.datasourceUUID))

            for (i in 1..size) {
                val dataSourceTime = rpcClient.datasource_T(T.Args(dataSource.datasourceUUID, i))
                val open = BigDecimal(rpcClient.datasource_O(O.Args(dataSource.datasourceUUID, i)))
                val high = BigDecimal(rpcClient.datasource_H(H.Args(dataSource.datasourceUUID, i)))
                val low = BigDecimal(rpcClient.datasource_L(L.Args(dataSource.datasourceUUID, i)))
                val close = BigDecimal(rpcClient.datasource_C(C.Args(dataSource.datasourceUUID, i)))
                var volume = rpcClient.datasource_V(V.Args(dataSource.datasourceUUID, i)).toLong()
                result.add(Bar(toLocalDateTime(dataSourceTime), open, high, low, close, volume))
            }
        }
        //log.info("end")

        return result
    }

    fun stakanCount(countStr: String) =
            countStr.toBigDecimal().intValueExact()

    fun datetime(datetimeStr: String): LocalDateTime {
        return LocalDateTime.now()!!
    }

}