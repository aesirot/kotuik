package robot

import com.enfernuz.quik.lua.rpc.api.messages.datasource.CreateDataSource
import com.enfernuz.quik.lua.rpc.api.messages.datasource.Size
import com.enfernuz.quik.lua.rpc.api.messages.datasource.T
import com.enfernuz.quik.lua.rpc.api.messages.datasource.V
import com.enfernuz.quik.lua.rpc.api.structures.DataSourceTime
import common.Connector
import common.Util
import org.slf4j.LoggerFactory
import java.math.BigDecimal

const val securityCode = "TGKD"
const val classCode = "TQBR"

const val depth = 60*8

fun main() {
    val log = LoggerFactory.getLogger("robot.MaxMinVolume")

    val rpcClient = Connector.get();

    val dataSource = Util.dataSource(classCode, securityCode, CreateDataSource.Interval.INTERVAL_M1, rpcClient)

    val size = rpcClient.datasource_Size(Size.Args(dataSource.datasourceUUID))
    val minIdx = Math.max(size - depth, 1)

    var maxVolume = BigDecimal.ZERO

    lateinit var maxTime: DataSourceTime
    for (i in minIdx..size) {
        val vlString = rpcClient.datasource_V(V.Args(dataSource.datasourceUUID, i))
        val volume = BigDecimal(vlString)
        if (volume >= maxVolume) {
            maxTime = rpcClient.datasource_T(T.Args(dataSource.datasourceUUID, i))
            maxVolume = volume
        }
    }

    log.info("maxVolume=$maxVolume time ${Util.format(maxTime)} ")

    rpcClient.datasource_Close(dataSource.datasourceUUID)
    rpcClient.close();
}

