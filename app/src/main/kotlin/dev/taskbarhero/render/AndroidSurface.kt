package dev.taskbarhero.render

import android.graphics.Canvas
import android.graphics.Paint
import dev.taskbarhero.paint.Surface

/**
 * Binds the shared layout code to a real Canvas. Everything above this class —
 * the pixel painter, the bar layout, the dungeon layout — is plain Kotlin and
 * runs unchanged in tests and in the PNG previewer.
 *
 * Anti-aliasing stays off for rectangles: pixel art wants hard edges, and a
 * fractional cell boundary must not turn into a grey seam.
 */
class AndroidSurface(private val canvas: Canvas) : Surface {

    private val hard = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val smooth = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        hard.color = color
        canvas.drawRect(left, top, right, bottom, hard)
    }

    override fun circle(cx: Float, cy: Float, radius: Float, color: Int) {
        smooth.color = color
        canvas.drawCircle(cx, cy, radius, smooth)
    }
}
