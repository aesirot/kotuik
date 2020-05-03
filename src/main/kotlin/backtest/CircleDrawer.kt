package backtest

import org.jfree.ui.Drawable
import java.awt.*
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D


class CircleDrawer(private val outlinePaint: Paint?,
                   private val outlineStroke: Stroke?,
                   private val fillPaint: Paint?) : Drawable {
    override fun draw(g2: Graphics2D, area: Rectangle2D) {
        val ellipse: Ellipse2D = Ellipse2D.Double(area.x,
                area.y,
                area.width,
                area.height)
        if (fillPaint != null) {
            g2.paint = fillPaint
            g2.fill(ellipse)
        }
        if (outlinePaint != null &&
                outlineStroke != null) {
            g2.paint = outlinePaint
            g2.stroke = outlineStroke
            g2.draw(ellipse)
        }
        g2.paint = Color.black
        g2.stroke = BasicStroke(1.0f)
        val line1: Line2D = Line2D.Double(area.centerX,
                area.minY,
                area.centerX,
                area.maxY)
        val line2: Line2D = Line2D.Double(area.minX,
                area.centerY,
                area.maxX,
                area.centerY)
        g2.draw(line1)
        g2.draw(line2)
    }

}