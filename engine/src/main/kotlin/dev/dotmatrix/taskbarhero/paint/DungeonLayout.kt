package dev.dotmatrix.taskbarhero.paint

import dev.dotmatrix.taskbarhero.engine.Balance
import dev.dotmatrix.taskbarhero.engine.DotFont
import dev.dotmatrix.taskbarhero.engine.Fmt
import dev.dotmatrix.taskbarhero.engine.GameState
import dev.dotmatrix.taskbarhero.engine.Hud
import dev.dotmatrix.taskbarhero.engine.Sprites

/**
 * The same matrix as the bar, given a full screen: bigger fighters, the stats the
 * bar has no room for, and a live event log.
 *
 * Composed from the bottom up — log, then stats, then whatever is left goes to
 * the arena — so a short viewport (landscape, a cover display) drops sections
 * instead of drawing past its own edge.
 */
object DungeonLayout {

    /**
     * Column budget for a portrait phone: about 21 characters per line, which is
     * as small as a 5x7 face gets before the dots stop reading as dots.
     */
    const val TARGET_COLS = 130

    /** One text line plus its leading. */
    private const val LINE = 9
    private const val TEXT = DotFont.HEIGHT

    private const val MAX_SPRITE = 44
    private const val MIN_SPRITE = 11
    private const val MAX_LOG_LINES = 6
    private const val GAP = 4

    /** Keeps dots off the very edge of the display. */
    private const val MARGIN = 2f

    fun draw(
        p: DotPainter,
        cols: Int,
        rows: Int,
        state: GameState,
        log: List<String>,
        nowMs: Long,
        b: Balance = Balance(),
    ) {
        if (cols < 60 || rows < TEXT * 2) return
        val hud = Hud.of(state, b)
        val bottom = rows - 1
        p.grid(cols, rows, step = 4)

        p.text(MARGIN, 1f, "TASKBAR HERO", Palette.INK)
        val mode = if (state.autoLevel) "AUTO" else "MANUAL"
        p.text(cols - MARGIN - DotFont.measure(mode), 1f, mode, Palette.RED)
        val headerBottom = 1 + TEXT + 2
        p.dottedRule(MARGIN, headerBottom.toFloat(), (cols - MARGIN * 2).toInt())

        // Reserve the bottom blocks first; the arena lives on what remains.
        val logHeight = if (log.isEmpty()) 0 else GAP + minOf(log.size, MAX_LOG_LINES) * LINE
        val statsHeight = GAP + STAT_ROWS * LINE
        val arenaTop = headerBottom + 3

        var arenaBottom = bottom - logHeight - statsHeight - GAP
        var showLog = logHeight > 0
        var showStats = true
        if (arenaBottom - arenaTop < MIN_ARENA) {
            arenaBottom += logHeight
            showLog = false
        }
        if (arenaBottom - arenaTop < MIN_ARENA) {
            arenaBottom += statsHeight
            showStats = false
        }
        arenaBottom = arenaBottom.coerceAtMost(bottom)

        drawArena(p, hud, cols, nowMs, top = arenaTop, bottom = arenaBottom)

        var y = arenaBottom + GAP
        if (showStats) {
            drawStats(p, hud, state, cols, b, top = y, bottom = bottom)
            y += statsHeight
        }
        if (showLog) drawLog(p, log, cols, top = y, bottom = bottom)
    }

    /** Caption on top, the two fighters centred, health under each. */
    private fun drawArena(p: DotPainter, hud: Hud, cols: Int, nowMs: Long, top: Int, bottom: Int) {
        val bandTop = top + TEXT + 3
        val available = bottom - bandTop
        if (available < MIN_SPRITE + 3) {
            p.textCentered(cols / 2f, top.toFloat(), DotFont.clipWords(captionOf(hud), cols - 4), captionColor(hud))
            return
        }

        // Fighters and their meters move as one block, centred in the free band —
        // otherwise the sprites float and the meters drift to the far edge.
        val side = minOf(available - 3, MAX_SPRITE, (cols - 8) / 2).toFloat()
        val blockTop = bandTop + (available - (side + 3)) / 2f
        val heroArt = Sprites.heroFrame(nowMs, if (hud.isDown) 1L else 0L)
        val enemyArt = Sprites.of(hud.enemySprite)

        // The caption belongs to the fight, so it travels with the block.
        p.textCentered(
            cols / 2f,
            (blockTop - TEXT - 3f).coerceAtLeast(top.toFloat()),
            DotFont.clipWords(captionOf(hud), cols - 4),
            captionColor(hud),
        )
        p.sprite(MARGIN, blockTop, heroArt, Palette.INK, Palette.RED, side / heroArt.size)
        if (!hud.isDown) {
            val scale = side / enemyArt.size
            p.sprite(cols - MARGIN - enemyArt.size * scale, blockTop, enemyArt, Palette.MID, Palette.RED, scale)
        }
        p.textCentered(cols / 2f, blockTop + side / 2f - TEXT / 2f, "VS", Palette.DIM)

        val meterRow = blockTop + side + 2f
        val cells = 16
        p.meter(MARGIN, meterRow, cells, hud.heroHp, Palette.RED)
        if (!hud.isDown) {
            p.meter(cols - MARGIN - p.meterWidth(cells), meterRow, cells, hud.enemyHp, Palette.INK)
        }
    }

    /** Four label/value pairs in two columns, then the price of the next level. */
    private fun drawStats(
        p: DotPainter,
        hud: Hud,
        state: GameState,
        cols: Int,
        b: Balance,
        top: Int,
        bottom: Int,
    ) {
        p.dottedRule(MARGIN, top.toFloat(), (cols - MARGIN * 2).toInt())

        val pairs = listOf(
            Stat("ACT", hud.stage, "BEST", Fmt.stage(state.deepestAct, state.deepestWave)),
            Stat("LEVEL", state.level.toString(), "KILLS", Fmt.short(state.kills.toDouble())),
            Stat("GOLD", hud.gold, "BOSSES", state.bossKills.toString()),
            Stat("RUNES", state.runes.toString(), "WIPES", state.deaths.toString()),
        )

        // Label and value ride together instead of sitting in aligned columns:
        // at 21 characters a line, aligned columns leave no room for the values.
        val rightX = cols * 0.5f
        val leftBudget = (rightX - MARGIN - 4f).toInt()
        val rightBudget = (cols - MARGIN - rightX).toInt()

        var y = top + GAP
        for (stat in pairs) {
            if (y + TEXT > bottom) return
            drawPair(p, MARGIN, y.toFloat(), stat.label, stat.value, leftBudget)
            drawPair(p, rightX, y.toFloat(), stat.rightLabel, stat.rightValue, rightBudget)
            y += LINE
        }

        if (y + TEXT <= bottom) {
            val label = "RUNE +${state.runes * 2}%"
            p.text(MARGIN, y.toFloat(), label, Palette.MID)
            p.meter(MARGIN + DotFont.measure(label) + 4f, y + 2f, cells = 10, fraction = hud.runeProgress, lit = Palette.MID)
            y += LINE
        }
        if (y + TEXT <= bottom) {
            val next = "NEXT LV ${Fmt.short(b.levelCost(state.level))} G"
            p.text(MARGIN, y.toFloat(), DotFont.clipWords(next, (cols - MARGIN * 2).toInt()), if (hud.buttonEnabled) Palette.RED else Palette.DIM)
        }
    }

    private fun captionOf(hud: Hud): String = if (hud.isDown) "HERO DOWN" else hud.enemyName

    private fun captionColor(hud: Hud): Int = if (hud.isDown) Palette.RED else Palette.MID

    /** "GOLD 4.5K": dim label, ink value, clipped as a unit. */
    private fun drawPair(p: DotPainter, x: Float, y: Float, label: String, value: String, budget: Int) {
        p.text(x, y, label, Palette.DIM)
        val valueX = x + DotFont.measure(label) + 4f
        val room = budget - DotFont.measure(label) - 4
        if (room > 0) p.text(valueX, y, DotFont.clip(value, room), Palette.INK)
    }

    private fun drawLog(p: DotPainter, log: List<String>, cols: Int, top: Int, bottom: Int) {
        if (log.isEmpty()) return
        p.dottedRule(MARGIN, top.toFloat(), (cols - MARGIN * 2).toInt())
        var y = top + GAP
        val room = ((bottom - y + 1) / LINE).coerceIn(0, MAX_LOG_LINES)
        for (line in log.takeLast(room)) {
            if (y + TEXT > bottom) return
            p.text(MARGIN, y.toFloat(), DotFont.clipWords(line, (cols - MARGIN * 2).toInt()), Palette.MID)
            y += LINE
        }
    }

    /** Four stat pairs, the rune meter, and the "next level" price. */
    private const val STAT_ROWS = 6

    /** Below this the arena cannot hold a caption, two fighters and their meters. */
    private const val MIN_ARENA = TEXT + 3 + MIN_SPRITE + 3

    private data class Stat(val label: String, val value: String, val rightLabel: String, val rightValue: String)
}
