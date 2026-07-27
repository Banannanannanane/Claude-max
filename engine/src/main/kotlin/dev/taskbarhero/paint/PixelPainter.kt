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
class PixelPainter(
    private val surface: Surface,
    val unit: Float,
    /**
     * Font pixels per cell. The grid is fine enough for 32x32 sprites, which makes
     * a 1x face too small to read, so text is drawn at 2x — exactly how a pixel
     * game renders at a low internal resolution and upscales by an integer.
     */
    val textScale: Float = 2f,
    /** Drop-in art, when the player has provided some. Null keeps the built-ins. */
    private val sheet: Sheet? = null,
) {

    /** Height of a line of text, in cells. */
    val textHeight: Float get() = PixelFont.HEIGHT * textScale

    /** Width [text] will occupy, in cells. */
    fun measure(text: String): Float = PixelFont.measure(text) * textScale

    /** Trims [text] to [budgetCells] of drawn width. */
    fun clip(text: String, budgetCells: Float): String =
        PixelFont.clip(text, (budgetCells / textScale).toInt())

    fun clipWords(text: String, budgetCells: Float): String =
        PixelFont.clipWords(text, (budgetCells / textScale).toInt())

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
    fun text(x: Float, y: Float, text: String, color: Int, shadow: Int = Palette.OUTLINE, scale: Float = textScale): Float {
        val cells = PixelFont.cells(text)
        if (shadow != 0) {
            for (c in cells) fill(x + (c.x + 1) * scale, y + (c.y + 1) * scale, scale, scale, shadow)
        }
        for (c in cells) fill(x + c.x * scale, y + c.y * scale, scale, scale, color)
        return PixelFont.measure(text) * scale
    }

    fun textCentered(centerX: Float, y: Float, label: String, color: Int, shadow: Int = Palette.OUTLINE): Float =
        text(centerX - measure(label) / 2f, y, label, color, shadow)

    /**
     * Draws a fighter: the dropped-in sheet frame when there is one for [key],
     * otherwise the built-in art. The sprite is centred in a [side]-cell box and
     * bottom-aligned, so frames of any aspect stand on the floor.
     *
     * Sheet frames are scaled by a **whole number** of cells per source pixel —
     * never 1.14x, which would double every seventh row and shred the artwork —
     * and by the *same* number across the cast, so the pack's own proportions
     * survive: the ogre towers over the knight, and a fallen hero's skull stays a
     * trinket on the floor instead of swelling to fill its box.
     */
    fun fighter(x: Float, y: Float, side: Float, key: String, art: Sprites.Art) {
        val frame = sheet?.frame(key)
        if (frame == null) {
            sprite(x, y, art, side / art.size)
            return
        }
        val scale = fitScale(side)
        val w = frame.width * scale
        val h = frame.height * scale
        val left = x + (side - w) / 2f
        val top = y + (side - h)
        val drawn = surface.image(
            frame.x, frame.y, frame.width, frame.height,
            left * unit, top * unit, (left + w) * unit, (top + h) * unit,
        )
        if (!drawn) sprite(x, y, art, side / art.size)
    }

    /**
     * The grid the fighters are drawn on: the sheet's own pixel size when there is
     * one, otherwise the built-in art's. Layouts size their fighter boxes in
     * multiples of this, so a pack of 30-pixel frames can go up a whole step in a
     * room that would not have fitted two rows of 32.
     */
    val spriteUnit: Int get() = sheet?.unit ?: Sprites.FIGHTER_SIZE

    /**
     * How wide [key] actually comes out in a [side]-cell box. Sheet frames are
     * rarely square, and a 16-wide knight spaced as if it were 32 wide leaves the
     * party standing in a scattered line instead of a block — so layouts space
     * fighters by this, not by the box.
     */
    fun fighterWidth(side: Float, key: String): Float {
        val frame = sheet?.frame(key) ?: return side
        return frame.width * fitScale(side)
    }

    /** Whole cells per sheet pixel: whole, and the same for every frame. */
    private fun fitScale(side: Float): Float {
        val unit = sheet?.unit ?: return 1f
        return kotlin.math.floor(side / unit).coerceAtLeast(1f)
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
        fill(centerX - width / 2f, y, width, 2f, Palette.SHADOW)
        fill(centerX - width / 2f + 2f, y + 2f, width - 4f, 2f, Palette.SHADOW)
    }

    /**
     * A classic RPG meter: outline, dark socket, flat fill and a lighter run
     * along the top. Keeps one pixel of fill while [fraction] is above zero — a
     * nearly dead fighter must not read as a dead one — and one pixel of socket
     * below full.
     */
    fun bar(x: Float, y: Float, w: Float, h: Float, fraction: Double, color: Int, socket: Int) {
        fill(x - 2f, y - 2f, w + 4f, h + 4f, Palette.OUTLINE)
        fill(x, y, w, h, socket)
        val f = fraction.coerceIn(0.0, 1.0)
        var filled = (w * f).toFloat()
        if (f > 0.0 && filled < 2f) filled = 2f
        if (f < 1.0 && filled > w - 2f) filled = w - 2f
        if (filled <= 0f) return
        fill(x, y, filled, h, color)
        if (h >= 5f) fill(x, y, filled, 2f, tint(color, 0.34f))
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
        weight: Float = 2f,
    ) {
        fill(x, y, w, h, face)
        fill(x, y, w, weight, light)
        fill(x, y, weight, h, light)
        fill(x, y + h - weight, w, weight, dark)
        fill(x + w - weight, y, weight, h, dark)
    }

    /** The same panel pressed in — bevel reversed — for sockets and unlit buttons. */
    fun inset(x: Float, y: Float, w: Float, h: Float, face: Int, weight: Float = 2f) =
        panel(x, y, w, h, face, light = Palette.BEVEL_DARK, dark = Palette.BEVEL_LIGHT, weight = weight)

    /** One-pixel frame, no fill. */
    fun frame(x: Float, y: Float, w: Float, h: Float, color: Int, weight: Float = 2f) {
        fill(x, y, w, weight, color)
        fill(x, y + h - weight, w, weight, color)
        fill(x, y, weight, h, color)
        fill(x + w - weight, y, weight, h, color)
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
        courseHeight: Int = 12,
        brickWidth: Int = 22,
    ) {
        fill(x, y, w, h, biome.stoneDark)
        var course = 0
        var top = y
        while (top < y + h) {
            val bottom = minOf(top + courseHeight - 2f, y + h)
            fill(x, top, w, bottom - top, biome.stone)
            fill(x, top, w, 2f, biome.stoneLit)
            // Mortar joints, offset every other course.
            var joint = x + if (course % 2 == 0) brickWidth.toFloat() else brickWidth / 2f
            while (joint < x + w) {
                fill(joint, top, 2f, bottom - top, biome.stoneDark)
                joint += brickWidth
            }
            top += courseHeight
            course++
        }
    }

    /** Flagstone the fighters stand on. */
    fun floor(x: Float, y: Float, w: Float, h: Float, biome: Biome = Biome.DUNGEON) {
        fill(x, y, w, h, biome.floor)
        fill(x, y, w, 2f, biome.stoneLit)
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
    fun torch(x: Float, y: Float, nowMs: Long, scale: Float = 2f) {
        surface.circle((x + 2f * scale) * unit, (y + 1f * scale) * unit, 5f * scale * unit, translucent(Palette.TORCH, 0.10f))
        fill(x + scale, y + 3f * scale, 2f * scale, 4f * scale, Palette.OUTLINE)
        fill(x, y + 2f * scale, 4f * scale, scale, Palette.GOLD_DARK)
        val flame = (if ((nowMs / 400L) % 2L == 0L) 3f else 2f) * scale
        fill(x + scale, y + 2f * scale - flame, 2f * scale, flame, Palette.TORCH)
        fill(x + scale, y + scale - flame, scale, scale, Palette.TORCH_CORE)
    }

    /** Dotted separator, one pixel tall. */
    fun divider(x: Float, y: Float, w: Float, color: Int = Palette.BEVEL_LIGHT, size: Float = 2f) {
        var i = 0f
        while (i < w) {
            fill(x + i, y, size, size, color)
            i += size * 2f
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
