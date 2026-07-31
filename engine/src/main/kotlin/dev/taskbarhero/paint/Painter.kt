package dev.taskbarhero.paint

import dev.taskbarhero.assets.Art
import dev.taskbarhero.assets.Atlas
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Draws the pack's pieces the way each was meant to be used: sprites at whole
 * multiples of their own pixels, panels as nine-slices, bars as three-slices.
 *
 * Every rule here exists because breaking it is what makes bought art look
 * cheap — a stretched corner, a sprite on a half pixel, a bar whose end caps have
 * been squashed.
 */
class Painter(
    val surface: Surface,
    private val dungeon: Atlas,
    private val ui: Atlas,
) {

    /** The pack's own pixel grid: its tallest drawn frame. */
    val artUnit: Int get() = dungeon.unit

    /**
     * The largest whole number of screen pixels one art pixel can be worth inside
     * [heightPx]. Never zero — a sprite shown smaller than it was drawn has lost
     * pixels, and looks it.
     */
    fun scaleFor(heightPx: Float): Int =
        floor(heightPx / artUnit).toInt().coerceAtLeast(1)

    fun fill(x: Float, y: Float, w: Float, h: Float, color: Int) {
        if (w <= 0f || h <= 0f) return
        surface.rect(x, y, x + w, y + h, color)
    }

    fun text(x: Float, y: Float, text: String, sizePx: Float, color: Int) =
        surface.text(x, y, text, sizePx, color)

    fun textRight(right: Float, y: Float, text: String, sizePx: Float, color: Int) =
        surface.text(right - surface.measure(text, sizePx), y, text, sizePx, color)

    fun textCentre(centreX: Float, y: Float, text: String, sizePx: Float, color: Int) =
        surface.text(centreX - surface.measure(text, sizePx) / 2f, y, text, sizePx, color)

    // --- sprites -------------------------------------------------------------

    /**
     * Draws a dungeon sprite standing on ([centreX], [floorY]) at [scale] screen
     * pixels per art pixel.
     *
     * Bottom-aligned and centred, so frames of any shape share one floor: that is
     * what lets a boss drawn at twice the scale tower over the party instead of
     * hovering, or sinking into the flagstones.
     */
    fun sprite(key: String, centreX: Float, floorY: Float, scale: Int) {
        val f = dungeon.require(key)
        val w = f.drawnWidth * scale
        val h = f.drawnHeight * scale
        val left = (centreX - w / 2f).roundToInt().toFloat()
        val top = (floorY - h).roundToInt().toFloat()
        surface.image(Surface.Sheet.DUNGEON, f.x, f.y, f.width, f.height, left, top, left + w, top + h)
    }

    /**
     * The scale at which [key] comes out about [targetPx] tall — for an icon that
     * has to sit beside a line of text without swallowing the line above it.
     */
    fun scaleToFit(key: String, targetPx: Float): Int {
        val f = dungeon.require(key)
        return (targetPx / f.drawnHeight).toInt().coerceAtLeast(1)
    }

    /** How wide [key] comes out at [scale] — what a layout must space fighters by. */
    fun spriteWidth(key: String, scale: Int): Float =
        (dungeon.require(key).drawnWidth * scale).toFloat()

    fun spriteHeight(key: String, scale: Int): Float =
        (dungeon.require(key).drawnHeight * scale).toFloat()

    /** Tiles [key] across a rectangle, clipping the last column and row. */
    fun tile(key: String, x: Float, y: Float, w: Float, h: Float, scale: Int) {
        val f = dungeon.require(key)
        val tw = (f.drawnWidth * scale).toFloat()
        val th = (f.drawnHeight * scale).toFloat()
        if (tw <= 0f || th <= 0f) return

        var ty = y
        while (ty < y + h) {
            val bottom = minOf(ty + th, y + h)
            val srcH = ((bottom - ty) / th * f.height).roundToInt().coerceAtLeast(1)
            var tx = x
            while (tx < x + w) {
                val right = minOf(tx + tw, x + w)
                val srcW = ((right - tx) / tw * f.width).roundToInt().coerceAtLeast(1)
                surface.image(Surface.Sheet.DUNGEON, f.x, f.y, srcW, srcH, tx, ty, right, bottom)
                tx += tw
            }
            ty += th
        }
    }

    // --- interface -----------------------------------------------------------

    /**
     * A nine-slice: corners kept at their drawn size, edges stretched along one
     * axis, middle stretched both ways. Scaling a framed panel as one image is the
     * single most obvious way to make a UI pack look wrong.
     */
    fun panel(key: String, x: Float, y: Float, w: Float, h: Float, corner: Float = 12f) {
        val f = ui.require(key)
        val c = minOf(corner, w / 2f, h / 2f)
        // The source corner keeps the same proportion of the frame as the drawn one.
        val sc = minOf((c / w * f.width).roundToInt().coerceAtLeast(1), f.width / 2)
        val scy = minOf((c / h * f.height).roundToInt().coerceAtLeast(1), f.height / 2)
        val sx = intArrayOf(f.x, f.x + sc, f.x + f.width - sc)
        val sw = intArrayOf(sc, f.width - 2 * sc, sc)
        val sy = intArrayOf(f.y, f.y + scy, f.y + f.height - scy)
        val sh = intArrayOf(scy, f.height - 2 * scy, scy)
        val dx = floatArrayOf(x, x + c, x + w - c)
        val dw = floatArrayOf(c, w - 2 * c, c)
        val dy = floatArrayOf(y, y + c, y + h - c)
        val dh = floatArrayOf(c, h - 2 * c, c)

        for (row in 0..2) for (col in 0..2) {
            if (sw[col] <= 0 || sh[row] <= 0 || dw[col] <= 0f || dh[row] <= 0f) continue
            surface.image(
                Surface.Sheet.UI, sx[col], sy[row], sw[col], sh[row],
                dx[col], dy[row], dx[col] + dw[col], dy[row] + dh[row],
            )
        }
    }

    /**
     * A three-slice bar: caps keep their width, the middle stretches, and the fill
     * is clipped to [fraction] of the track.
     *
     * A bar that keeps one pixel lit above zero and one pixel dark below full is a
     * bar you can read at a glance — "nearly dead" must not look like "dead".
     */
    fun bar(x: Float, y: Float, w: Float, h: Float, fraction: Double, colour: Art.Bar) {
        slices(Art.Bar.BACK, x, y, w, h)
        val f = fraction.coerceIn(0.0, 1.0)
        if (f <= 0.0) return
        var filled = (w * f).toFloat()
        val cap = ui.require(colour.left).width.toFloat()
        if (filled < cap * 2f) filled = cap * 2f
        if (f < 1.0 && filled > w - 2f) filled = w - 2f
        slices(colour, x, y, filled, h)
    }

    private fun slices(colour: Art.Bar, x: Float, y: Float, w: Float, h: Float) {
        val l = ui.require(colour.left)
        val m = ui.require(colour.mid)
        val r = ui.require(colour.right)
        // Caps are scaled by the bar's height only, so they never smear sideways.
        val capW = (l.width * (h / l.height)).coerceAtMost(w / 2f)
        surface.image(Surface.Sheet.UI, l.x, l.y, l.width, l.height, x, y, x + capW, y + h)
        surface.image(Surface.Sheet.UI, r.x, r.y, r.width, r.height, x + w - capW, y, x + w, y + h)
        if (w - capW * 2f > 0f) {
            surface.image(Surface.Sheet.UI, m.x, m.y, m.width, m.height, x + capW, y, x + w - capW, y + h)
        }
    }

    /** The frame a button is drawn in, by state. */
    fun button(x: Float, y: Float, w: Float, h: Float, enabled: Boolean, corner: Float = 14f) =
        panel(if (enabled) Art.BUTTON else Art.BUTTON_OFF, x, y, w, h, corner)

    /** The blob a fighter stands on, so nobody floats. */
    fun shadow(centreX: Float, floorY: Float, width: Float) {
        fill(centreX - width / 2f, floorY - 2f, width, 2f, Palette.SHADOW)
        fill(centreX - width / 2f + 2f, floorY, width - 4f, 2f, Palette.SHADOW)
    }
}

/**
 * How a party stands in a room.
 *
 * Sized as a block rather than per hero — three heroes at the scale one could
 * afford is a smear — and overlapping by a quarter, which is enough to read as a
 * formation and little enough to keep each class recognisable. The front hero,
 * the one taking the hits, stands nearest the monster and is drawn last.
 */
object Formation {

    private const val OVERLAP = 0.25f

    fun step(slot: Float): Float = slot * (1f - OVERLAP)

    fun width(members: Int, slot: Float): Float = slot + step(slot) * (members - 1)

    /** Centre of member [index], counting the front hero as 0. */
    fun centreOf(index: Int, members: Int, slot: Float, left: Float): Float =
        left + step(slot) * (members - 1 - index) + slot / 2f

    /** Rearmost first, so the front hero overlaps the rest. */
    fun drawOrder(members: Int): IntProgression = (members - 1) downTo 0

    /**
     * Where the duel stands in a room running from [left] to [right].
     *
     * Centred as one group with a fixed gap: pinning the party to one wall and the
     * monster to the other reads as a fight on a narrow bar and as two separate
     * scenes on a wide one, because the gap would grow with the widget.
     */
    fun scene(members: Int, slot: Float, enemyWidth: Float, left: Float, right: Float): Scene {
        val party = width(members, slot)
        val gap = slot * 1.4f
        val span = party + gap + enemyWidth
        val start = maxOf(left, (left + right) / 2f - span / 2f)
        val enemyCentre = minOf(start + party + gap, right - enemyWidth) + enemyWidth / 2f
        return Scene(partyLeft = start, enemyCentre = enemyCentre)
    }

    data class Scene(val partyLeft: Float, val enemyCentre: Float)
}
