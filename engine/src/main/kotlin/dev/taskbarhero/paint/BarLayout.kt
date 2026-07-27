package dev.taskbarhero.paint

import dev.taskbarhero.engine.Balance
import dev.taskbarhero.engine.Fmt
import dev.taskbarhero.engine.GameState
import dev.taskbarhero.engine.Hud
import dev.taskbarhero.engine.IconKey
import dev.taskbarhero.engine.PixelFont
import dev.taskbarhero.engine.SpriteKey
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

    /**
     * Cells across a one-row bar. Fine enough that a 32x32 fighter fits with room
     * to spare, which is why text is drawn at 2x on top of it.
     */
    const val ROWS_COMPACT = 64

    /** Nominal height of one launcher row, in dp. */
    const val ROW_HEIGHT_DP = 70f

    /** From here up, the bar earns a stats strip under the arena. */
    const val ROWS_TALL = 88

    /** How long an event holds the arena caption instead of the monster name. */
    const val EVENT_FLASH_MS = 3_500L

    private const val PAD = 4f

    /** The coin icon is 5x5 art drawn at 2x, like the text beside it. */
    private const val COIN = 10f
    private const val ICON_SCALE = 2f

    /**
     * Height of the button deck, in dp. Fixed rather than proportional so the
     * buttons stay a comfortable touch target on every widget size — and so the
     * RemoteViews layout can pin the same value with a plain dp height.
     */
    const val DECK_HEIGHT_DP = 48f

    /**
     * How the deck's width splits between LV UP / HEAL / CUBE / AUTO. These mirror
     * the layout_weight values in res/layout/widget_bar_tall.xml; a test keeps them
     * summing to one.
     */
    val DECK_SPLIT = floatArrayOf(0.28f, 0.24f, 0.24f, 0.24f)

    /** One info line above the deck: act and record, then runes and kills. */
    private const val INFO_LINE = 22

    /** Rows a room needs before it can hold a caption, fighters and their meters. */
    private const val MIN_ROOM = 60

    private val ROW_Y = floatArrayOf(6f, 26f, 46f)

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
            val statusRight = statusWidth(p, hud)
            drawStatusColumn(p, hud, statusRight, rows - 2f)
            drawArena(p, hud, statusRight, actionLeft - 2f, rows - 2f, nowMs, recent, Biome.of(state.act))
            drawActionButton(p, hud, actionLeft, cols - PAD, rows - 2f)
            return
        }

        // Tall bar: the room owns the full width, the deck carries the actions, and
        // the info lines appear only if they do not cost the fighters their size —
        // on a two-row widget the room wins, since the deck already shows the
        // numbers you act on and the status column the ones you watch.
        val deckTop = rows - deck
        val infoLines = if (deckTop - MIN_ROOM - 2 >= 2 * INFO_LINE) 2 else 0
        val roomBottom = deckTop - infoLines * INFO_LINE - 2f

        val statusRight = statusWidth(p, hud)
        drawStatusColumn(p, hud, statusRight, roomBottom)
        drawArena(p, hud, statusRight, cols - PAD - 2f, roomBottom, nowMs, recent, Biome.of(state.act))
        drawInfoLines(p, hud, state, cols, roomBottom + 4f, infoLines, recent)
        drawDeck(p, hud, cols, deckTop.toFloat(), rows - 2f)
    }

    /** Width of the left column, measured from the strings actually in it. */
    private fun statusWidth(p: PixelPainter, hud: Hud): Float {
        val stage = p.measure(hud.stage)
        val level = p.measure(hud.level)
        val gold = COIN + 4f + p.measure(hud.gold)
        return PAD + 4f + maxOf(stage, level, gold) + 8f
    }

    /**
     * Left column: where we are, how strong we are, what we can spend. Runes are
     * left to the tall strip — three lines is all a 4x1 bar can hold.
     */
    private fun drawStatusColumn(p: PixelPainter, hud: Hud, right: Float, bottom: Float) {
        val x = PAD + 4f
        p.text(x, ROW_Y[0], hud.stage, Palette.PARCHMENT)
        p.text(x, ROW_Y[1], hud.level, Palette.PARCHMENT_DIM)
        p.sprite(x, ROW_Y[2] + 2f, Sprites.icon(IconKey.COIN), ICON_SCALE)
        p.text(x + COIN + 4f, ROW_Y[2], hud.gold, Palette.GOLD)
        // Hairline between the stats and the room.
        p.fill(right - 6f, PAD + 2f, 2f, bottom - PAD - 4f, Palette.BEVEL_DARK)
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
        biome: Biome,
    ) {
        val width = to - from
        if (width < 48f) return

        val top = PAD
        val height = bottom - top

        // Stacked from the top: caption on the wall, fighters standing on the
        // flagstone, their health sunk into the floor beneath their feet. A 4x1 bar
        // has no row to spare, and nothing may cover a sprite's head.
        val captionY = top + 2f
        val meterHeight = 6f
        val meterY = bottom - meterHeight - 3f
        val feet = meterY - 5f
        // The party and the monster share the room, so the party is sized as a
        // block: whatever scale lets all of them stand in half the arena.
        val members = hud.party.size.coerceAtLeast(1)
        val scale = Formation.scale(
            members = members,
            maxWidth = (width - 20f) * 0.55f,
            maxHeight = feet - (captionY + p.textHeight + 4f),
            slotFraction = partyFraction(p, hud),
            unit = p.spriteUnit,
        )
        val side = p.spriteUnit * scale
        val floorTop = feet - 3f

        p.bricks(from, top, width, floorTop - top, biome)
        p.floor(from, floorTop, width, bottom - floorTop, biome)
        // Framed dark, so the room reads as sunk into the panel.
        p.frame(from, top, width, height, Palette.BEVEL_DARK)

        // Torches on the side walls, not over the middle: the fight stands in the
        // centre of the room, and a sconce through a hero's head is not lighting.
        // On a one-row bar there is no wall above the heads either, so the room
        // goes unlit rather than cluttered.
        if (width > 92f && top + 36f <= feet - side) {
            p.torch(from + width * 0.14f, top + 20f, nowMs)
            // Offset flicker, so the pair does not blink in lockstep.
            p.torch(from + width * 0.86f - 8f, top + 20f, nowMs + 200L)
        }

        val budget = width - 12f
        val caption = recent?.text ?: if (hud.isDown) fittingWord(p, DEFEAT_WORDS, budget) else hud.enemyShort
        val captionColor = recent?.let(::toneColor) ?: if (hud.isDown) Palette.HP else Palette.PARCHMENT
        val text = p.clipWords(caption, budget)
        val textWidth = p.measure(text).toFloat()
        val center = (from + to) / 2f
        // A plaque behind the letters: parchment on masonry needs the contrast.
        p.fill(center - textWidth / 2f - 4f, captionY - 2f, textWidth + 8f, p.textHeight + 4f, biome.stoneDark)
        p.text(center - textWidth / 2f, captionY, text, captionColor)

        val roster = hud.party
        // Spaced by how wide the sprites really come out — a pack of narrow heroes
        // must close ranks rather than stand a box-width apart — and measured on
        // the party standing, so the formation does not reflow around the smaller
        // grave sprites the moment it wipes.
        val slot = roster.maxOf { p.fighterWidth(side, it.cls.sprite.name) }
        val enemyWidth = if (hud.isDown) 0f else p.fighterWidth(side, hud.enemySprite.name)
        val scene = Formation.scene(roster.size, slot, enemyWidth, from + 6f, to - 6f)

        for (i in Formation.drawOrder(roster.size)) {
            val member = roster[i]
            val art = Sprites.heroFrame(member.cls, nowMs, member.down || hud.isDown)
            val center = scene.partyLeft + Formation.offset(i, roster.size, slot) + slot / 2f
            p.shadow(center, feet - 2f, slot * 0.8f)
            p.fighter(center - side / 2f, feet - side, side, spriteKey(member, hud, nowMs), art, nowMs)
        }

        if (!hud.isDown) {
            val enemyArt = Sprites.of(hud.enemySprite)
            p.shadow(scene.enemyCenter, feet - 2f, enemyWidth * 0.8f)
            p.fighter(scene.enemyCenter - side / 2f, feet - side, side, hud.enemySprite.name, enemyArt, nowMs)
        }

        val meterWidth = ((width - 18f) / 2f).coerceAtMost(60f)
        p.bar(from + 6f, meterY, meterWidth, meterHeight, hud.frontHp, Palette.HP, Palette.HP_SOCKET)
        if (!hud.isDown) {
            p.bar(to - 6f - meterWidth, meterY, meterWidth, meterHeight, hud.enemyHp, Palette.FOE, Palette.FOE_SOCKET)
        }
    }

    /** Right: the single tappable action, as a chunky gold-rimmed button. */
    private fun drawActionButton(p: PixelPainter, hud: Hud, from: Float, to: Float, bottom: Float) {
        val w = to - from
        if (w < 24f) return
        val top = PAD + 2f
        val h = (bottom - 2f - top).coerceAtMost(60f)
        val hot = hud.buttonEnabled

        // Dark slab either way; gold is the trim, not the fill. A lit button gets a
        // gold rim and a gold price, which is all the invitation it needs.
        p.panel(from, top, w, h, Palette.STONE_DARK, light = if (hot) Palette.GOLD_DARK else Palette.BEVEL_DARK, dark = Palette.BEVEL_DARK)
        p.frame(from, top, w, h, if (hot) Palette.GOLD else Palette.BEVEL_LIGHT)

        val center = from + w / 2f
        val budget = w - 8f
        val labelY = top + (h - 34f) / 2f
        p.textCentered(center, labelY, fittingLabel(p, hud, budget), if (hot) Palette.PARCHMENT else Palette.PARCHMENT_DIM)

        // The coin only appears on a price you can actually pay — an unaffordable
        // level should not glint at you.
        if (hot) {
            val cost = p.clip(hud.buttonCost, budget - 14f)
            val costX = center - (COIN + 4f + p.measure(cost)) / 2f
            p.sprite(costX, labelY + 21f, Sprites.icon(IconKey.COIN), ICON_SCALE)
            p.text(costX + COIN + 4f, labelY + 19f, cost, Palette.GOLD)
        } else {
            p.textCentered(center, labelY + 19f, p.clip(hud.buttonCost, budget), Palette.GOLD_DARK)
        }
    }

    /** Narrow bars lose the word before they lose the meaning. */
    private fun fittingLabel(p: PixelPainter, hud: Hud, budget: Float): String =
        fittingWord(p, listOf(hud.buttonLabel, "LV+", "UP"), budget)

    private val DEFEAT_WORDS = listOf("DEFEATED", "WIPED", "DEAD")

    /** First of [words] that fits, falling back to a hard clip of the first. */
    private fun fittingWord(p: PixelPainter, words: List<String>, budget: Float): String =
        words.firstOrNull { p.measure(it) <= budget } ?: p.clip(words.first(), budget)

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
        val x = PAD + 4f
        val right = cols - PAD - 4f

        // First line: the run's high-water marks. The current act is already in the
        // status column, so repeating it here would waste the only spare line.
        p.text(x, top, p.clip(hud.deepest, (right - x) / 2f), Palette.PARCHMENT)
        val slain = "${Fmt.short(state.kills.toDouble())} SLAIN"
        p.text(right - p.measure(slain), top, slain, Palette.PARCHMENT_DIM)

        if (lines < 2) return
        // Second line: passive progress on the left, the ticker on the right.
        val y = top + INFO_LINE
        val runes = "R${state.runes} +${state.runes * 2}%"
        p.text(x, y, runes, Palette.MANA)
        val meterX = x + p.measure(runes) + 8f
        val meterWidth = 44f
        p.bar(meterX, y + 3f, meterWidth, 8f, hud.runeProgress, Palette.MANA, Palette.MANA_SOCKET)

        // The rest of the line belongs to events. Left empty between them, so a
        // boss drop actually catches the eye instead of replacing a static number.
        val ticker = recent ?: return
        val budget = right - (meterX + meterWidth) - 10f
        if (budget > 40f) {
            val clipped = p.clipWords(ticker.text, budget)
            p.text(right - p.measure(clipped), y, clipped, toneColor(ticker))
        }
    }

    /**
     * The deck: three real buttons, drawn where the RemoteViews touch zones sit.
     * [DECK_SPLIT] is the contract between this drawing and those zones.
     */
    private fun drawDeck(p: PixelPainter, hud: Hud, cols: Int, top: Float, bottom: Float) {
        val left = PAD
        val width = cols - PAD * 2f
        val height = bottom - top - 2f
        if (height < 24f || width < 60f) return

        var x = left
        val buttons = listOf(
            DeckButton(listOf("LV UP", "LV+"), hud.buttonCost, hud.buttonEnabled, Palette.GOLD),
            DeckButton(listOf("HEAL"), hud.potionCost, hud.potionEnabled, Palette.HP),
            // The Cube shows how close the stash is to a fusion, in its future grade's colour.
            DeckButton(
                listOf("CUBE"),
                hud.cubeLabel,
                hud.cubeEnabled,
                hud.cubeGrade?.let(Palette::grade) ?: Palette.BEVEL_LIGHT,
                costHasCoin = false,
            ),
            DeckButton(listOf("AUTO"), if (hud.autoOn) "ON" else "OFF", hud.autoOn, Palette.MANA, costHasCoin = false),
        )
        for ((i, button) in buttons.withIndex()) {
            val w = width * DECK_SPLIT[i] - if (i < buttons.lastIndex) 2f else 0f
            drawDeckButton(p, button, x, top + 2f, w, height)
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
        val budget = w - 8f
        val twoLines = h >= 38f
        val labelY = if (twoLines) y + (h - 34f) / 2f else y + (h - p.textHeight) / 2f
        p.textCentered(center, labelY, fittingWord(p, button.labels, budget), if (button.lit) Palette.PARCHMENT else Palette.PARCHMENT_DIM)
        if (!twoLines) return

        val valueY = labelY + 20f
        if (button.costHasCoin && button.lit) {
            val cost = p.clip(button.value, budget - 14f)
            val costX = center - (COIN + 4f + p.measure(cost)) / 2f
            p.sprite(costX, valueY + 2f, Sprites.icon(IconKey.COIN), ICON_SCALE)
            p.text(costX + COIN + 4f, valueY, cost, Palette.GOLD)
        } else {
            val color = if (button.lit) button.accent else Palette.PARCHMENT_DIM
            p.textCentered(center, valueY, p.clip(button.value, budget), color)
        }
    }

    /**
     * Sheet key for a party member — the same pose the built-in art would be
     * showing at [nowMs], so both sets of sprites keep the same beat.
     */
    internal fun spriteKey(member: dev.taskbarhero.engine.HeroHud, hud: Hud, nowMs: Long): String =
        Sprites.heroKey(member.cls, nowMs, member.down || hud.isDown).name

    /**
     * How much of its box the party's art fills across, measured on the standing
     * sprites. Feeds [Formation.scale] so a pack of slim heroes is sized by what it
     * draws rather than by the box it was handed.
     */
    internal fun partyFraction(p: PixelPainter, hud: Hud): Float {
        val box = p.spriteUnit.toFloat()
        val widest = hud.party.maxOfOrNull { p.fighterWidth(box, it.cls.sprite.name) } ?: box
        return widest / box
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
