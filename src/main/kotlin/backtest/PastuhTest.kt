package backtest

import java.math.BigDecimal
import java.time.LocalDateTime

fun main () {
    var bars = ArrayList<Bar>()
    bars.add(Bar(LocalDateTime.now(), BigDecimal("100.1"), BigDecimal("100.6"), BigDecimal("100.1"), BigDecimal("100.4"), 5))
    bars.add(Bar(LocalDateTime.now(), BigDecimal("100.5"), BigDecimal("100.5"), BigDecimal("100.5"), BigDecimal("100.5"), 1))
    bars.add(Bar(LocalDateTime.now(), BigDecimal("100.2"), BigDecimal("100.2"), BigDecimal("100.2"), BigDecimal("100.2"), 2))

    val result = Pastuh.optimalMaxBuyPrice(bars, BigDecimal("0.3"), 1)
    if (result != BigDecimal("100.2")) {
        throw Exception("Expected 100.2, got $result")
    }
}