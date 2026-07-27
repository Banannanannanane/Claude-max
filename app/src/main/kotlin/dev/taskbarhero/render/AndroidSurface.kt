package dev.taskbarhero.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import dev.taskbarhero.paint.Surface

/**
 * Binds the shared layout code to a real Canvas. Everything above this class —
 * the pixel painter, the bar layout, the dungeon layout — is plain Kotlin and
 * runs unchanged in tests and in the PNG previewer.
 *
 * Anti-aliasing stays off for rectangles: pixel art wants hard edges, and a
 * fractional cell boundary must not turn into a grey seam.
 */
class AndroidSurface(private val canvas: Canvas, private val sheet: Bitmap? = null) : Surface {

    private val hard = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val smooth = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // isFilterBitmap stays off: smoothing a pixel sheet is what makes drop-in art
    // look like a photograph of art.
    private val blit = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val src = Rect()
    private val dst = RectF()

    override fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        hard.color = color
        canvas.drawRect(left, top, right, bottom, hard)
    }

    override fun circle(cx: Float, cy: Float, radius: Float, color: Int) {
        smooth.color = color
        canvas.drawCircle(cx, cy, radius, smooth)
    }

    override fun image(
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): Boolean {
        val bitmap = sheet ?: return false
        // A mapping that points off the sheet draws nothing and lets the caller
        // fall back, rather than throwing inside a widget update.
        if (srcX < 0 || srcY < 0 || srcX + srcWidth > bitmap.width || srcY + srcHeight > bitmap.height) {
            return false
        }
        src.set(srcX, srcY, srcX + srcWidth, srcY + srcHeight)
        dst.set(left, top, right, bottom)
        canvas.drawBitmap(bitmap, src, dst, blit)
        return true
    }
}
