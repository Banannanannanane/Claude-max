package dev.dotmatrix.taskbarhero.paint

import dev.dotmatrix.taskbarhero.engine.Balance
import dev.dotmatrix.taskbarhero.engine.DotFont
import dev.dotmatrix.taskbarhero.engine.Fmt
import dev.dotmatrix.taskbarhero.engine.GameState
import dev.dotmatrix.taskbarhero.engine.Hud
import dev.dotmatrix.taskbarhero.engine.Sprites

/**
 * The taskbar layout: status column, arena, action button — and a stats strip
 * once the widget is resized past one row.
 *
 * Sizes are expressed in dot cells, and the horizontal split matches the two
 * touch zones declared in the widget's RemoteViews layout.
 */
object BarLayout {

    /** Width of the LV UP button as a fraction of the bar. Mirrors the layout weights (300 / 1000). */
    const val ACTION_ZONE_FRACTION = 0.30f

    /** Cells across a one-row bar. Everything else is derived from this pitch. */
    const val ROWS_COMPACT = 32

    /** Nominal height of one launcher row, in dp — a 4x1 Nothing OS cell. */
    const val ROW_HEIGHT_DP = 70f

    /** From here up, the bar earns a stats strip under the arena. */
    const val ROWS_TALL = 44

    /** How long an event holds the arena caption instead of the monster name. */
    const val EVENT_FLASH_MS = 3_500L

    /**
     * Pixels per dot cell. Capped at the one-row pitch so a taller widget gains
     * *rows* rather than bigger dots — stretching the matrix would cost the bar
     * the horizontal cells its text needs.
     */
    fun unitPx(heightPx: Float, density: Float): Float =
        (minOf(heightPx, ROW_HEIGHT_DP * density) / ROWS_COMPACT).coerceAtLeast(1.5f)

    fun rowsFor(heightPx: Float, density: Float): Int =
        (heightPx / unitPx(heightPx, density)).toInt().coerceAtLeast(ROWS_COMPACT)

    fun draw(
        p: DotPainter,
        cols: Int,
        rows: Int,
        state: GameState,
        nowMs: Long,
        recentEvent: String? = null,
        b: Balance = Balance(),
    ) {
        val hud = Hud.of(state, b, autoNote = false)
        val tall = rows >= ROWS_TALL
        // On a tall bar the arena keeps the top block and the strip takes the
        // bottom; on a 4x1 the arena owns the whole height.
        val stripTop = if (tall) rows - STRIP_HEIGHT else rows
        val arenaBottom = stripTop - 3

        p.panel(0f, 0f, cols.toFloat(), rows.toFloat(), Palette.BG, radiusDot = 7f)
        p.grid(cols, rows)
        p.frame(0.3f, 0.3f, cols - 0.6f, rows - 0.6f, Palette.DIM, radiusDot = 7f, widthDot = 0.25f)

        val actionLeft = cols * (1f - ACTION_ZONE_FRACTION)
        drawStatusColumn(p, hud)
        drawArena(
            p, hud,
            from = statusWidth(hud),
            to = actionLeft,
            bottom = arenaBottom,
            nowMs = nowMs,
            recentEvent = recentEvent,
        )
        drawActionButton(p, hud, from = actionLeft, to = cols.toFloat(), bottom = arenaBottom)
        if (tall) drawStatsStrip(p, hud, state, cols, stripTop, recentEvent)
    }

    /** Rows the tall strip occupies: a rule and two text lines. */
    private const val STRIP_HEIGHT = 24

    private const val COLUMN_X = 3f
    private val ROW_Y = floatArrayOf(2f, 12f, 22f)

    /**
     * Where the arena may start: measured from the real strings, because "LV 240"
     * and "1.4T" are a third wider than "LV 7" and "640".
     */
    private fun statusWidth(hud: Hud): Float {
        val stage = 3f + 3f + DotFont.measure(hud.stage)
        val level = DotFont.measure(hud.level).toFloat()
        val gold = DotFont.measure(hud.gold).toFloat()
        return COLUMN_X + maxOf(stage, level, gold) + 5f
    }

    /**
     * Left column: where we are, how strong we are, what we can spend. Runes are
     * left to the tall strip — three lines is all a 4x1 bar can hold.
     */
    private fun drawStatusColumn(p: DotPainter, hud: Hud) {
        val x = COLUMN_X
        // Live marker: red while fighting, dim while the hero is down.
        p.dot(x, ROW_Y[0] + 1f, if (hud.isDown) Palette.DIM else Palette.RED, scale = 1.2f)
        p.text(x + 6f, ROW_Y[0], hud.stage, Palette.INK)
        p.text(x, ROW_Y[1], hud.level, Palette.INK)
        p.text(x, ROW_Y[2], hud.gold, Palette.RED)
    }

    /** Middle: hero versus monster, health under each, caption above. */
    private fun drawArena(
        p: DotPainter,
        hud: Hud,
        from: Float,
        to: Float,
        bottom: Int,
        nowMs: Long,
        recentEvent: String?,
    ) {
        val width = to - from
        if (width < 24f) return

        // While the hero is down there is no monster to name, and the grave sprite
        // needs a word next to it, not a countdown.
        val caption = recentEvent ?: if (hud.isDown) "DOWN" else hud.enemyShort
        val captionColor = if (recentEvent != null || hud.isDown) Palette.RED else Palette.MID
        // Centred over the arena: it labels the fight, not either fighter.
        p.textCentered((from + to) / 2f, 1f, DotFont.clipWords(caption, (width - 1f).toInt()), captionColor)

        val spriteTop = 10f
        val meterRow = bottom - 2f
        // The fighters take whatever is left between the caption and the meters,
        // but never so much that they meet in the middle of the arena.
        val side = minOf(meterRow - spriteTop - 2f, (width - 6f) / 2f, 30f).coerceAtLeast(11f)
        val heroArt = Sprites.heroFrame(nowMs, if (hud.isDown) 1L else 0L)
        val enemyArt = Sprites.of(hud.enemySprite)

        p.sprite(from, spriteTop, heroArt, Palette.INK, Palette.RED, side / heroArt.size)
        if (!hud.isDown) {
            // The monster faces the hero from the far side of the arena.
            val scale = side / enemyArt.size
            p.sprite(to - enemyArt.size * scale, spriteTop, enemyArt, Palette.MID, Palette.RED, scale)
        }

        val cells = ((width / 2f) / 2.4f).toInt().coerceIn(4, 14)
        p.meter(from, meterRow, cells, hud.heroHp, Palette.RED)
        if (!hud.isDown) {
            p.meter(to - p.meterWidth(cells), meterRow, cells, hud.enemyHp, Palette.INK)
        }
    }

    /** Right: the single tappable action. Red frame when the next level is affordable. */
    private fun drawActionButton(p: DotPainter, hud: Hud, from: Float, to: Float, bottom: Int) {
        val pad = 2f
        val x = from + pad
        val w = (to - from) - pad * 2f
        if (w < 12f) return

        val top = 3f
        val height = (bottom - 1f - top).coerceAtMost(34f)
        val hot = hud.buttonEnabled
        p.frame(x, top, w, height, if (hot) Palette.RED else Palette.DIM, radiusDot = 5f, widthDot = 0.4f)

        val center = x + w / 2f
        val budget = (w - 2f).toInt()
        // Label and price share the frame, centred as a pair.
        val labelY = top + (height - 18f) / 2f
        p.textCentered(center, labelY, fittingLabel(hud, budget), if (hot) Palette.INK else Palette.MID)
        p.textCentered(center, labelY + 11f, DotFont.clip(hud.buttonCost, budget), if (hot) Palette.RED else Palette.DIM)
    }

    /** Narrow bars lose the word before they lose the meaning. */
    private fun fittingLabel(hud: Hud, budget: Int): String =
        listOf(hud.buttonLabel, "LV+", "UP").firstOrNull { DotFont.measure(it) <= budget }
            ?: DotFont.clip(hud.buttonLabel, budget)

    /** Extra strip on 4x2 and taller: run stats plus the event ticker. */
    private fun drawStatsStrip(p: DotPainter, hud: Hud, state: GameState, cols: Int, stripTop: Int, recentEvent: String?) {
        val x = 3f
        val right = cols - 3f
        val firstLine = stripTop + 3f
        val secondLine = firstLine + 10f
        p.dottedRule(x, stripTop.toFloat(), (right - x).toInt())

        p.text(x, firstLine, "ACT ${hud.stage}", Palette.INK)
        val best = DotFont.clip(hud.deepest, ((right - x) / 2f).toInt())
        p.text(right - DotFont.measure(best), firstLine, best, Palette.MID)

        val runes = "${hud.runes} +${state.runes * 2}%"
        p.text(x, secondLine, runes, Palette.MID)
        val meterX = x + DotFont.measure(runes) + 4f
        p.meter(meterX, secondLine + 2f, cells = 8, fraction = hud.runeProgress, lit = Palette.MID)

        val kills = "K ${Fmt.short(state.kills.toDouble())}"
        val ticker = recentEvent ?: if (state.autoLevel) "AUTO / IDLING" else kills
        val budget = (right - (meterX + p.meterWidth(8)) - 4f).toInt()
        if (budget > 20) {
            val clipped = DotFont.clipWords(ticker, budget)
            p.text(right - DotFont.measure(clipped), secondLine, clipped, Palette.MID)
        }
    }
}
