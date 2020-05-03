package db

import java.math.BigDecimal
import java.time.LocalDateTime

class Trade(val classCode: String,
            val securityCode: String,
            val direction: String,
            val quantity: Int,
            val price: BigDecimal,
            val currency: String,
            val amount: BigDecimal,
            val trade_datetime: LocalDateTime,
            val transId: String?,
            var tradeId: Int,
            val orderNum: String?,
            val quikTradeNum: String?
)  {
    var position: Int? = null
    var buyAmount: BigDecimal? = null
    var sellAmount: BigDecimal? = null
    var realizedPnL: BigDecimal? = null
    var feeAmount: BigDecimal? = null
}