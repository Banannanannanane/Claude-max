package dev.dotmatrix.taskbarhero.paint

import dev.dotmatrix.taskbarhero.engine.DotFont
import dev.dotmatrix.taskbarhero.engine.Sprites

/**
 * Draws in dot units instead of pixels: one unit is one cell of the matrix, so
 * the same layout lands crisply on a 4x1 bar, on a stretched 5x2 one, and on a
 * full screen.
 *
 * Text and meters are circles (the Ndot look); sprites are squares, which keeps
 * the fighters legible at 12 cells tall.
 */
class DotPainter(private val surface: Surface, val unit: Float) {

    /** A touch under half a cell, so neighbouring dots stay visually separate. */
    private val dotRadius: Float get() = unit * 0.38f

    fun dot(xDot: Float, yDot: Float, color: Int, scale: Float = 1f) {
        surface.circle((xDot + 0.5f * scale) * unit, (yDot + 0.5f * scale) * unit, dotRadius * scale, color)
    }

    /** Draws [text] with its top-left at ([xDot], [yDot]). Returns the width used, in dots. */
    fun text(xDot: Float, yDot: Float, text: String, color: Int, scale: Float = 1f, spacing: Int = 1): Float {
        val r = dotRadius * scale
        for (d in DotFont.dots(text, spacing)) {
            surface.circle(
                (xDot + (d.x + 0.5f) * scale) * unit,
                (yDot + (d.y + 0.5f) * scale) * unit,
                r,
                color,
            )
        }
        return DotFont.measure(text, spacing) * scale
    }

    fun textCentered(centerXDot: Float, yDot: Float, text: String, color: Int, scale: Float = 1f): Float {
        val w = DotFont.measure(text) * scale
        return text(centerXDot - w / 2f, yDot, text, color, scale)
    }

    fun sprite(xDot: Float, yDot: Float, art: Sprites.Art, color: Int, accent: Int, scale: Float = 1f) {
        val side = unit * scale * 0.92f
        for (y in 0 until art.size) {
            for (x in 0 until art.size) {
                val cell = art.cell(x, y)
                if (cell == '.') continue
                val left = (xDot + x * scale) * unit
                val top = (yDot + y * scale) * unit
                surface.rect(left, top, left + side, top + side, if (cell == '+') accent else color)
            }
        }
    }

    /**
     * Segmented meter, [cells] dots wide with the first [fraction] of them lit.
     * Always keeps one dot lit while the fraction is above zero, and one dark
     * below one: a nearly dead fighter should not read as a full bar.
     */
    fun meter(xDot: Float, yDot: Float, cells: Int, fraction: Double, lit: Int, unlit: Int = Palette.DIM) {
        val f = fraction.coerceIn(0.0, 1.0)
        var litCells = Math.round(f * cells).toInt()
        if (f > 0.0 && litCells == 0) litCells = 1
        if (f < 1.0 && litCells == cells) litCells = cells - 1
        for (i in 0 until cells) {
            dot(xDot + i * 2f, yDot, if (i < litCells) lit else unlit, scale = 0.9f)
        }
    }

    /** Width in dots taken by a [meter] of [cells] cells. */
    fun meterWidth(cells: Int): Float = (cells - 1) * 2f + 0.9f

    fun frame(xDot: Float, yDot: Float, wDot: Float, hDot: Float, color: Int, radiusDot: Float, widthDot: Float = 0.35f) {
        surface.roundRectStroke(
            xDot * unit,
            yDot * unit,
            (xDot + wDot) * unit,
            (yDot + hDot) * unit,
            radiusDot * unit,
            widthDot * unit,
            color,
        )
    }

    fun panel(xDot: Float, yDot: Float, wDot: Float, hDot: Float, color: Int, radiusDot: Float) {
        surface.roundRectFill(
            xDot * unit,
            yDot * unit,
            (xDot + wDot) * unit,
            (yDot + hDot) * unit,
            radiusDot * unit,
            color,
        )
    }

    /** Faint background matrix — the texture that sells the display metaphor. */
    fun grid(cols: Int, rows: Int, step: Int = 3) {
        var y = 1
        while (y < rows) {
            var x = 1
            while (x < cols) {
                surface.circle((x + 0.5f) * unit, (y + 0.5f) * unit, unit * 0.16f, Palette.GRID)
                x += step
            }
            y += step
        }
    }

    fun dottedRule(xDot: Float, yDot: Float, wDot: Int, color: Int = Palette.DIM) {
        var x = 0
        while (x < wDot) {
            dot(xDot + x, yDot, color, scale = 0.7f)
            x += 2
        }
    }
}
