package pnl

import common.Connector
import db.Trade
import db.dao.TradeDAO
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun main() {
    ShowTradeHist.show("RU000A0JW5C7", BarInterval.HOUR)
}

enum class BarInterval {
    DAY, HOURS_4, HOURS_2, HOUR, MIN_30, MIN_20, MIN_15, MIN_10, MIN_6, MIN_5, MIN_1
}

object ShowTradeHist {
    val path = "C:\\projects\\IdeaProjects\\kotuik\\src\\main\\resources\\"
    val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd");
    val timeFormat = DateTimeFormatter.ofPattern("HHmmss");

    fun show(securityCode: String, interval: BarInterval) {
        val trades = TradeDAO.select("sec_code='$securityCode'")
        if (trades.isEmpty()) {
            return
        }
        val chartTag = "g1"

        val rpcClient = Connector.get()
        rpcClient.qlua_DelAllLabels(chartTag)

        for (trade in trades) {
            val dateTime = round(trade.trade_datetime, interval)
            val params = params(trade, dateTime)

            rpcClient.qlua_AddLabel(chartTag, params)
        }


    }

    private fun params(trade: Trade, dateTime: LocalDateTime): HashMap<String, String> {
        val params = HashMap<String, String>()
        val buy = trade.direction == "B"
        params["TEXT"] = ""
        params["IMAGE_PATH"] = path + (if (buy) "up.bmp" else "down.bmp")
        params["ALIGNMENT"] = "LEFT"
        params["YVALUE"] = trade.price.toPlainString()
        params["DATE"] = dateFormat.format(dateTime)
        params["TIME"] = timeFormat.format(dateTime)
        params["TIME"] = "180000"
        params["R"] = "0"
        params["G"] = "0"
        params["B"] = "0"
        params["TRANSPARENCY"] = "0"
        params["TRANSPARENT_BACKGROUND"] = "1"
        params["FONT_FACE_NAME"] = "Arial"
        params["FONT_HEIGHT"] = "12"
        params["HINT"] = "${trade.quantity} ${trade.price.toPlainString()}"
        return params
    }

    private fun round(unrounded: LocalDateTime, interval: BarInterval): LocalDateTime {
        return when (interval) {
            BarInterval.DAY -> unrounded.truncatedTo(ChronoUnit.DAYS)
            BarInterval.HOUR -> unrounded.truncatedTo(ChronoUnit.HOURS)
            BarInterval.MIN_1 -> unrounded.truncatedTo(ChronoUnit.MINUTES)
            BarInterval.HOURS_4 -> unrounded.truncatedTo(ChronoUnit.HOURS).minusHours((unrounded.hour % 4).toLong())
            BarInterval.HOURS_2 -> unrounded.truncatedTo(ChronoUnit.HOURS).minusHours((unrounded.hour % 2).toLong())
            BarInterval.MIN_30 -> unrounded.truncatedTo(ChronoUnit.MINUTES).minusMinutes((unrounded.minute % 30).toLong())
            BarInterval.MIN_20 -> unrounded.truncatedTo(ChronoUnit.MINUTES).minusMinutes((unrounded.minute % 20).toLong())
            BarInterval.MIN_15 -> unrounded.truncatedTo(ChronoUnit.MINUTES).minusMinutes((unrounded.minute % 15).toLong())
            BarInterval.MIN_10 -> unrounded.truncatedTo(ChronoUnit.MINUTES).minusMinutes((unrounded.minute % 10).toLong())
            BarInterval.MIN_6 -> unrounded.truncatedTo(ChronoUnit.MINUTES).minusMinutes((unrounded.minute % 6).toLong())
            BarInterval.MIN_5 -> unrounded.truncatedTo(ChronoUnit.MINUTES).minusMinutes((unrounded.minute % 5).toLong())
        }
    }
}