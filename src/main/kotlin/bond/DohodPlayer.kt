package bond

import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.google.common.collect.Lists
import common.DBService
import common.HibernateUtil
import common.StakanProvider
import model.BidAskLog
import model.Bond
import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartPanel
import org.jfree.chart.plot.PlotOrientation
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer
import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import org.jfree.util.ShapeUtilities
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Ellipse2D
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import javax.swing.JFrame
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.system.exitProcess


fun main() {
    val start = LocalDateTime.of(2021, 1, 4, 10, 3);
    val stop = LocalDateTime.of(2021, 1, 8, 19, 3);
    DohodPlayer.init(start, stop)

    //val str = "RU000A0JWZY6;2020-12-24T10:37:27.817762700;112.6010;112.814290;1037;0.066;0.0505;0.017980233288;-0.000244916445"
    val str = "SU26207RMFS9;2021-01-08T14:58:41.440335;113.7020;113.904380;1768.169312906505;0.055395;0.054650;0.000347222312;-0.000397777688;325"

    DohodPlayer.setSquare(4.0, 6.0, 5.0, 6.0)

    val split = str.split(";")
    val duration = BigDecimal(split[4])
    val tradeYtm = BigDecimal(split[5])
    val approxBuy = BigDecimal(split[6])
    val ytmCorrection = BigDecimal(split[7])

    DohodPlayer.addBond(DBService.getBond(split[0]))

    DohodPlayer.visualize(duration, tradeYtm, approxBuy, ytmCorrection)
}

object DohodPlayer {
    private val map = TreeMap<LocalDateTime, MutableMap<String, BidAskLog>>()
    private var currentDtm: LocalDateTime = LocalDateTime.now()
    private lateinit var curve: Curve
    private lateinit var frame: JFrame
    private lateinit var bonds: MutableSet<Bond>

    private var minX: Double? = null
    private var maxX: Double? = null
    private var minY: Double? = null
    private var maxY: Double? = null

    private val points = ArrayList<ArrayList<BigDecimal>>()

    private val log = LoggerFactory.getLogger(this::class.simpleName)

    fun init(start: LocalDateTime, end: LocalDateTime) {
        currentDtm = start
        this.curve = CurveHolder.curveOFZ()

        bonds = HashSet()
        bonds.addAll(curve.bonds)

        HibernateUtil.getSessionFactory().openSession().use { session ->
            val query = session.createQuery(
                "from BidAskLog where dtm >= :s and dtm <= :e", BidAskLog::class.java
            )
            query.setParameter("s", start)
            query.setParameter("e", end)
            val list = query.list()

            for (log in list) {
                if (!map.containsKey(log.dtm)) {
                    map[log.dtm] = TreeMap()
                }

                map[log.dtm]!![log.code] = log
            }
        }
    }

    private lateinit var buySeries: XYSeries
    private lateinit var sellSeries: XYSeries
    private lateinit var curveSeries: XYSeries

    private lateinit var tradeSeries: XYSeries
    private lateinit var approxSeries: XYSeries
    private lateinit var approxDeltaSeries: XYSeries
    private lateinit var approxDeltaCurrentSeries: XYSeries
    private val curveBuilder = CurveBuilder(StakanSimulator())
    private val labels = HashMap<String, String>()

    fun visualize(duration: BigDecimal, tradeYtm: BigDecimal, approx: BigDecimal, correction: BigDecimal) {
        points.add(Lists.newArrayList(duration, tradeYtm, approx, correction))

        currentDtm = map.firstKey()

        val dataset = XYSeriesCollection()

        buySeries = XYSeries("buy")
        dataset.addSeries(buySeries)

        sellSeries = XYSeries("sell")
        dataset.addSeries(sellSeries)

        val durY = duration.toDouble() / 365
        tradeSeries = XYSeries("сделка")
        tradeSeries.add(durY, tradeYtm.toDouble() * 100)
        dataset.addSeries(tradeSeries)

        approxSeries = XYSeries("апрокс B")
        approxSeries.add(durY, approx.toDouble() * 100)
        dataset.addSeries(approxSeries)

        approxDeltaSeries = XYSeries("апрокс B корр")
        approxDeltaSeries.add(durY, (approx + correction).toDouble() * 100)
        dataset.addSeries(approxDeltaSeries)

        approxDeltaCurrentSeries = XYSeries("апроксB тек")
        dataset.addSeries(approxDeltaCurrentSeries)

        val chart = ChartFactory.createScatterPlot(
            "Кривая доходности",
            "Дюрация",
            "Доходность",
            dataset,
            PlotOrientation.VERTICAL,
            true, true, false
        )
        (chart.plot as XYPlot).renderer.baseShape = Ellipse2D.Double(-3.0, -3.0, 6.0, 6.0)
        (chart.plot as XYPlot).renderer.setSeriesShape(0, Ellipse2D.Double(-3.0, -3.0, 6.0, 6.0))
        (chart.plot as XYPlot).renderer.setSeriesShape(1, Ellipse2D.Double(-3.0, -3.0, 6.0, 6.0))

        (chart.plot as XYPlot).renderer.setSeriesShape(2, ShapeUtilities.createRegularCross(6f, 0f))
        (chart.plot as XYPlot).renderer.setSeriesShape(3, ShapeUtilities.createRegularCross(6f, 0f))
        (chart.plot as XYPlot).renderer.setSeriesShape(4, ShapeUtilities.createRegularCross(6f, 0f))
        (chart.plot as XYPlot).renderer.setSeriesShape(5, ShapeUtilities.createRegularCross(6f, 0f))


        if (minX != null) {
            (chart.plot as XYPlot).domainAxis.setRange(minX!!, maxX!!)
            (chart.plot as XYPlot).rangeAxis.setRange(minY!!, maxY!!)
        } else {
            (chart.plot as XYPlot).rangeAxis.setRange(3.10, 7.10)
            //(chart.plot as XYPlot).rangeAxis.setRange(4.10, 6.70)
        }

        val curveDataset = XYSeriesCollection()
        curveSeries = XYSeries("curve")
        curveDataset.addSeries(curveSeries)

        val curveRenderer = XYLineAndShapeRenderer()
        curveRenderer.setSeriesPaint(0, Color.BLACK)
        curveRenderer.setSeriesShapesVisible(0, false)
        (chart.plot as XYPlot).setRenderer(1, curveRenderer)
        (chart.plot as XYPlot).setDataset(1, curveDataset)

        val panel = ChartPanel(chart)

        (chart.plot as XYPlot).renderer.setBaseItemLabelGenerator { dataset, series, item ->
            if (series > 1) {
                return@setBaseItemLabelGenerator null
            }
            val x = dataset.getX(series, item)
            val y = dataset.getY(series, item)
            labels[labelKey(x, y)]!!
        }
        (chart.plot as XYPlot).renderer.setBaseItemLabelsVisible(true)


        frame = JFrame("Доход")
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

        frame.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (KeyEvent.VK_LEFT == e.keyCode) {
                    currentDtm = currentDtm.minus(15, ChronoUnit.MINUTES)

                    if (currentDtm <= map.firstKey()) {
                        currentDtm = map.firstKey()
                    } else if (!map.containsKey(currentDtm)) {
                        currentDtm = map.floorKey(currentDtm)
                    }

                    showCurve()
                } else if (KeyEvent.VK_RIGHT == e.keyCode) {
                    currentDtm = currentDtm.plus(15, ChronoUnit.MINUTES)

                    if (currentDtm >= map.lastKey()) {
                        currentDtm = map.lastKey()
                    } else if (!map.containsKey(currentDtm)) {
                        currentDtm = map.ceilingKey(currentDtm)
                    }

                    showCurve()
                }
            }
        })

        frame.isVisible = true

        showCurve()
    }

    private fun showCurve() {
        val timeStr = currentDtm.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        frame.title = "Доход $timeStr"

        try {
            val settleDt = BusinessCalendar.addDays(currentDtm.toLocalDate(), 1)

            curveBuilder.build(curve, settleDt)

            buySeries.clear()
            sellSeries.clear()
            curveSeries.clear()
            labels.clear()
            approxDeltaCurrentSeries.clear()

            for (point in points) {
                val approx = curve.approx(point[0])
                approxDeltaCurrentSeries.add(point[0].toDouble() / 365, (approx + point[3].toDouble()) * 100)
            }


            for (bond in bonds) {
                if (!map[currentDtm]!!.containsKey(bond.code)) {
                    continue
                }

                val bid = map[currentDtm]!![bond.code]!!.bid
                val ask = map[currentDtm]!![bond.code]!!.ask
                val nkd = StakanSimulator().nkd(bond, settleDt)

                val dirtyPrice = bid + nkd.divide(bond.nominal, 6, RoundingMode.HALF_UP) * BigDecimal(100)
                val ytm = CalcYieldDouble.effectiveYTM(bond, settleDt, dirtyPrice)
                val durationBID = CalcDuration.durationDays(bond, settleDt, ytm, dirtyPrice)

                //val days = ChronoUnit.DAYS.between(settleDt, bond.earlyRedemptionDate ?: bond.maturityDt)
                //bidTrace.addPoint(days.toDouble() / 365, ytm.toDouble() * 100)
                addPoint(labels, durationBID, ytm, bond, buySeries)

                val dirtyPrice2 = ask + nkd.divide(bond.nominal, 6, RoundingMode.HALF_UP) * BigDecimal(100)
                val ytm2 = CalcYieldDouble.effectiveYTM(bond, settleDt, dirtyPrice2)
                val durationASK = CalcDuration.durationDays(bond, settleDt, ytm, dirtyPrice)

                addPoint(labels, durationASK, ytm2, bond, sellSeries)
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
    }

    private fun addPoint(
        labels: HashMap<String, String>,
        duration: BigDecimal,
        ytm: BigDecimal,
        bond: Bond,
        series: XYSeries
    ) {
        labels[labelKey(duration.toDouble() / 365, ytm.toDouble() * 100)] = bond.code.substring(2, 7)
        series.add(duration.toDouble() / 365, ytm.toDouble() * 100)
    }


    private fun labelKey(x: Number, y: Number): String {
        return "$x/$y"
    }

    fun addBond(bond: Bond) {
        bonds.add(bond)
    }

    fun setSquare(minX: Double, maxX: Double, minY: Double, maxY: Double) {
        this.minX = minX
        this.maxX = maxX
        this.minY = minY
        this.maxY = maxY
    }

    private class StakanSimulator : StakanProvider() {

        override fun stakan(classCode: String, secCode: String): GetQuoteLevel2.Result {
            if (!map.containsKey(currentDtm) || !map[currentDtm]!!.containsKey(secCode)) {
                return GetQuoteLevel2.Result("0", "0", Lists.newArrayList(), Lists.newArrayList())
            }

            val bidAskLog = map[currentDtm]!![secCode]!!

            val bid = GetQuoteLevel2.QuoteEntry(bidAskLog.bid.toPlainString(), "1")
            val ask = GetQuoteLevel2.QuoteEntry(bidAskLog.ask.toPlainString(), "1")

            return GetQuoteLevel2.Result("1", "1", Lists.newArrayList(bid), Lists.newArrayList(ask))
        }

        override fun nkd(bond: Bond, settleDate: LocalDate): BigDecimal {
            val settleDt = BusinessCalendar.addDays(currentDtm.toLocalDate(), 1)
            return CalcYield.calcAccrual(bond, settleDt)
        }
    }

}