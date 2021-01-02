package bond

import com.enfernuz.quik.lua.rpc.api.messages.GetParamEx
import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.messages.SubscribeLevel2Quotes
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import common.Connector
import info.monitorenter.gui.chart.Chart2D
import info.monitorenter.gui.chart.ITrace2D
import info.monitorenter.gui.chart.traces.Trace2DLtd
import info.monitorenter.gui.chart.traces.Trace2DSimple
import info.monitorenter.gui.chart.traces.painters.TracePainterDisc
import org.apache.commons.math3.fitting.WeightedObservedPoints
import robot.orel.OrelOFZ
import java.awt.Color
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.swing.JFrame


fun main() {
    // Create a chart:

    // Create a chart:
    val chart = Chart2D()

    chart.gridColor = Color.GRAY
    chart.toolTipType = Chart2D.ToolTipType.DATAVALUES

    // Create an ITrace:
    val bidTrace: ITrace2D = Trace2DSimple()
    bidTrace.setTracePainter(TracePainterDisc())
    bidTrace.color = Color.GREEN

    val sellTrace: ITrace2D = Trace2DSimple()
    sellTrace.setTracePainter(TracePainterDisc())
    sellTrace.color = Color.RED

    // Add the trace to the chart. This has to be done before adding points (deadlock prevention):
    chart.addTrace(bidTrace)
    bidTrace.name = "BUY"

    chart.addTrace(sellTrace)
    sellTrace.name = "SELL"

    val approxTrace: ITrace2D = Trace2DLtd(130)
    approxTrace.color = Color.GRAY
    approxTrace.name = "approx"
    chart.addTrace(approxTrace)

    val curve = CurveHolder.curveOFZ()

    // Make it visible:
    // Create a frame.
    val obs = WeightedObservedPoints()


    try {

        Thread(OrelOFZ()).start()

        Thread(Runnable {
            val rpcClient = Connector.get()
            while (true) {
                synchronized(rpcClient) {
                    val settleDt = BusinessCalendar.addDays(LocalDate.now(), 1)

                    CurveBuilder.stakanBuilder().build(curve, settleDt)

                    bidTrace.removeAllPoints()
                    sellTrace.removeAllPoints()
                    approxTrace.removeAllPoints()

                    for (bond in curve.bonds) {
                        val args = SubscribeLevel2Quotes.Args("TQOB", bond.code)
                        rpcClient.qlua_SubscribeLevelIIQuotes(args)
                        Thread.sleep(500)

                        val args2 = GetQuoteLevel2.Args("TQOB", bond.code)
                        val stakan = rpcClient.qlua_getQuoteLevel2(args2)
                        //лучший bid последний, лучший offer первый
                        val bid = BigDecimal(stakan.bids[stakan.bids.size - 1].price)
                        val ask = BigDecimal(stakan.offers[0].price)
                        val nkd = nkd("TQOB", bond.code, rpcClient)

                        val dirtyPrice = bid + nkd.divide(bond.nominal, 6, RoundingMode.HALF_UP) * BigDecimal(100)
                        val ytm = CalcYield.effectiveYTM(bond, settleDt, dirtyPrice)
                        val durationBID = CalcDuration.durationDays(bond, settleDt, ytm, dirtyPrice)

                        //val days = ChronoUnit.DAYS.between(settleDt, bond.earlyRedemptionDate ?: bond.maturityDt)
                        //bidTrace.addPoint(days.toDouble() / 365, ytm.toDouble() * 100)
                        bidTrace.addPoint(durationBID.toDouble() / 365, ytm.toDouble() * 100)

                        val dirtyPrice2 = ask + nkd.divide(bond.nominal, 6, RoundingMode.HALF_UP) * BigDecimal(100)
                        val ytm2 = CalcYield.effectiveYTM(bond, settleDt, dirtyPrice2)
                        val durationASK = CalcDuration.durationDays(bond, settleDt, ytm, dirtyPrice)

                        //sellTrace.addPoint(days.toDouble() / 365, ytm2.toDouble() * 100)
                        sellTrace.addPoint(durationASK.toDouble() / 365, ytm2.toDouble() * 100)
                    }


                    var step = 36

                    for (i in 1..120) {
                        val d = curve.approx(BigDecimal(step * i))

                        approxTrace.addPoint((step * i).toDouble() / 365, d * 100)
                    }
                }

                Thread.sleep(30000)
            }
        }).start()


    } catch (e: Exception) {
        e.printStackTrace()
        System.exit(0)
    }

    val frame = JFrame("OFZ PD")
    // add the chart to the frame:
    frame.contentPane.add(chart)
    frame.setSize(400, 300)

    // Enable the termination button [cross on the upper right edge]:
    frame.addWindowListener(
            object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    System.exit(0)
                }
            }
    )
    frame.isVisible = true
}

private fun nkd(classCode: String, secCode: String, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
    val args = GetParamEx.Args(classCode, secCode, "ACCRUEDINT")
    val ex = rpcClient.qlua_getParamEx(args)
    return BigDecimal(ex.paramValue)
}
