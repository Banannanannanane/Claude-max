package dev.taskbarhero.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import dev.taskbarhero.paint.Surface

/**
 * The phone's half of [Surface]: the same layout code, drawn onto a Canvas.
 *
 * Both paints are built once and reused — a widget redraw allocates nothing — and
 * both have every smoothing flag off. Anti-aliasing a pixel sheet or a pixel font
 * is how bought art starts looking like a photograph of a drawing.
 */
class AndroidSurface(
    private val canvas: Canvas,
    private val art: GameArt.Load.Ready,
) : Surface {

    private val fill = Paint().apply { isAntiAlias = false }

    private val blit = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        isDither = false
    }

    private val ink = Paint().apply {
        isAntiAlias = false
        isSubpixelText = false
        isLinearText = false
        typeface = art.font
    }

    private val src = Rect()
    private val dst = RectF()

    override fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        fill.color = color
        canvas.drawRect(left, top, right, bottom, fill)
    }

    override fun image(
        sheet: Surface.Sheet,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        val bitmap: Bitmap = when (sheet) {
            Surface.Sheet.DUNGEON -> art.dungeonBitmap
            Surface.Sheet.UI -> art.uiBitmap
            Surface.Sheet.ICONS -> art.iconBitmap
            Surface.Sheet.SCENE -> art.sceneBitmap
        }
        if (srcX < 0 || srcY < 0 || srcX + srcWidth > bitmap.width || srcY + srcHeight > bitmap.height) return
        src.set(srcX, srcY, srcX + srcWidth, srcY + srcHeight)
        dst.set(left, top, right, bottom)
        canvas.drawBitmap(bitmap, src, dst, blit)
    }

    override fun text(x: Float, y: Float, text: String, sizePx: Float, color: Int) {
        ink.textSize = sizePx
        ink.color = color
        // Callers give the top-left; Canvas wants a baseline.
        canvas.drawText(text, x, y - ink.fontMetrics.ascent, ink)
    }

    override fun measure(text: String, sizePx: Float): Float {
        ink.textSize = sizePx
        return ink.measureText(text)
    }

    override fun lineHeight(sizePx: Float): Float {
        ink.textSize = sizePx
        val m = ink.fontMetrics
        return m.descent - m.ascent
    }
}
