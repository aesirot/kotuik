package model

import bond.BusinessCalendar
import bond.CalcYield
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "robot")
class RobotState {

    @Id
    @Column(name = "robot_id")
    var id = ""

    @Column
    var type: String = ""

    @Column(length = 4000)
    var state: String? = null

    @Column
    var parentId: String? = null

    @Column
    var created: java.time.LocalDateTime = LocalDateTime.now()

    @Column
    var updated: java.time.LocalDateTime = LocalDateTime.now()

}