package dev.taskbarhero.paint

import dev.taskbarhero.engine.Balance
import dev.taskbarhero.engine.Fmt
import dev.taskbarhero.engine.GameState
import dev.taskbarhero.engine.Hud
import dev.taskbarhero.engine.IconKey
import dev.taskbarhero.engine.PixelFont
import dev.taskbarhero.engine.Sprites
import dev.taskbarhero.engine.Ticker
import dev.taskbarhero.engine.Tone

/**
 * The bar: a dungeon room three panels wide. Stats on the left, the fight in the
 * middle against brick and flagstone, the one action on the right.
 *
 * Sizes are in pixel cells, and the horizontal split matches the two touch zones
 * declared in the widget's RemoteViews layout.
 */
object BarLayout {

    /** Width of the LV UP button as a fraction of the bar. Mirrors the layout weights (300 / 1000). */
    const val ACTION_ZONE_FRACTION = 0.30f

    /** Art pixels across a one-row bar. Everything else derives from this pitch. */
    const val ROWS_COMPACT = 32

    /** Nominal height of one launcher row, in dp. */
    const val ROW_HEIGHT_DP = 70f

    /** From here up, the bar earns a stats strip under the arena. */
    const val ROWS_TALL = 44

    /** How long an event holds the arena caption instead of the monster name. */
    const val EVENT_FLASH_MS = 3_500L

    private const val PAD = 2f
    private const val COIN = 5f

    /** Rows the tall strip occupies: a divider and two text lines. */
    private const val STRIP_HEIGHT = 24

    private val ROW_Y = floatArrayOf(3f, 13f, 23f)

    /**
     * Device pixels per art pixel. Capped at the one-row pitch so a taller widget
     * gains *rows* rather than chunkier pixels — stretching would cost the bar the
     * horizontal cells its text needs.
     */
    fun unitPx(heightPx: Float, density: Float): Float =
        (minOf(heightPx, ROW_HEIGHT_DP * density) / ROWS_COMPACT).coerceAtLeast(1.5f)

    fun rowsFor(heightPx: Float, density: Float): Int =
        (heightPx / unitPx(heightPx, density)).toInt().coerceAtLeast(ROWS_COMPACT)

    fun draw(
        p: PixelPainter,
        cols: Int,
        rows: Int,
        state: GameState,
        nowMs: Long,
        recent: Ticker? = null,
        b: Balance = Balance(),
    ) {
        val hud = Hud.of(state, b, autoNote = false)
        val tall = rows >= ROWS_TALL
        val stripTop = if (tall) rows - STRIP_HEIGHT else rows
        val arenaBottom = stripTop - 1f

        // The window itself: a bevelled slab, like a game docked to a taskbar.
        p.fill(0f, 0f, cols.toFloat(), rows.toFloat(), Palette.VOID)
        p.panel(0f, 0f, cols.toFloat(), rows.toFloat(), Palette.STONE)

        val actionLeft = cols * (1f - ACTION_ZONE_FRACTION)
        val statusRight = statusWidth(hud)

        drawStatusColumn(p, hud, statusRight, arenaBottom)
        drawArena(p, hud, from = statusRight, to = actionLeft - 1f, bottom = arenaBottom, nowMs = nowMs, recent = recent)
        drawActionButton(p, hud, from = actionLeft, to = cols - PAD, bottom = arenaBottom)
        if (tall) drawStatsStrip(p, hud, state, cols, stripTop, recent)
    }

    /** Width of the left column, measured from the strings actually in it. */
    private fun statusWidth(hud: Hud): Float {
        val stage = PixelFont.measure(hud.stage).toFloat()
        val level = PixelFont.measure(hud.level).toFloat()
        val gold = COIN + 2f + PixelFont.measure(hud.gold)
        return PAD + 2f + maxOf(stage, level, gold) + 4f
    }

    /**
     * Left column: where we are, how strong we are, what we can spend. Runes are
     * left to the tall strip — three lines is all a 4x1 bar can hold.
     */
    private fun drawStatusColumn(p: PixelPainter, hud: Hud, right: Float, bottom: Float) {
        val x = PAD + 2f
        p.text(x, ROW_Y[0], hud.stage, Palette.PARCHMENT)
        p.text(x, ROW_Y[1], hud.level, Palette.PARCHMENT_DIM)
        p.sprite(x, ROW_Y[2] + 1f, Sprites.icon(IconKey.COIN))
        p.text(x + COIN + 2f, ROW_Y[2], hud.gold, Palette.GOLD)
        // Hairline between the stats and the room.
        p.fill(right - 3f, PAD + 1f, 1f, bottom - PAD - 2f, Palette.BEVEL_DARK)
    }

    /** Middle: the room. Brick wall, torch, flagstone, two fighters, two meters. */
    private fun drawArena(
        p: PixelPainter,
        hud: Hud,
        from: Float,
        to: Float,
        bottom: Float,
        nowMs: Long,
        recent: Ticker?,
    ) {
        val width = to - from
        if (width < 24f) return

        val top = PAD
        val height = bottom - top

        // Stacked from the top: caption on the wall, fighters standing on the
        // flagstone, their health sunk into the floor beneath their feet. A 4x1 bar
        // has no row to spare, and nothing may cover a sprite's head.
        val captionY = top + 1f
        val meterHeight = 3f
        val meterY = bottom - meterHeight - 1f
        val feet = meterY - 2f
        val slot = Sprites.FIGHTER_SIZE
        // Integer scale only: half a pixel of scaling would drop art pixels.
        val scale = minOf((feet - (captionY + PixelFont.HEIGHT + 2f)) / slot, (width - 8f) / 2f / slot)
            .toInt().coerceAtLeast(1).toFloat()
        val side = slot * scale
        val floorTop = feet - 1f

        p.bricks(from, top, width, floorTop - top)
        p.floor(from, floorTop, width, bottom - floorTop)
        // Framed dark, so the room reads as sunk into the panel.
        p.frame(from, top, width, height, Palette.BEVEL_DARK)

        if (width > 46f) p.torch(from + width / 2f - 2f, top + 10f, nowMs)

        val budget = (width - 6f).toInt()
        val caption = recent?.text ?: if (hud.isDown) fittingWord(DEFEAT_WORDS, budget) else hud.enemyShort
        val captionColor = recent?.let(::toneColor) ?: if (hud.isDown) Palette.HP else Palette.PARCHMENT
        val text = PixelFont.clipWords(caption, budget)
        val textWidth = PixelFont.measure(text).toFloat()
        val center = (from + to) / 2f
        // A plaque behind the letters: parchment on masonry needs the contrast.
        p.fill(center - textWidth / 2f - 2f, captionY - 1f, textWidth + 4f, PixelFont.HEIGHT + 2f, Palette.STONE_DARK)
        p.text(center - textWidth / 2f, captionY, text, captionColor)

        val heroArt = Sprites.heroFrame(nowMs, if (hud.isDown) 1L else 0L)
        val heroX = from + 3f
        p.shadow(heroX + side / 2f, feet - 1f, side * 0.7f)
        p.sprite(heroX, feet - side, heroArt, side / heroArt.size)

        if (!hud.isDown) {
            val enemyArt = Sprites.of(hud.enemySprite)
            val enemyX = to - 3f - side
            p.shadow(enemyX + side / 2f, feet - 1f, side * 0.7f)
            p.sprite(enemyX, feet - side, enemyArt, side / enemyArt.size)
        }

        val meterWidth = ((width - 9f) / 2f).coerceAtMost(30f)
        p.bar(from + 3f, meterY, meterWidth, meterHeight, hud.heroHp, Palette.HP, Palette.HP_SOCKET)
        if (!hud.isDown) {
            p.bar(to - 3f - meterWidth, meterY, meterWidth, meterHeight, hud.enemyHp, Palette.FOE, Palette.FOE_SOCKET)
        }
    }

    /** Right: the single tappable action, as a chunky gold-rimmed button. */
    private fun drawActionButton(p: PixelPainter, hud: Hud, from: Float, to: Float, bottom: Float) {
        val w = to - from
        if (w < 12f) return
        val top = PAD + 1f
        val h = (bottom - 1f - top).coerceAtMost(30f)
        val hot = hud.buttonEnabled

        // Dark slab either way; gold is the trim, not the fill. A lit button gets a
        // gold rim and a gold price, which is all the invitation it needs.
        p.panel(from, top, w, h, Palette.STONE_DARK, light = if (hot) Palette.GOLD_DARK else Palette.BEVEL_DARK, dark = Palette.BEVEL_DARK)
        p.frame(from, top, w, h, if (hot) Palette.GOLD else Palette.BEVEL_LIGHT)

        val center = from + w / 2f
        val budget = (w - 4f).toInt()
        val labelY = top + (h - 17f) / 2f
        p.textCentered(center, labelY, fittingLabel(hud, budget), if (hot) Palette.PARCHMENT else Palette.PARCHMENT_DIM)

        // The coin only appears on a price you can actually pay — an unaffordable
        // level should not glint at you.
        if (hot) {
            val cost = PixelFont.clip(hud.buttonCost, budget - 7)
            val costX = center - (COIN + 2f + PixelFont.measure(cost)) / 2f
            p.sprite(costX, labelY + 10f, Sprites.icon(IconKey.COIN))
            p.text(costX + COIN + 2f, labelY + 9f, cost, Palette.GOLD)
        } else {
            p.textCentered(center, labelY + 9f, PixelFont.clip(hud.buttonCost, budget), Palette.GOLD_DARK)
        }
    }

    /** Narrow bars lose the word before they lose the meaning. */
    private fun fittingLabel(hud: Hud, budget: Int): String =
        fittingWord(listOf(hud.buttonLabel, "LV+", "UP"), budget)

    private val DEFEAT_WORDS = listOf("DEFEATED", "WIPED", "DEAD")

    /** First of [words] that fits, falling back to a hard clip of the first. */
    private fun fittingWord(words: List<String>, budget: Int): String =
        words.firstOrNull { PixelFont.measure(it) <= budget } ?: PixelFont.clip(words.first(), budget)

    /** Extra strip on 4x2 and taller: rune progress, the record, the ticker. */
    private fun drawStatsStrip(p: PixelPainter, hud: Hud, state: GameState, cols: Int, stripTop: Int, recent: Ticker?) {
        val x = PAD + 2f
        val right = cols - PAD - 2f
        val firstLine = stripTop + 3f
        val secondLine = firstLine + 10f
        p.divider(x, stripTop.toFloat(), right - x)

        p.text(x, firstLine, "ACT ${hud.stage}", Palette.PARCHMENT)
        val best = PixelFont.clip(hud.deepest, ((right - x) / 2f).toInt())
        p.text(right - PixelFont.measure(best), firstLine, best, Palette.PARCHMENT_DIM)

        val runes = "${hud.runes} +${state.runes * 2}%"
        p.text(x, secondLine, runes, Palette.MANA)
        val meterX = x + PixelFont.measure(runes) + 4f
        val meterWidth = 22f
        p.bar(meterX, secondLine + 1f, meterWidth, 4f, hud.runeProgress, Palette.MANA, Palette.MANA_SOCKET)

        val kills = "${Fmt.short(state.kills.toDouble())} SLAIN"
        val ticker = recent?.text ?: if (state.autoLevel) "AUTO" else kills
        val color = recent?.let(::toneColor) ?: Palette.PARCHMENT_DIM
        val budget = (right - (meterX + meterWidth) - 5f).toInt()
        if (budget > 20) {
            val clipped = PixelFont.clipWords(ticker, budget)
            p.text(right - PixelFont.measure(clipped), secondLine, clipped, color)
        }
    }

    /** Tone to colour — the one place the palette's meaning is decided. */
    internal fun toneColor(ticker: Ticker): Int = when (ticker.tone) {
        Tone.NEUTRAL -> Palette.PARCHMENT_DIM
        Tone.GOOD -> Palette.GOLD
        Tone.BAD -> Palette.HP
        Tone.MAGIC -> Palette.MANA
        Tone.LOOT -> Palette.grade(ticker.grade)
    }
}
