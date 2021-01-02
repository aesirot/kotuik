package model.robot

import java.math.BigDecimal

class PolzuchiiSellState(val classCode: String,
                         val securityCode: String,
                         var quantity: Int,
                         val startPrice: BigDecimal,
                         val minPrice: BigDecimal,
                         val maxShift: Int) {
}