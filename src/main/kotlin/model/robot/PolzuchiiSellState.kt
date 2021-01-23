package model.robot

import java.math.BigDecimal

class PolzuchiiSellState(val classCode: String,
                         val securityCode: String,
                         var quantity: Int,
                         var startPrice: BigDecimal,
                         var minPrice: BigDecimal,
                         var maxShift: Int) {
}