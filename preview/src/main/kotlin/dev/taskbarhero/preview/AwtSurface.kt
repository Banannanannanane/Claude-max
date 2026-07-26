package dev.taskbarhero.preview

import dev.taskbarhero.paint.Surface
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D

/** The desktop twin of AndroidSurface — same layout code, Graphics2D instead of Canvas. */
class AwtSurface(private val g: Graphics2D) : Surface {

    init {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.composite = AlphaComposite.SrcOver
    }

    override fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        g.color = Color(color, true)
        g.fill(Rectangle2D.Float(left, top, right - left, bottom - top))
    }

    override fun circle(cx: Float, cy: Float, radius: Float, color: Int) {
        g.color = Color(color, true)
        g.fill(Ellipse2D.Float(cx - radius, cy - radius, radius * 2f, radius * 2f))
    }
}
