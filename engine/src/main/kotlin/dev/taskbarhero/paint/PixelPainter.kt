package dev.taskbarhero.paint

import dev.taskbarhero.engine.PixelFont
import dev.taskbarhero.engine.Sprites

/**
 * Draws in pixel cells rather than device pixels: one cell is one art pixel, so
 * the same layout lands crisply on a 4x1 bar, on a stretched 5x2 one, and on a
 * full screen — a denser phone just gets a bigger cell.
 *
 * Everything is a filled square. That is the whole trick behind the look: hard
 * edges, no anti-aliased curves, no gradients.
 */
class PixelPainter(private val surface: Surface, val unit: Float) {

    /** Fills a rectangle given in cells. */
    fun fill(x: Float, y: Float, w: Float, h: Float, color: Int) {
        if (w <= 0f || h <= 0f) return
        surface.rect(x * unit, y * unit, (x + w) * unit, (y + h) * unit, color)
    }

    fun pixel(x: Float, y: Float, color: Int, size: Float = 1f) = fill(x, y, size, size, color)

    /**
     * Draws [text] with its top-left at ([x], [y]) over a one-cell shadow.
     * Returns the width used, in cells.
     */
    fun text(x: Float, y: Float, text: String, color: Int, shadow: Int = Palette.OUTLINE, scale: Float = 1f): Float {
        val cells = PixelFont.cells(text)
        if (shadow != 0) {
            for (c in cells) fill(x + (c.x + 1) * scale, y + (c.y + 1) * scale, scale, scale, shadow)
        }
        for (c in cells) fill(x + c.x * scale, y + c.y * scale, scale, scale, color)
        return PixelFont.measure(text) * scale
    }

    fun textCentered(centerX: Float, y: Float, label: String, color: Int, shadow: Int = Palette.OUTLINE): Float {
        val w = PixelFont.measure(label).toFloat()
        return text(centerX - w / 2f, y, label, color, shadow)
    }

    /** Blits palette-indexed art. Colours travel with the sprite, not the call site. */
    fun sprite(x: Float, y: Float, art: Sprites.Art, scale: Float = 1f) {
        for (row in 0 until art.size) {
            for (col in 0 until art.size) {
                val color = art.colorAt(col, row) ?: continue
                fill(x + col * scale, y + row * scale, scale, scale, color)
            }
        }
    }

    /**
     * The blob a fighter stands on, so sprites do not float above the floor. Flat
     * and wide rather than round: a circle at this size just looks like a ball.
     */
    fun shadow(centerX: Float, y: Float, width: Float) {
        fill(centerX - width / 2f, y, width, 1f, Palette.SHADOW)
        fill(centerX - width / 2f + 1f, y + 1f, width - 2f, 1f, Palette.SHADOW)
    }

    /**
     * A classic RPG meter: outline, dark socket, flat fill and a lighter run
     * along the top. Keeps one pixel of fill while [fraction] is above zero — a
     * nearly dead fighter must not read as a dead one — and one pixel of socket
     * below full.
     */
    fun bar(x: Float, y: Float, w: Float, h: Float, fraction: Double, color: Int, socket: Int) {
        fill(x - 1f, y - 1f, w + 2f, h + 2f, Palette.OUTLINE)
        fill(x, y, w, h, socket)
        val f = fraction.coerceIn(0.0, 1.0)
        var filled = (w * f).toFloat()
        if (f > 0.0 && filled < 1f) filled = 1f
        if (f < 1.0 && filled > w - 1f) filled = w - 1f
        if (filled <= 0f) return
        fill(x, y, filled, h, color)
        if (h >= 3f) fill(x, y, filled, 1f, tint(color, 0.34f))
    }

    /** Chunky bevelled panel: lit top-left, shaded bottom-right, square corners. */
    fun panel(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        face: Int,
        light: Int = Palette.BEVEL_LIGHT,
        dark: Int = Palette.BEVEL_DARK,
    ) {
        fill(x, y, w, h, face)
        fill(x, y, w, 1f, light)
        fill(x, y, 1f, h, light)
        fill(x, y + h - 1f, w, 1f, dark)
        fill(x + w - 1f, y, 1f, h, dark)
    }

    /** The same panel pressed in — bevel reversed — for sockets and unlit buttons. */
    fun inset(x: Float, y: Float, w: Float, h: Float, face: Int) =
        panel(x, y, w, h, face, light = Palette.BEVEL_DARK, dark = Palette.BEVEL_LIGHT)

    /** One-pixel frame, no fill. */
    fun frame(x: Float, y: Float, w: Float, h: Float, color: Int) {
        fill(x, y, w, 1f, color)
        fill(x, y + h - 1f, w, 1f, color)
        fill(x, y, 1f, h, color)
        fill(x + w - 1f, y, 1f, h, color)
    }

    /**
     * Dungeon masonry: staggered courses of stone with mortar between them. The
     * backdrop is what makes the bar read as a room rather than a status line.
     */
    fun bricks(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        biome: Biome = Biome.DUNGEON,
        courseHeight: Int = 6,
        brickWidth: Int = 11,
    ) {
        fill(x, y, w, h, biome.stoneDark)
        var course = 0
        var top = y
        while (top < y + h) {
            val bottom = minOf(top + courseHeight - 1f, y + h)
            fill(x, top, w, bottom - top, biome.stone)
            fill(x, top, w, 1f, biome.stoneLit)
            // Mortar joints, offset every other course.
            var joint = x + if (course % 2 == 0) brickWidth.toFloat() else brickWidth / 2f
            while (joint < x + w) {
                fill(joint, top, 1f, bottom - top, biome.stoneDark)
                joint += brickWidth
            }
            top += courseHeight
            course++
        }
    }

    /** Flagstone the fighters stand on. */
    fun floor(x: Float, y: Float, w: Float, h: Float, biome: Biome = Biome.DUNGEON) {
        fill(x, y, w, h, biome.floor)
        fill(x, y, w, 1f, biome.stoneLit)
    }

    /**
     * One inventory cell: a sunken socket, the grade's colour as its border, and a
     * gem in the same colour. Border-is-rarity is the ARPG convention TBH leans on,
     * so a glance at the stash reads as "what have I got, and how good".
     */
    fun itemCell(x: Float, y: Float, size: Float, color: Int, filled: Boolean) {
        fill(x, y, size, size, if (filled) Palette.STONE_DARK else Palette.BEVEL_DARK)
        frame(x, y, size, size, if (filled) color else Palette.BEVEL_LIGHT)
        if (!filled) return

        // A cut gem: waist wide, tapered top and bottom, one specular pixel.
        val center = x + size / 2f
        val top = y + size * 0.22f
        val h = size * 0.56f
        fill(center - size * 0.22f, top + h * 0.25f, size * 0.44f, h * 0.5f, color)
        fill(center - size * 0.11f, top, size * 0.22f, h * 0.3f, color)
        fill(center - size * 0.11f, top + h * 0.7f, size * 0.22f, h * 0.3f, color)
        pixel(center - size * 0.16f, top + h * 0.3f, Palette.PARCHMENT)
    }

    /**
     * Wall torch with a two-frame flame, driven by the wall clock so every redraw
     * agrees on which frame is showing.
     */
    fun torch(x: Float, y: Float, nowMs: Long) {
        surface.circle((x + 2f) * unit, (y + 1f) * unit, 4f * unit, translucent(Palette.TORCH, 0.10f))
        fill(x + 1f, y + 3f, 2f, 4f, Palette.OUTLINE)
        fill(x, y + 2f, 4f, 1f, Palette.GOLD_DARK)
        val flame = if ((nowMs / 400L) % 2L == 0L) 3f else 2f
        fill(x + 1f, y + 2f - flame, 2f, flame, Palette.TORCH)
        pixel(x + 1f, y + 1f - flame, Palette.TORCH_CORE)
    }

    /** Dotted separator, one pixel tall. */
    fun divider(x: Float, y: Float, w: Float, color: Int = Palette.BEVEL_LIGHT) {
        var i = 0f
        while (i < w) {
            fill(x + i, y, 1f, 1f, color)
            i += 2f
        }
    }

    /** Brightens a colour towards white, for bar highlights. */
    private fun tint(color: Int, amount: Float): Int {
        val a = (color ushr 24) and 0xFF
        val r = lift((color ushr 16) and 0xFF, amount)
        val g = lift((color ushr 8) and 0xFF, amount)
        val b = lift(color and 0xFF, amount)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lift(channel: Int, amount: Float): Int =
        (channel + (255 - channel) * amount).toInt().coerceIn(0, 255)

    private fun translucent(color: Int, alpha: Float): Int =
        ((alpha * 255f).toInt().coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)
}
