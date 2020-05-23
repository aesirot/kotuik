package robot.spreadler

import backtest.Bar
import com.enfernuz.quik.lua.rpc.api.messages.GetParamEx
import com.enfernuz.quik.lua.rpc.api.messages.GetSecurityInfo
import com.enfernuz.quik.lua.rpc.api.messages.datasource.CreateDataSource.Interval.INTERVAL_D1
import common.Connector
import common.Util
import org.slf4j.LoggerFactory
import robot.SpreadlerConfigurator
import java.math.BigDecimal
import java.text.DecimalFormat
import kotlin.collections.ArrayList

fun main() {
    BondSelector.run()
}


object BondSelector {
    val log = LoggerFactory.getLogger(this::class.java)
    val decimalFormat = DecimalFormat("0.00")

    private class SecurityInfo(val classCode: String,
                               val secCode: String,
                               val name: String,
                               val shortName: String,
                               val matDate: String)

    fun run() {

        val bonds = loadAllBonds()
        println("корИмя;имя;класс;код;мед объема;мед размах;погашение;есть;есть колво;инструкция")
        for (bond in bonds) {
            val bars = loadBars(bond)
            if (bars.size < 21) {
                continue
            }

            val medianVolume = calculateMedianDayVolume(bond, bars, 21)
            if (medianVolume < 100) {
                continue
            }

            val medianSpread = calculateMedianDaySpread(bond, bars, 21)

            val instr = ("add { \"classCode\": \"${bond.classCode}\", \"securityCode\": \"${bond.secCode}\", " +
                    "\"id\": \"\", \"maxBuyPrice\": 80, " +
                    "\"quantity\": 30, \"maxShift\": 20, \"minSellSpread\": 0.3, " +
                    "\"buyStage\": true, \"buyPrice\": 80, " +
                    "\"restQuantity\": 30, \"updated\": \"01.01.2020 00:00:00\" }").replace("\"", "\"\"")

            val any = SpreadlerConfigurator.config.spreadlers.find { it.securityCode == bond.secCode }
            val exists = if (any != null) "Д" else ""
            val existQuantity = any?.quantity?.toString() ?: ""

            if (medianSpread > BigDecimal("0.3")) {
                println("\"${bond.shortName}\";\"${bond.name}\";\"${bond.classCode}\";\"${bond.secCode}\";\"$medianVolume\"" +
                        ";\"${decimalFormat.format(medianSpread)}\";\"${bond.matDate}\";\"$exists\";\"$existQuantity\";\"$instr\"")
            }

        }

    }

    private fun calculateMedianDayVolume(bond: SecurityInfo, bars: ArrayList<Bar>, depth: Int): Long {
        if (bars.size < depth) {
            return 0L
        }

        val volumes = ArrayList<Long>()
        for (i in bars.size - depth..bars.size - 1) {
            volumes.add(bars[i].volume)
        }

        volumes.sort()

        return volumes[10]
    }

    private fun loadBars(bond: SecurityInfo): ArrayList<Bar> {
        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            val dataSource = Util.dataSource(bond.classCode, bond.secCode, INTERVAL_D1, rpcClient)
            try {
                return Util.toBars(dataSource)
            } finally {
                rpcClient.datasource_Close(dataSource.datasourceUUID)
            }
        }
    }

    private fun calculateMedianDaySpread(bond: SecurityInfo, bars: ArrayList<Bar>, depth: Int): BigDecimal {
        if (bars.size < depth) {
            return BigDecimal.ZERO
        }

        val spreads = ArrayList<BigDecimal>()
        for (i in bars.size - depth..bars.size - 1) {
            spreads.add(bars[i].high - bars[i].low)
        }

        spreads.sort()

        return spreads[10]
    }

    private fun loadAllBonds(): ArrayList<SecurityInfo> {
        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            val securities = ArrayList<SecurityInfo>()
            val size = rpcClient.qlua_getNumberOf("securities")
            for (i in 1..size - 1) {
                val item = rpcClient.qlua_getItem("securities", i)!!
                val classCode = item["class_code"]!!
                if (classCode == "EQOB" || classCode == "TQCB") {
                    val secCode = item["code"]!!
                    val name = item["name"]!!
                    val shortName = item["short_name"]!!
                    val matDate = item["mat_date"]!!

                    //фильтруем по ненулевому текущему обороту
                    val ex = rpcClient.qlua_getParamEx(GetParamEx.Args(classCode, secCode, "VALTODAY"))
                    if (BigDecimal(ex.paramValue).compareTo(BigDecimal.ZERO) == 0) {
                        continue
                    }

                    securities.add(SecurityInfo(classCode, secCode, name, shortName, matDate))
                }
            }
            return securities
        }
    }
}