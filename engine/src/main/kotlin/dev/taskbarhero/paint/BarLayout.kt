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

    /**
     * Height of the button deck, in dp. Fixed rather than proportional so the
     * buttons stay a comfortable touch target on every widget size — and so the
     * RemoteViews layout can pin the same value with a plain dp height.
     */
    const val DECK_HEIGHT_DP = 48f

    /**
     * How the deck's width splits between LV UP / POTION / AUTO. These mirror the
     * layout_weight values in res/layout/widget_bar_tall.xml; a test keeps them
     * summing to one.
     */
    val DECK_SPLIT = floatArrayOf(0.40f, 0.30f, 0.30f)

    /** One info line above the deck: act and record, then runes and kills. */
    private const val INFO_LINE = 11

    /** Rows a room needs before it can hold a caption, fighters and their meters. */
    private const val MIN_ROOM = 30

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

    /** Rows the deck takes at a given pixel pitch. Constant in dp, so constant in dp. */
    fun deckRows(unitPx: Float, density: Float): Int =
        Math.round(DECK_HEIGHT_DP * density / unitPx).coerceAtLeast(18)

    fun draw(
        p: PixelPainter,
        cols: Int,
        rows: Int,
        state: GameState,
        nowMs: Long,
        recent: Ticker? = null,
        b: Balance = Balance(),
        /** Rows reserved for the button deck; 0 on a one-row bar, which has none. */
        deckRows: Int = 0,
    ) {
        val hud = Hud.of(state, b, autoNote = false)
        val deck = if (rows >= ROWS_TALL) deckRows else 0

        // The window itself: a bevelled slab, like a game docked to a taskbar.
        p.fill(0f, 0f, cols.toFloat(), rows.toFloat(), Palette.VOID)
        p.panel(0f, 0f, cols.toFloat(), rows.toFloat(), Palette.STONE)

        if (deck <= 0) {
            // One-row bar: room plus the single action on the right.
            val actionLeft = cols * (1f - ACTION_ZONE_FRACTION)
            val statusRight = statusWidth(hud)
            drawStatusColumn(p, hud, statusRight, rows - 1f)
            drawArena(p, hud, statusRight, actionLeft - 1f, rows - 1f, nowMs, recent)
            drawActionButton(p, hud, actionLeft, cols - PAD, rows - 1f)
            return
        }

        // Tall bar: the room owns the full width, the deck carries the actions, and
        // the info lines appear only if they do not cost the fighters their size —
        // on a two-row widget the room wins, since the deck already shows the
        // numbers you act on and the status column the ones you watch.
        val deckTop = rows - deck
        val infoLines = if (deckTop - MIN_ROOM - 1 >= 2 * INFO_LINE) 2 else 0
        val roomBottom = deckTop - infoLines * INFO_LINE - 1f

        val statusRight = statusWidth(hud)
        drawStatusColumn(p, hud, statusRight, roomBottom)
        drawArena(p, hud, statusRight, cols - PAD - 1f, roomBottom, nowMs, recent)
        drawInfoLines(p, hud, state, cols, roomBottom + 2f, infoLines, recent)
        drawDeck(p, hud, cols, deckTop.toFloat(), rows - 1f)
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

    /** Info above the deck: where we stand, and what is accruing on its own. */
    private fun drawInfoLines(
        p: PixelPainter,
        hud: Hud,
        state: GameState,
        cols: Int,
        top: Float,
        lines: Int,
        recent: Ticker?,
    ) {
        if (lines <= 0) return
        val x = PAD + 2f
        val right = cols - PAD - 2f

        // First line: the run's high-water marks. The current act is already in the
        // status column, so repeating it here would waste the only spare line.
        p.text(x, top, PixelFont.clip(hud.deepest, ((right - x) / 2f).toInt()), Palette.PARCHMENT)
        val slain = "${Fmt.short(state.kills.toDouble())} SLAIN"
        p.text(right - PixelFont.measure(slain), top, slain, Palette.PARCHMENT_DIM)

        if (lines < 2) return
        // Second line: passive progress on the left, the ticker on the right.
        val y = top + INFO_LINE
        val runes = "R${state.runes} +${state.runes * 2}%"
        p.text(x, y, runes, Palette.MANA)
        val meterX = x + PixelFont.measure(runes) + 4f
        val meterWidth = 22f
        p.bar(meterX, y + 1f, meterWidth, 4f, hud.runeProgress, Palette.MANA, Palette.MANA_SOCKET)

        // The rest of the line belongs to events. Left empty between them, so a
        // boss drop actually catches the eye instead of replacing a static number.
        val ticker = recent ?: return
        val budget = (right - (meterX + meterWidth) - 5f).toInt()
        if (budget > 20) {
            val clipped = PixelFont.clipWords(ticker.text, budget)
            p.text(right - PixelFont.measure(clipped), y, clipped, toneColor(ticker))
        }
    }

    /**
     * The deck: three real buttons, drawn where the RemoteViews touch zones sit.
     * [DECK_SPLIT] is the contract between this drawing and those zones.
     */
    private fun drawDeck(p: PixelPainter, hud: Hud, cols: Int, top: Float, bottom: Float) {
        val left = PAD
        val width = cols - PAD * 2f
        val height = bottom - top - 1f
        if (height < 12f || width < 30f) return

        var x = left
        val buttons = listOf(
            DeckButton(listOf("LV UP", "LV+"), hud.buttonCost, hud.buttonEnabled, Palette.GOLD),
            DeckButton(listOf("POTION", "HEAL"), hud.potionCost, hud.potionEnabled, Palette.HP),
            DeckButton(listOf("AUTO"), if (hud.autoOn) "ON" else "OFF", hud.autoOn, Palette.MANA, costHasCoin = false),
        )
        for ((i, button) in buttons.withIndex()) {
            val w = width * DECK_SPLIT[i] - if (i < buttons.lastIndex) 1f else 0f
            drawDeckButton(p, button, x, top + 1f, w, height)
            x += width * DECK_SPLIT[i]
        }
    }

    private data class DeckButton(
        /** Labels longest first; the widest that fits is the one drawn. */
        val labels: List<String>,
        val value: String,
        val lit: Boolean,
        val accent: Int,
        val costHasCoin: Boolean = true,
    )

    private fun drawDeckButton(p: PixelPainter, button: DeckButton, x: Float, y: Float, w: Float, h: Float) {
        val trim = if (button.lit) button.accent else Palette.BEVEL_LIGHT
        p.panel(x, y, w, h, Palette.STONE_DARK, light = trim, dark = Palette.BEVEL_DARK)
        p.frame(x, y, w, h, trim)

        val center = x + w / 2f
        val budget = (w - 4f).toInt()
        val twoLines = h >= 19f
        val labelY = if (twoLines) y + (h - 17f) / 2f else y + (h - PixelFont.HEIGHT) / 2f
        p.textCentered(center, labelY, fittingWord(button.labels, budget), if (button.lit) Palette.PARCHMENT else Palette.PARCHMENT_DIM)
        if (!twoLines) return

        val valueY = labelY + 10f
        if (button.costHasCoin && button.lit) {
            val cost = PixelFont.clip(button.value, budget - 7)
            val costX = center - (COIN + 2f + PixelFont.measure(cost)) / 2f
            p.sprite(costX, valueY + 1f, Sprites.icon(IconKey.COIN))
            p.text(costX + COIN + 2f, valueY, cost, Palette.GOLD)
        } else {
            val color = if (button.lit) button.accent else Palette.PARCHMENT_DIM
            p.textCentered(center, valueY, PixelFont.clip(button.value, budget), color)
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
