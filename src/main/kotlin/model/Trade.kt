package model

import java.math.BigDecimal
import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "trade")
class Trade {
    companion object {
        fun build(classCode: String,
                  securityCode: String,
                  direction: String,
                  quantity: Int,
                  price: BigDecimal,
                  currency: String,
                  amount: BigDecimal,
                  trade_datetime: LocalDateTime,
                  transId: String?,
                  tradeId: Int,
                  orderNum: String?,
                  quikTradeNum: String?): Trade {
            val trade = Trade()
            trade.classCode = classCode
            trade.securityCode = securityCode
            trade.direction = direction
            trade.quantity = quantity
            trade.price = price
            trade.currency = currency
            trade.amount = amount
            trade.trade_datetime = trade_datetime
            trade.transId = transId
            trade.tradeId = tradeId
            trade.orderNum = orderNum
            trade.quikTradeNum = quikTradeNum

            return trade
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "s_trade")
    @Column(name = "trade_id")
    var tradeId: Int = 0

    @Column(name = "class_code", length = 30)
    var classCode: String = ""

    @Column(name = "sec_code", length = 250)
    var securityCode: String = ""

    @Column(length = 10)
    var direction: String = ""

    @Column
    var quantity: Int = 0

    @Column(precision = 20, scale = 6)
    var price: BigDecimal = BigDecimal.ZERO

    @Column(length = 20)
    var currency: String = ""

    @Column(precision = 20, scale = 6)
    var amount: BigDecimal = BigDecimal.ZERO

    @Column(name = "trade_datetime")
    var trade_datetime: LocalDateTime = LocalDateTime.now()

    @Column(name = "trans_id", length = 30)
    var transId: String? = null

    @Column(name = "order_num", length = 30)
    var orderNum: String? = null

    @Column(name = "quik_trade_id", length = 30)
    var quikTradeNum: String? = null


    @Column
    var position: Int? = null

    @Column(name = "buy_amount", precision = 20, scale = 6)
    var buyAmount: BigDecimal? = null

    @Column(name = "sell_amount", precision = 20, scale = 6)
    var sellAmount: BigDecimal? = null

    @Column(name = "realized_pnl", precision = 20, scale = 6)
    var realizedPnL: BigDecimal? = null

    @Column(name = "fee_amount", precision = 20, scale = 6)
    var feeAmount: BigDecimal? = null
}