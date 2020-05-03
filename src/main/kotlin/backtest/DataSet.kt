package backtest

import org.jfree.data.time.*
import org.jfree.data.xy.XYDataset

object Dataset {
    fun createDataset(): XYDataset {
        val s1 = TimeSeries("Курс USD")
        s1.add(Day(11, 5, 2017), 58.0824)
        s1.add(Day(12, 5, 2017), 57.1161)
        s1.add(Day(13, 5, 2017), 57.1640)
        s1.add(Day(16, 5, 2017), 56.5258)
        s1.add(Day(17, 5, 2017), 56.2603)
        s1.add(Day(18, 5, 2017), 56.7383)
        s1.add(Day(19, 5, 2017), 57.4683)
        s1.add(Day(20, 5, 2017), 57.1602)
        s1.add(Day(23, 5, 2017), 56.4988)
        s1.add(Day(24, 5, 2017), 56.5552)
        s1.add(Day(25, 5, 2017), 56.2743)
        val s2 = TimeSeries("Курс EUR")
        s2.add(Day(11, 5, 2017), 63.2634)
        s2.add(Day(12, 5, 2017), 62.1595)
        s2.add(Day(13, 5, 2017), 62.0915)
        s2.add(Day(16, 5, 2017), 61.8449)
        s2.add(Day(17, 5, 2017), 62.0382)
        s2.add(Day(18, 5, 2017), 62.9568)
        s2.add(Day(19, 5, 2017), 63.9967)
        s2.add(Day(20, 5, 2017), 63.6479)
        s2.add(Day(23, 5, 2017), 63.1713)
        s2.add(Day(24, 5, 2017), 63.6189)
        s2.add(Day(25, 5, 2017), 62.9203)
        val s3 = TimeSeries("Нефть марки Brent")
        s3.add(Day(11, 5, 2017), 50.78)
        s3.add(Day(12, 5, 2017), 50.82)
        s3.add(Day(13, 5, 2017), 51.77)
        s3.add(Day(16, 5, 2017), 51.30)
        s3.add(Day(17, 5, 2017), 52.14)
        s3.add(Day(18, 5, 2017), 52.47)
        s3.add(Day(19, 5, 2017), 53.77)
        s3.add(Day(22, 5, 2017), 53.76)
        s3.add(Day(23, 5, 2017), 54.18)
        s3.add(Day(24, 5, 2017), 53.94)
        s3.add(Day(25, 5, 2017), 54.44)
        val dataset = TimeSeriesCollection()
        dataset.addSeries(s1)
        dataset.addSeries(s2)
        dataset.addSeries(s3)
        return dataset
    }

    fun getHour(value: Int): Hour {
        return Hour(value, day)
    }

    val day: Day
        get() = Day(15, 8, 2017)

    fun createSuppliersBids(): XYDataset {
        val hour = getHour(1)
        val hour1 = getHour(1)
        val hour2 = hour1.next() as Hour
        val series1 = TimeSeries("Поставщик 1")
        series1.add(Minute(13, hour), 200.0)
        series1.add(Minute(14, hour), 195.0)
        series1.add(Minute(45, hour), 190.0)
        series1.add(Minute(46, hour), 188.0)
        series1.add(Minute(47, hour), 185.0)
        series1.add(Minute(52, hour), 180.0)
        val series2 = TimeSeries("Поставщик 2")
        series2.add(Minute(25, hour1), 185.0)
        series2.add(Minute(0, hour2), 175.0)
        series2.add(Minute(5, hour2), 170.0)
        series2.add(Minute(6, hour2), 168.0)
        series2.add(Minute(9, hour2), 165.0)
        series2.add(Minute(10, hour2), 163.0)
        val result = TimeSeriesCollection()
        result.addSeries(series1)
        result.addSeries(series2)
        return result
    }
}
