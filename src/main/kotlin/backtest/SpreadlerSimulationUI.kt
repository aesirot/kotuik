package backtest

import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartPanel
import org.jfree.chart.JFreeChart
import org.jfree.chart.annotations.XYAnnotation
import org.jfree.chart.annotations.XYDrawableAnnotation
import org.jfree.chart.annotations.XYPointerAnnotation
import org.jfree.chart.axis.DateAxis
import org.jfree.chart.plot.Marker
import org.jfree.chart.plot.PlotOrientation
import org.jfree.chart.plot.ValueMarker
import org.jfree.data.time.Hour
import org.jfree.data.time.Minute
import org.jfree.data.xy.XYDataset
import org.jfree.ui.*
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints


fun main() {
    val demo = TimeSeriesMarker("Пример маркеров")
    demo.pack()
    RefineryUtilities.centerFrameOnScreen(demo)
    demo.isVisible = true

}

class SpreadlerSimulationUI {
}


class TimeSeriesMarker(title: String?) : ApplicationFrame(title) {
    private fun createChart(data: XYDataset): JFreeChart {
        val chart = ChartFactory.createScatterPlot(
                "Пример аннотаций и маркеров", null, null, data,
                PlotOrientation.VERTICAL, true, true, false)
        // Настройка диаграммы ...
        val plot = chart.xyPlot

        // Определение временной оси
        val domainAxis = DateAxis(null)
        domainAxis.upperMargin = 0.50
        plot.domainAxis = domainAxis

        // Скрытие осевых линий
        plot.axisOffset = RectangleInsets(1.0, 1.0, 1.0, 1.0)
        var axis = plot.domainAxis
        axis.isAxisLineVisible = false
        axis = plot.rangeAxis
        axis.isAxisLineVisible = false
        val rangeAxis = plot.rangeAxis
        rangeAxis.upperMargin = 0.30
        rangeAxis.lowerMargin = 0.50
        val start: Marker = createRangeMarker(200.0,
                "Стартовая цена предложений",
                RectangleAnchor.BOTTOM_RIGHT,
                TextAnchor.TOP_RIGHT, Color.red)
        val target: Marker = createRangeMarker(175.0,
                "Выгодная цена",
                RectangleAnchor.TOP_RIGHT,
                TextAnchor.BOTTOM_RIGHT, Color.blue)
        plot.addRangeMarker(start)
        plot.addRangeMarker(target)
        val hour: Hour = Dataset.getHour(2)
        var millis = hour.firstMillisecond.toDouble()
        val originalEnd: Marker = createRangeMarker(millis,
                "Закрытие торговой сессии (02:00)",
                RectangleAnchor.TOP_LEFT,
                TextAnchor.TOP_RIGHT, Color.red)
        val min = Minute(15, hour)
        millis = min.firstMillisecond.toDouble()
        val currentEnd: Marker = createRangeMarker(millis,
                "Завершение сделок (02:15)",
                RectangleAnchor.TOP_RIGHT,
                TextAnchor.TOP_LEFT, Color.blue)
        plot.addDomainMarker(originalEnd)
        plot.addDomainMarker(currentEnd)

        // Аннотация лучшего предложения
        val h: Hour = Dataset.getHour(2)
        val m = Minute(10, h)
        millis = m.firstMillisecond.toDouble()
        val cd = CircleDrawer(Color.red,
                BasicStroke(1.0f), null)
        val bestBid: XYAnnotation = XYDrawableAnnotation(millis,
                163.0, 11.0, 11.0, cd)
        plot.addAnnotation(bestBid)
        plot.addAnnotation(createPtrAnnotation(millis))
        return chart
    }

    private fun createRangeMarker(value: Double,
                                  caption: String,
                                  ranchor: RectangleAnchor,
                                  tanchor: TextAnchor,
                                  color: Color): Marker {
        val marker: Marker = ValueMarker(value)
        marker.setPaint(color)
        marker.setLabel(caption)
        marker.setLabelAnchor(ranchor)
        marker.setLabelTextAnchor(tanchor)

        return marker
    }

    private fun createPtrAnnotation(millis: Double): XYPointerAnnotation {
        val pointer: XYPointerAnnotation
        pointer = XYPointerAnnotation("Выгодное предложение",
                millis, 163.0,
                3.0 * Math.PI / 4.0)
        pointer.baseRadius = 35.0
        pointer.tipRadius = 10.0
        pointer.font = Font("SansSerif", Font.PLAIN, 9)
        pointer.paint = Color.blue
        pointer.textAnchor = TextAnchor.HALF_ASCENT_RIGHT
        return pointer
    }

    init {
        val data: XYDataset = Dataset.createSuppliersBids()
        val chart = createChart(data)
        //chart.antiAlias = false
        //chart.textAntiAlias = false

        val rh = RenderingHints(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
        rh[RenderingHints.KEY_ANTIALIASING] = RenderingHints.VALUE_ANTIALIAS_OFF
        chart.renderingHints = rh

        val chartPanel = ChartPanel(chart)
        //chartPanel.preferredSize = Dimension(560, 480)
        contentPane = chartPanel
    }
}