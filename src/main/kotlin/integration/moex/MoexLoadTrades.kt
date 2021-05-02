package integration.moex

import bond.CurveHolder
import com.google.gson.GsonBuilder
import common.HibernateUtil
import common.Telega
import integration.moex.model.TradesResponse
import model.integration.MoexTrade
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.hibernate.Transaction
import org.slf4j.LoggerFactory
import robot.spreadler.SpreadlerConfigurator
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun main() {
    MoexLoadTrades.loadAll(getAllBonds())
}

private fun getAllBonds(): HashSet<String> {
    val secCodes = HashSet<String>()

    SpreadlerConfigurator.config.spreadlers.forEach { secCodes.add(it.securityCode) }
    CurveHolder.createCurveSystema().bonds.forEach { secCodes.add(it.code) }
    CurveHolder.curveOFZ().bonds.forEach { secCodes.add(it.code) }

    return secCodes
}

object MoexLoadTrades {
    val logger = LoggerFactory.getLogger(this::class.java)!!

    fun loadAll(secCodes: Collection<String>) {
        secCodes.forEach { load(it) }
    }

    fun load(secCode: String) {
        try {
            val response = callService(secCode)

            val gson = GsonBuilder().create()
            val tradesResponse = gson.fromJson(response, TradesResponse::class.java)

            val trades = parseTradeInfo(tradesResponse)

            saveToDB(trades)
        } catch (e: Exception) {
            val msg = "Error loading trades from Moex: ${e.message}"
            logger.error(msg, e)
            Telega.Holder.get().sendMessage(msg)
        }
    }

    private fun callService(secCode: String): String {
        HttpClients.createDefault().use { httpclient ->
            val httpget = HttpGet("https://iss.moex.com/iss/engines/stock/markets/bonds/securities/$secCode/trades.json")
            val response = httpclient.execute(httpget)

            if (response == null) {
                throw Exception("No response")
            }

            return EntityUtils.toString(response.entity)!!
        }
    }

    private fun parseTradeInfo(tradesResponse: TradesResponse): ArrayList<MoexTrade> {
        val yields = tradeEffectiveYields(tradesResponse)

        if (tradesResponse.trades == null) {
            throw Exception("tradesResponse.trades is null")
        }

        val colTradeno = tradesResponse.trades!!.columns!!.indexOf("TRADENO")
        val colSystime = tradesResponse.trades!!.columns!!.indexOf("SYSTIME")
        val colPrice = tradesResponse.trades!!.columns!!.indexOf("PRICE")
        val colQuantity = tradesResponse.trades!!.columns!!.indexOf("QUANTITY")
        val colSecId = tradesResponse.trades!!.columns!!.indexOf("SECID")

        if (colTradeno == -1 || colSystime == -1 || colPrice == -1 || colQuantity == -1 || colSecId == -1) {
            throw Exception("No column!")
        }

        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        val trades = ArrayList<MoexTrade>()
        for (tradeFields in tradesResponse.trades!!.data!!) {
            val tradeId = (tradeFields[colTradeno] as Double).toLong()
            val systime = LocalDateTime.parse(tradeFields[colSystime] as String, dateTimeFormatter)
            val price = tradeFields[colPrice] as Double
            val quantity = tradeFields[colQuantity] as Double
            val secId = tradeFields[colSecId] as String
            val yieldValue = yields[tradeId]!!

            val tradeInfo = MoexTrade()
            tradeInfo.securityCode = secId
            tradeInfo.moexTradeId = tradeId
            tradeInfo.trade_datetime = systime
            tradeInfo.price = price
            tradeInfo.quantity = quantity.toInt()
            tradeInfo.yieldValue = yieldValue.first
            tradeInfo.duration = yieldValue.second

            trades.add(tradeInfo)
        }

        return trades
    }

    private fun saveToDB(trades: java.util.ArrayList<MoexTrade>) {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val transaction: Transaction = session.beginTransaction()
            for (trade in trades) {
                session.merge(trade)
            }
            transaction.commit()
        }
    }

    private fun tradeEffectiveYields(tradesResponse: TradesResponse): HashMap<Long, Pair<Double, Int>> {
        if (tradesResponse.trades_yields == null) {
            throw Exception("tradesResponse.trades_yields is null")
        }

        val colTradeno = tradesResponse.trades_yields!!.columns!!.indexOf("TRADENO")
        val colYield = tradesResponse.trades_yields!!.columns!!.indexOf("EFFECTIVEYIELD")
        val colDuration = tradesResponse.trades_yields!!.columns!!.indexOf("DURATION")

        if (colTradeno == -1 || colYield == -1 || colDuration == -1) {
            throw Exception("No column!")
        }

        val result = HashMap<Long, Pair<Double, Int>>()
        for (tradeFields in tradesResponse.trades_yields!!.data!!) {
            val tradeId = (tradeFields[colTradeno] as Double).toLong()
            val effYield = tradeFields[colYield] as Double
            val duration = tradeFields[colDuration] as Double
            result[tradeId] = Pair(effYield, duration.toInt())
        }

        return result
    }

}