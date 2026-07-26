package dev.dotmatrix.taskbarhero.preview

import dev.dotmatrix.taskbarhero.paint.Surface
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

/** The desktop twin of AndroidSurface — same layout code, Graphics2D instead of Canvas. */
class AwtSurface(private val g: Graphics2D) : Surface {

    init {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    override fun circle(cx: Float, cy: Float, radius: Float, color: Int) {
        g.color = argb(color)
        g.fill(Ellipse2D.Float(cx - radius, cy - radius, radius * 2f, radius * 2f))
    }

    override fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        g.color = argb(color)
        g.fill(Rectangle2D.Float(left, top, right - left, bottom - top))
    }

    override fun roundRectFill(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
        g.color = argb(color)
        g.fill(RoundRectangle2D.Float(left, top, right - left, bottom - top, radius * 2f, radius * 2f))
    }

    override fun roundRectStroke(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        strokeWidth: Float,
        color: Int,
    ) {
        g.color = argb(color)
        g.stroke = BasicStroke(strokeWidth)
        g.draw(RoundRectangle2D.Float(left, top, right - left, bottom - top, radius * 2f, radius * 2f))
    }

    private fun argb(color: Int) = Color(color, true)
}
