package dev.taskbarhero.preview

import dev.taskbarhero.paint.Surface
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * The desktop twin of the phone's surface: same layout code, Graphics2D instead
 * of Canvas, and the same three asset files read off disk instead of out of the
 * APK. What this renders is what ships — that is the only reason it exists.
 */
class AwtSurface(
    private val g: Graphics2D,
    private val dungeon: BufferedImage,
    private val ui: BufferedImage,
    private val icons: BufferedImage,
    private val scene: BufferedImage,
    private val font: Font,
) : Surface {

    init {
        // Nothing here is allowed to be smoothed: not the sprites, not the glyphs.
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
    }

    override fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        g.color = Color(color, true)
        g.fillRect(left.toInt(), top.toInt(), (right - left).toInt(), (bottom - top).toInt())
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
        val image = when (sheet) {
            Surface.Sheet.DUNGEON -> dungeon
            Surface.Sheet.UI -> ui
            Surface.Sheet.ICONS -> icons
            Surface.Sheet.SCENE -> scene
        }
        g.drawImage(
            image,
            left.toInt(), top.toInt(), right.toInt(), bottom.toInt(),
            srcX, srcY, srcX + srcWidth, srcY + srcHeight,
            null,
        )
    }

    override fun text(x: Float, y: Float, text: String, sizePx: Float, color: Int) {
        val f = font.deriveFont(Font.PLAIN, sizePx)
        g.font = f
        g.color = Color(color, true)
        // Callers give the top-left; the toolkit wants a baseline.
        g.drawString(text, x.toInt(), (y + g.getFontMetrics(f).ascent).toInt())
    }

    override fun measure(text: String, sizePx: Float): Float =
        g.getFontMetrics(font.deriveFont(Font.PLAIN, sizePx)).stringWidth(text).toFloat()

    override fun lineHeight(sizePx: Float): Float =
        g.getFontMetrics(font.deriveFont(Font.PLAIN, sizePx)).height.toFloat()
}
