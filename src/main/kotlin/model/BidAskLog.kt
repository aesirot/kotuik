package model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "log_bid_ask")
class BidAskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "s_log")
    @Column(name = "log_id")
    var id = 0

    @Column
    var code: String = ""

    @Column
    var dtm: LocalDateTime = LocalDateTime.now()

    @Column(precision = 20, scale = 10)
    var bid: BigDecimal = BigDecimal.ZERO

    @Column(precision = 20, scale = 10)
    var ask: BigDecimal = BigDecimal.ZERO

}