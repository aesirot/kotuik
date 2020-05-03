package backtest

import java.math.BigDecimal
import java.time.LocalDateTime

class Bar(var datetime: LocalDateTime, var open: BigDecimal, var high: BigDecimal, var low: BigDecimal, var close: BigDecimal, var volume: Long) {

    override fun toString(): String {
        return "Bar(datetime=$datetime, open=$open, high=$high, low=$low, close=$close, volume=$volume)"
    }
}