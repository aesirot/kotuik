package pnl

import common.Connector
import common.Util
import db.Trade
import db.dao.TradeDAO
import java.math.BigDecimal

object TradesFromQuik {

    fun load() {

        val trades = ArrayList<Trade>()
        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            val tradeNumber = rpcClient.qlua_getNumberOf("trades")

            for (i in 0..tradeNumber - 1) {
                val item = rpcClient.qlua_getItem("trades", i)!!

                val classCode = item["class_code"]!!
                val secCode = item["sec_code"]!!
                val flags = item["flags"]!!.toInt()
                val direction = if (flags.and(4) > 0) "S" else "B"
                val quantity = item["qty"]!!.toInt()
                val amount = BigDecimal(item["value"]!!)
                val price = BigDecimal(item["price"]!!)
                val currency = item["trade_currency"]!!
                val datetime = Util.datetime(item["datetime"]!!)
                val transId = item["trans_id"]
                val quikTradeId = item["trade_num"]!!
                val orderNum = item["order_num"]!!

                trades.add(Trade(classCode, secCode, direction, quantity, price,
                        currency, amount, datetime, transId, 0, orderNum, quikTradeId))
            }
        }

        for (trade in trades) {
            val select = TradeDAO.select("quik_trade_id='${trade.quikTradeNum}'")
            if (select.isNotEmpty()) {
                continue
            }

            TradeDAO.insert(trade)
        }
    }
}