package dev.dotmatrix.taskbarhero.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import dev.dotmatrix.taskbarhero.paint.Surface

/**
 * Binds the shared layout code to a real Canvas. Everything above this class —
 * the dot painter, the bar layout, the dungeon layout — is plain Kotlin and runs
 * unchanged in tests and in the PNG previewer.
 */
class AndroidSurface(private val canvas: Canvas) : Surface {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bounds = RectF()

    override fun circle(cx: Float, cy: Float, radius: Float, color: Int) {
        fill.color = color
        canvas.drawCircle(cx, cy, radius, fill)
    }

    override fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        fill.color = color
        canvas.drawRect(left, top, right, bottom, fill)
    }

    override fun roundRectFill(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
        bounds.set(left, top, right, bottom)
        fill.color = color
        canvas.drawRoundRect(bounds, radius, radius, fill)
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
        bounds.set(left, top, right, bottom)
        stroke.color = color
        stroke.strokeWidth = strokeWidth
        canvas.drawRoundRect(bounds, radius, radius, stroke)
    }
}
