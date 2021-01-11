package robot.orel

import bond.BusinessCalendar
import bond.Curve
import bond.CurveBuilder
import bond.CurveHolder
import common.Connector
import common.StakanSubscriber
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import robot.AbstractLoopRobot
import java.time.LocalDate

class OrelOFZ : AbstractLoopRobot() {

    @Transient
    lateinit var curve: Curve

    @Transient
    lateinit var log: Logger

    override fun name(): String = "OrelOFZ"

    override fun init() {
        super.init()
        log = LoggerFactory.getLogger(this::class.java)
        curve = CurveHolder.curveOFZ()

        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            for (bond in curve.bonds) {
                StakanSubscriber.subscribe("TQOB", bond.code)
            }
        }
    }

    override fun execute() {
        val settleDate = BusinessCalendar.addDays(LocalDate.now(), 1)
        CurveBuilder.stakanBuilder().build(curve, settleDate)
    }

}