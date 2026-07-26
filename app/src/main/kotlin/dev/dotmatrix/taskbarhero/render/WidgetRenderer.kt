package dev.dotmatrix.taskbarhero.render

import android.graphics.Bitmap
import android.graphics.Canvas
import dev.dotmatrix.taskbarhero.engine.Balance
import dev.dotmatrix.taskbarhero.engine.GameState
import dev.dotmatrix.taskbarhero.paint.BarLayout
import dev.dotmatrix.taskbarhero.paint.DotPainter

/**
 * Turns the bar layout into the one bitmap a RemoteViews ImageView can show —
 * a home-screen widget cannot host a custom View, and drawing every dot
 * ourselves is the only way to get the matrix look on stock Android.
 */
object WidgetRenderer {

    /** RemoteViews bitmaps travel over Binder; keep them well under the transaction limit. */
    private const val MAX_BITMAP_PX = 2_048

    fun render(
        state: GameState,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        nowMs: Long,
        recentEvent: String? = null,
        b: Balance = Balance(),
    ): Bitmap {
        val w = widthPx.coerceIn(1, MAX_BITMAP_PX)
        val h = heightPx.coerceIn(1, MAX_BITMAP_PX)
        val unit = BarLayout.unitPx(h.toFloat(), density)
        val rows = BarLayout.rowsFor(h.toFloat(), density)
        val cols = (w / unit).toInt()

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val painter = DotPainter(AndroidSurface(Canvas(bitmap)), unit)
        BarLayout.draw(painter, cols, rows, state, nowMs, recentEvent, b)
        return bitmap
    }
}
