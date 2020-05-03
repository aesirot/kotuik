package db.dao

import common.DBConnector
import db.Trade
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types

object TradeDAO {

    const val insert = "insert into trade (class_code, sec_code, direction, quantity, price, currency, amount, " +
            "trade_datetime, trans_id, quik_trade_id, order_num, position, buy_amount, sell_amount, realized_pnl, fee_amount)\n" +
            "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?,     ?, ?, ?, ?, ?, ?);"

    const val update = "update trade\n" +
            " set class_code = ?,\n" +
            " sec_code = ?,\n" +
            " direction = ?,\n" +
            " quantity = ?,\n" +
            " price = ?,\n" +
            " currency = ?,\n" +
            " amount = ?,\n" +
            " trade_datetime = ?,\n" +
            " trans_id = ?,\n" +
            " quik_trade_id = ?,\n" +
            " order_num = ?,\n" +
            " position = ?,\n" +
            " buy_amount = ?,\n" +
            " sell_amount = ?,\n" +
            " realized_pnl = ?,\n" +
            " fee_amount = ?\n" +
            "where trade_id = ?";

    fun insert(trade: Trade) {
        synchronized(DBConnector) {
            val returnedAttributes = arrayOf("trade_id")
            val statement = DBConnector.connection().prepareStatement(insert, returnedAttributes)
            bind(statement, trade)
            val execute = statement.executeUpdate()

            val resultSet = statement.generatedKeys
            resultSet.next()
            val tradeId = resultSet.getInt(1)
            trade.tradeId = tradeId
        }
    }

    fun update(trade: Trade) {
        synchronized(DBConnector) {
            val statement = DBConnector.connection().prepareStatement(update)
            val i = bind(statement, trade)
            statement.setInt(i, trade.tradeId)

            statement.executeUpdate()
        }

    }

    fun select(where: String): List<Trade> {
        return select(where, null)
    }

    fun select(where: String, orderBy: String?): List<Trade> {
        val result = ArrayList<Trade>()
        synchronized(DBConnector) {
            val statement = DBConnector.connection().createStatement()
            var select = "select trade_id, class_code, sec_code, direction, quantity, price, currency, amount, " +
                    "trade_datetime, trans_id, quik_trade_id, order_num, position, buy_amount, sell_amount, " +
                    "realized_pnl, fee_amount " +
                    "from trade  where $where"
            if (orderBy != null) {
                select += " order by $orderBy"
            }
            val resultSet = statement.executeQuery(select)
            while (resultSet.next()) {
                var i = 1
                val tradeId = resultSet.getInt(i++)
                val classCode = resultSet.getString(i++)
                val secCode = resultSet.getString(i++)
                val direction = resultSet.getString(i++)
                val quantity = resultSet.getInt(i++)
                val price = resultSet.getBigDecimal(i++)
                val currency = resultSet.getString(i++)
                val amount = resultSet.getBigDecimal(i++)
                val tradeDateTime = resultSet.getTimestamp(i++).toLocalDateTime()
                val transId = resultSet.getString(i++)
                val quikTradeId = resultSet.getString(i++)
                val orderNum = resultSet.getString(i++)
                val position = resultSet.getInt(i++)
                val buyAmount = resultSet.getBigDecimal(i++)
                val sellAmount = resultSet.getBigDecimal(i++)
                val realizedPnL = resultSet.getBigDecimal(i++)
                val feeAmount = resultSet.getBigDecimal(i++)

                val trade = Trade(classCode, secCode, direction, quantity, price, currency, amount, tradeDateTime,
                        transId, tradeId, orderNum, quikTradeId)
                trade.position = position
                trade.buyAmount = buyAmount
                trade.sellAmount = sellAmount
                trade.realizedPnL = realizedPnL
                trade.feeAmount = feeAmount

                result.add(trade)
            }
        }

        return result
    }

    private fun bind(statement: PreparedStatement, trade: Trade): Int {
        var i = 1;
        statement.setString(i++, trade.classCode)
        statement.setString(i++, trade.securityCode)
        statement.setString(i++, trade.direction)
        statement.setInt(i++, trade.quantity)
        statement.setBigDecimal(i++, trade.price)
        statement.setString(i++, trade.currency)
        statement.setBigDecimal(i++, trade.amount)
        statement.setTimestamp(i++, Timestamp.valueOf(trade.trade_datetime))
        if (trade.transId == null) {
            statement.setNull(i++, Types.VARCHAR)
        } else {
            statement.setString(i++, trade.transId)
        }
        if (trade.quikTradeNum == null) {
            statement.setNull(i++, Types.VARCHAR)
        } else {
            statement.setString(i++, trade.quikTradeNum)
        }
        if (trade.orderNum == null) {
            statement.setNull(i++, Types.VARCHAR)
        } else {
            statement.setString(i++, trade.orderNum)
        }
        if (trade.position != null) {
            statement.setInt(i++, trade.position!!)
        } else {
            statement.setNull(i++, Types.INTEGER)
        }
        if (trade.buyAmount != null) {
            statement.setBigDecimal(i++, trade.buyAmount!!)
        } else {
            statement.setNull(i++, Types.DECIMAL)
        }
        if (trade.sellAmount != null) {
            statement.setBigDecimal(i++, trade.sellAmount!!)
        } else {
            statement.setNull(i++, Types.DECIMAL)
        }
        if (trade.realizedPnL != null) {
            statement.setBigDecimal(i++, trade.realizedPnL!!)
        } else {
            statement.setNull(i++, Types.DECIMAL)
        }
        if (trade.feeAmount != null) {
            statement.setBigDecimal(i++, trade.feeAmount!!)
        } else {
            statement.setNull(i++, Types.DECIMAL)
        }
        return i
    }


}