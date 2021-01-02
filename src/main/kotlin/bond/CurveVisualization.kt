package bond

import com.enfernuz.quik.lua.rpc.api.messages.GetParamEx
import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import common.Connector
import common.HibernateUtil
import common.StakanSubscriber
import model.Bond
import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartPanel
import org.jfree.chart.plot.PlotOrientation
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer
import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import robot.orel.OrelOFZ
import java.awt.Color
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Ellipse2D
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.swing.JFrame
import kotlin.system.exitProcess


fun main() {
    val curve = CurveHolder.curveOFZ()
    HibernateUtil.shutdown()

    val orelOFZ = OrelOFZ()
    orelOFZ.init()
    orelOFZ.start()
    CurveVisualization.visualize(curve)
}

object CurveVisualization {

    fun visualize(curve: Curve) {
        val dataset = XYSeriesCollection()

        val buySeries = XYSeries("buy")
        dataset.addSeries(buySeries)

        val sellSeries = XYSeries("sell")
        dataset.addSeries(sellSeries)

        val chart = ChartFactory.createScatterPlot(
                "Кривая доходности",
                "Дюрация",
                "Доходность",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false)
        (chart.plot as XYPlot).renderer.baseShape = Ellipse2D.Double(-3.0, -3.0, 6.0, 6.0)
        (chart.plot as XYPlot).renderer.setSeriesShape(0, Ellipse2D.Double(-3.0, -3.0, 6.0, 6.0))
        (chart.plot as XYPlot).renderer.setSeriesShape(1, Ellipse2D.Double(-3.0, -3.0, 6.0, 6.0))


        val curveDataset = XYSeriesCollection()
        val curveSeries = XYSeries("curve")
        curveDataset.addSeries(curveSeries)

        val curveRenderer = XYLineAndShapeRenderer()
        curveRenderer.setSeriesPaint(0, Color.BLACK)
        curveRenderer.setSeriesShapesVisible(0, false)
        (chart.plot as XYPlot).setRenderer(1, curveRenderer)
        (chart.plot as XYPlot).setDataset(1, curveDataset)

        val panel = ChartPanel(chart)

        val labels = HashMap<String, String>()
        (chart.plot as XYPlot).renderer.setBaseItemLabelGenerator { dataset, series, item ->
            val x = dataset.getX(series, item)
            val y = dataset.getY(series, item)
            labels[labelKey(x, y)]!!
        }
        (chart.plot as XYPlot).renderer.setBaseItemLabelsVisible(true)

        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            for (bond in curve.bonds) {
                StakanSubscriber.subscribe("TQOB", bond.code)
            }
        }
        Thread.sleep(20000)

        Thread(Runnable {
                while (true) {
                    try {
                    //rebuild in OrelOFZ
                    synchronized(rpcClient) {
                        val settleDt = BusinessCalendar.addDays(LocalDate.now(), 1)

                        buySeries.clear()
                        sellSeries.clear()
                        curveSeries.clear()
                        labels.clear()

                        for (bond in curve.bonds) {
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
                            addPoint(labels, durationBID, ytm, bond, buySeries)

                            val dirtyPrice2 = ask + nkd.divide(bond.nominal, 6, RoundingMode.HALF_UP) * BigDecimal(100)
                            val ytm2 = CalcYield.effectiveYTM(bond, settleDt, dirtyPrice2)
                            val durationASK = CalcDuration.durationDays(bond, settleDt, ytm, dirtyPrice)

                            addPoint(labels, durationASK, ytm2, bond, sellSeries)
                        }
                    }

                    val step = 36
                    for (i in 1..120) {
                        val days = step * i
                        val approxYTM = curve.approx(BigDecimal(days)) * 100
                        curveSeries.add(days.toDouble() / 365, approxYTM)
                    }

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    Thread.sleep(30000)
                }
        }).start()

        val frame = JFrame("OFZ PD")
        frame.contentPane.add(panel)
        frame.setSize(400, 300)

        // Enable the termination button [cross on the upper right edge]:
        frame.addWindowListener(
                object : WindowAdapter() {
                    override fun windowClosing(e: WindowEvent) {
                        exitProcess(0)
                    }
                }
        )
        frame.isVisible = true
    }

    private fun addPoint(labels: HashMap<String, String>, duration: BigDecimal, ytm: BigDecimal, bond: Bond, series: XYSeries) {
        labels[labelKey(duration.toDouble() / 365, ytm.toDouble() * 100)] = bond.code.substring(2, 7)
        series.add(duration.toDouble() / 365, ytm.toDouble() * 100)
    }

    private fun nkd(classCode: String, secCode: String, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
        val args = GetParamEx.Args(classCode, secCode, "ACCRUEDINT")
        val ex = rpcClient.qlua_getParamEx(args)
        return BigDecimal(ex.paramValue)
    }

    private fun labelKey(x: Number, y: Number): String {
        return "$x/$y"
    }

}