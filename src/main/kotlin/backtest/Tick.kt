package backtest

import java.math.BigDecimal
import java.time.LocalDateTime

class Tick(var datetime:LocalDateTime, var last: BigDecimal, var volume: Int) {
}