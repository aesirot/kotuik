package model.integration

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "moex_trade")
class MoexTrade {

    @Id
    @Column(name = "moex_trade_id")
    var moexTradeId: Long = 0

    @Column(name = "sec_code", length = 250)
    var securityCode: String = ""

    @Column
    var quantity: Int = 0

    @Column(precision = 20, scale = 6)
    var price: Double = 0.0

    @Column(name = "trade_datetime")
    var trade_datetime: LocalDateTime = LocalDateTime.MIN

    @Column(name = "yield", precision = 20, scale = 6)
    var yieldValue: Double? = null

    @Column
    var duration: Int? = null

}