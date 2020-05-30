package pnl

import common.Connector
import db.dao.TradeDAO
import java.text.DateFormat
import java.time.format.DateTimeFormatter

fun main() {
    ShowTradeHist.show("RU000A0JW5C7")
}

object ShowTradeHist {
    val path = "C:\\projects\\IdeaProjects\\kotuik\\src\\main\\resources\\"

    fun show(securityCode: String) {
        val trades = TradeDAO.select("sec_code='$securityCode'")
        if (trades.isEmpty()) {
            return
        }
        val classCode = trades.last().classCode
        val chartTag = "g1"

        val rpcClient = Connector.get()
        rpcClient.qlua_DelAllLabels(chartTag)

        val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd");
        val timeFormat = DateTimeFormatter.ofPattern("HHmmss");
        for (trade in trades) {
            val buy = trade.direction == "B"

            val params = HashMap<String, String>()
            params["TEXT"] = ""
            params["IMAGE_PATH"] = path + (if (buy) "up.bmp" else "down.bmp")
            params["ALIGNMENT"] = "LEFT"
            params["YVALUE"] = trade.price.toPlainString()
            params["DATE"] = dateFormat.format(trade.trade_datetime)
            params["TIME"] = "180000"
//            params["TIME"] = trade.trade_datetime.hour.toString() + trade.trade_datetime.minute.toString() + trade.trade_datetime.second.toString()
            params["R"] = "0"
            params["G"] = "0"
            params["B"] = "0"
            params["TRANSPARENCY"] = "0"
            params["TRANSPARENT_BACKGROUND"] = "1"
            params["FONT_FACE_NAME"] = "Arial"
            params["FONT_HEIGHT"] = "12"
            params["HINT"] = trade.quantity.toString()+ " " + trade.price.toPlainString()

            rpcClient.qlua_AddLabel(chartTag, params)
        }


    }
}