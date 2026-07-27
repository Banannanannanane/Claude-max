package dev.taskbarhero.paint

import dev.taskbarhero.engine.Balance
import dev.taskbarhero.engine.Fmt
import dev.taskbarhero.engine.GameState
import dev.taskbarhero.engine.HeroClass
import dev.taskbarhero.engine.Hud
import dev.taskbarhero.engine.IconKey
import dev.taskbarhero.engine.PixelFont
import dev.taskbarhero.engine.SpriteKey
import dev.taskbarhero.engine.Sprites
import dev.taskbarhero.engine.Ticker

/**
 * The bar's room, given a full screen: torch-lit brick, fighters four times the
 * size, the stats the bar has no room for, and a scrolling combat log.
 *
 * Composed from the bottom up — log, then stats, then whatever is left goes to the
 * room — so a short viewport (landscape, a cover display) drops sections instead
 * of drawing past its own edge.
 */
object DungeonLayout {

    /**
     * Art pixels across a portrait phone: about 21 characters per line, which is
     * as small as a 5x7 face gets while staying comfortable.
     */
    const val TARGET_COLS = 260

    /** One text line plus its leading. */
    private const val LINE = 20

    private const val TITLE = "TASKBAR HERO"

    /** Cap on the integer sprite scale: past 4x the fighters swamp the room. */
    private const val MAX_SCALE = 3f
    private const val MIN_SPRITE = 32
    private const val MAX_LOG_LINES = 6
    private const val GAP = 8
    private const val MARGIN = 6f
    private const val COIN = 10f
    private const val ICON_SCALE = 2f
    /** The party sheet shows each hero at half size; 32 art pixels is plenty. */
    private const val FIGHTER = Sprites.FIGHTER_SIZE

    /** Four stat pairs, the rune bar, and the "next level" price. */
    private const val STAT_ROWS = 6

    /** A roster line has to clear a whole fighter, not just a line of text. */
    private const val PARTY_LINE = Sprites.FIGHTER_SIZE + 4

    private const val STASH_CELL = 22

    /** Three roster lines plus the two-row stash grid underneath. */
    private const val PARTY_HEIGHT = GAP + 3 * PARTY_LINE + 2 * (STASH_CELL + 6)

    /** Below this the room cannot hold a caption, two fighters and their meters. */
    private const val MIN_ROOM = 14 + 12 + MIN_SPRITE + 20

    fun draw(
        p: PixelPainter,
        cols: Int,
        rows: Int,
        state: GameState,
        log: List<Ticker>,
        nowMs: Long,
        b: Balance = Balance(),
    ) {
        if (cols < 120 || rows < p.textHeight * 2) return
        val hud = Hud.of(state, b)
        val bottom = rows - 1

        p.fill(0f, 0f, cols.toFloat(), rows.toFloat(), Palette.STONE_DARK)

        val headerBottom = drawTitleBar(p, state, cols)

        // Reserve the bottom blocks first; the room lives on what remains.
        val logHeight = if (log.isEmpty()) 0 else GAP + minOf(log.size, MAX_LOG_LINES) * LINE
        val partyHeight = PARTY_HEIGHT
        val statsHeight = GAP + STAT_ROWS * LINE
        val roomTop = headerBottom + 3

        // Sections give way from the bottom up as the viewport shrinks: the log
        // first, then the stats, then the party roster. The room never goes.
        var roomBottom = bottom - logHeight - statsHeight - partyHeight - GAP
        var showLog = logHeight > 0
        var showStats = true
        var showParty = true
        if (roomBottom - roomTop < MIN_ROOM) {
            roomBottom += logHeight
            showLog = false
        }
        if (roomBottom - roomTop < MIN_ROOM) {
            roomBottom += statsHeight
            showStats = false
        }
        if (roomBottom - roomTop < MIN_ROOM) {
            roomBottom += partyHeight
            showParty = false
        }
        roomBottom = roomBottom.coerceAtMost(bottom)

        drawRoom(p, hud, cols, nowMs, top = roomTop, bottom = roomBottom, biome = Biome.of(state.act))

        var y = roomBottom + GAP
        if (showParty) {
            drawParty(p, hud, state, cols, b, nowMs, top = y, bottom = bottom)
            y += partyHeight
        }
        if (showStats) {
            drawStats(p, hud, state, cols, b, top = y, bottom = bottom)
            y += statsHeight
        }
        if (showLog) drawLog(p, log, cols, top = y, bottom = bottom)
    }

    /**
     * The title bar of a little game window, because that is what TBH is: a tiny
     * always-on-top window docked to a taskbar. The three studs on the right are
     * the window buttons of that conceit — inert, and deliberately so.
     */
    private fun drawTitleBar(p: PixelPainter, state: GameState, cols: Int): Int {
        val height = p.textHeight + 12f
        p.panel(0f, 0f, cols.toFloat(), height, Palette.STONE)

        val mode = if (state.autoLevel) "AUTO" else "MANUAL"
        val titleX = MARGIN + 2f
        // The studs are decoration and go first when the bar is tight: a title that
        // says what the game is, and a chip that says what it is doing, both beat
        // three squares that do nothing.
        val studs = titleX + p.measure(TITLE) + 12f + p.measure(mode) + 16f + 36f + MARGIN <= cols

        var x = cols - MARGIN - 8f
        if (studs) {
            for (i in 0 until 3) {
                p.fill(x, height / 2f - 4f, 8f, 8f, Palette.BEVEL_DARK)
                p.frame(x, height / 2f - 4f, 8f, 8f, Palette.BEVEL_LIGHT)
                x -= 12f
            }
        }

        val modeX = x - p.measure(mode) - 16f
        p.text(modeX, 6f, mode, if (state.autoLevel) Palette.GOLD else Palette.PARCHMENT_DIM)

        // And the title still yields to the chip rather than running into it.
        p.text(titleX, 6f, p.clipWords(TITLE, modeX - titleX - 12f), Palette.PARCHMENT)
        return (height + 4f).toInt()
    }

    /** The room: brick, torches, flagstone, the duel, a meter under each fighter. */
    private fun drawRoom(p: PixelPainter, hud: Hud, cols: Int, nowMs: Long, top: Int, bottom: Int, biome: Biome) {
        val height = bottom - top
        if (height < MIN_ROOM) return

        val width = cols - MARGIN * 2
        val captionY = top + 8f
        val meterHeight = 10f
        val meterY = bottom - meterHeight - 6f
        val feet = meterY - 6f
        // Same block rule as the bar; here there is room for a 2x or 3x party.
        val members = hud.party.size.coerceAtLeast(1)
        val scale = minOf(
            Formation.scale(
                members,
                (width - 40f) * 0.55f,
                feet - (captionY + p.textHeight + 8f),
                BarLayout.partyFraction(p, hud),
                p.spriteUnit,
            ),
            MAX_SCALE,
        )
        val side = p.spriteUnit * scale
        val floorTop = feet - 4f

        p.bricks(MARGIN, top.toFloat(), width, floorTop - top, biome, courseHeight = 16, brickWidth = 34)
        p.floor(MARGIN, floorTop, width, bottom - floorTop, biome)
        p.frame(MARGIN, top.toFloat(), width, height.toFloat(), Palette.BEVEL_DARK)
        p.torch(MARGIN + width * 0.16f, top + 20f, nowMs, scale = 3f)
        p.torch(MARGIN + width * 0.84f, top + 20f, nowMs + 200L, scale = 3f)

        val caption = if (hud.isDown) "HERO DEFEATED" else hud.enemyName
        val text = p.clipWords(caption, width - 16f)
        val textWidth = p.measure(text)
        p.fill(cols / 2f - textWidth / 2f - 6f, captionY - 4f, textWidth + 12f, p.textHeight + 8f, biome.stoneDark)
        p.text(cols / 2f - textWidth / 2f, captionY, text, if (hud.isDown) Palette.HP else Palette.PARCHMENT)
        val roster = hud.party
        val slot = roster.maxOf { p.fighterWidth(side, it.cls.sprite.name) }
        val enemyWidth = if (hud.isDown) 0f else p.fighterWidth(side, hud.enemySprite.name)
        val scene = Formation.scene(roster.size, slot, enemyWidth, MARGIN + 12f, cols - MARGIN - 12f)
        for (i in Formation.drawOrder(roster.size)) {
            val member = roster[i]
            val art = Sprites.heroFrame(member.cls, nowMs, member.down || hud.isDown)
            val center = scene.partyLeft + Formation.offset(i, roster.size, slot) + slot / 2f
            p.shadow(center, feet, slot * 0.8f)
            p.fighter(center - side / 2f, feet - side, side, BarLayout.spriteKey(member, hud, nowMs), art, nowMs)
        }

        if (!hud.isDown) {
            val enemyArt = Sprites.of(hud.enemySprite)
            p.shadow(scene.enemyCenter, feet, enemyWidth * 0.8f)
            p.fighter(scene.enemyCenter - side / 2f, feet - side, side, hud.enemySprite.name, enemyArt, nowMs)
        }

        val meterWidth = (width / 2f - 24f).coerceAtMost(92f)
        p.bar(MARGIN + 12f, meterY, meterWidth, meterHeight, hud.frontHp, Palette.HP, Palette.HP_SOCKET)
        if (!hud.isDown) {
            p.bar(cols - MARGIN - 12f - meterWidth, meterY, meterWidth, meterHeight, hud.enemyHp, Palette.FOE, Palette.FOE_SOCKET)
        }
    }

    /**
     * The party sheet: who is in it, how strong, how hurt — and the stash under it,
     * which is the Cube's larder. This is the payoff for opening the widget.
     */
    private fun drawParty(
        p: PixelPainter,
        hud: Hud,
        state: GameState,
        cols: Int,
        b: Balance,
        nowMs: Long,
        top: Int,
        bottom: Int,
    ) {
        p.divider(MARGIN, top.toFloat(), cols - MARGIN * 2)
        val row = PARTY_LINE
        var y = top + GAP

        // Columns measured from the widest strings, so the bar never lands on a level.
        val labelX = MARGIN + FIGHTER + 8f
        val levelX = labelX + HeroClass.entries.maxOf { p.measure(it.label) } + 8f
        val barX = levelX + p.measure("LV 999") + 8f
        val barWidth = cols - MARGIN - barX

        for (member in hud.party) {
            if (y + FIGHTER > bottom) return
            // The roster is a portrait, not a fight: the hero breathes, but never
            // swings at a monster that is not there.
            val pose = if (member.down) SpriteKey.GRAVE else member.cls.sprite
            p.fighter(MARGIN, y.toFloat(), FIGHTER.toFloat(), pose.name, Sprites.of(pose), nowMs)
            val mid = y + (Sprites.FIGHTER_SIZE - p.textHeight) / 2f
            p.text(labelX, mid, member.cls.label, if (member.down) Palette.HP else Palette.PARCHMENT)
            p.text(levelX, mid, p.clip(member.level, barX - levelX - 8f), Palette.PARCHMENT_DIM)
            if (barWidth > 16f) p.bar(barX, mid + 2f, barWidth, 10f, member.hp, Palette.HP, Palette.HP_SOCKET)
            y += row
        }
        // Locked slots stay visible: knowing a mage is coming is the point.
        for (locked in state.party.drop(state.unlocked)) {
            if (y + FIGHTER > bottom) return
            val mid = y + (Sprites.FIGHTER_SIZE - p.textHeight) / 2f
            p.text(labelX, mid, locked.cls.label, Palette.BEVEL_LIGHT)
            p.text(levelX, mid, "ACT ${locked.cls.unlockAct}", Palette.BEVEL_LIGHT)
            y += row
        }

        drawStash(p, state, cols, y.toFloat(), bottom, b)
    }

    /**
     * The stash as an inventory grid: one bordered cell per grade, its border the
     * grade's colour, with the count beside it. Two rows, because ten cells in one
     * row leaves no width for the numbers.
     */
    private fun drawStash(p: PixelPainter, state: GameState, cols: Int, y: Float, bottom: Int, b: Balance) {
        val perRow = state.stash.size / 2
        val slot = (cols - MARGIN * 2) / perRow
        val cell = STASH_CELL.toFloat()
        for ((grade, count) in state.stash.withIndex()) {
            val x = MARGIN + (grade % perRow) * slot
            val row = y + (grade / perRow) * (cell + 6f)
            // A short viewport drops the second row rather than drawing past its edge.
            if (row + cell > bottom) return
            val color = Palette.grade(grade)
            p.itemCell(x, row, cell, color, filled = count > 0)
            if (count == 0) continue
            // A pile one fusion away wears its grade's colour; the rest stay quiet.
            val ready = count >= b.cubeInput
            p.text(x + cell + 4f, row + 4f, count.toString(), if (ready) color else Palette.PARCHMENT_DIM)
        }
    }

    /** Four label/value pairs, the rune bar, then the price of the next level. */
    private fun drawStats(
        p: PixelPainter,
        hud: Hud,
        state: GameState,
        cols: Int,
        b: Balance,
        top: Int,
        bottom: Int,
    ) {
        p.divider(MARGIN, top.toFloat(), cols - MARGIN * 2)

        val pairs = listOf(
            Stat("ACT", hud.stage, "BEST", Fmt.stage(state.deepestAct, state.deepestWave)),
            Stat("GOLD", hud.gold, "SLAIN", Fmt.short(state.kills.toDouble())),
            Stat("GEAR", hud.gearBonus, "BOSSES", state.bossKills.toString()),
            Stat("RUNES", state.runes.toString(), "WIPES", state.deaths.toString()),
        )

        // Label and value ride together instead of sitting in aligned columns: at
        // 21 characters a line, aligned columns leave no room for the values.
        val rightX = cols * 0.5f
        val leftBudget = rightX - MARGIN - 8f
        val rightBudget = cols - MARGIN - rightX

        var y = top + GAP
        for (stat in pairs) {
            if (y + p.textHeight > bottom) return
            val valueColor = when (stat.label) {
                "GOLD" -> Palette.GOLD
                "GEAR" -> Palette.MANA
                else -> Palette.PARCHMENT
            }
            drawPair(p, MARGIN, y.toFloat(), stat.label, stat.value, leftBudget, valueColor)
            drawPair(p, rightX, y.toFloat(), stat.rightLabel, stat.rightValue, rightBudget, Palette.PARCHMENT)
            y += LINE
        }

        if (y + p.textHeight <= bottom) {
            val label = "RUNE +${state.runes * 2}%"
            p.text(MARGIN, y.toFloat(), label, Palette.MANA)
            p.bar(MARGIN + p.measure(label) + 10f, y + 3f, 60f, 10f, hud.runeProgress, Palette.MANA, Palette.MANA_SOCKET)
            y += LINE
        }
        if (y + p.textHeight <= bottom) {
            p.text(MARGIN, y.toFloat(), "NEXT LV", Palette.PARCHMENT_DIM)
            val x = MARGIN + p.measure("NEXT LV") + 10f
            p.sprite(x, y + 2f, Sprites.icon(IconKey.COIN), ICON_SCALE)
            p.text(
                x + COIN + 4f,
                y.toFloat(),
                Fmt.short(state.levelCost(b)),
                if (hud.buttonEnabled) Palette.GOLD else Palette.GOLD_DARK,
            )
        }
    }

    /** "GOLD 4.5K": dim label, bright value, clipped as a unit. */
    private fun drawPair(p: PixelPainter, x: Float, y: Float, label: String, value: String, budget: Float, color: Int) {
        p.text(x, y, label, Palette.PARCHMENT_DIM)
        val valueX = x + p.measure(label) + 8f
        val room = budget - p.measure(label) - 8f
        if (room > 0f) p.text(valueX, y, p.clip(value, room), color)
    }

    /** Combat log, newest last, each line in its event's own colour. */
    private fun drawLog(p: PixelPainter, log: List<Ticker>, cols: Int, top: Int, bottom: Int) {
        if (log.isEmpty()) return
        p.divider(MARGIN, top.toFloat(), cols - MARGIN * 2)
        var y = top + GAP
        val room = ((bottom - y + 1) / LINE).coerceIn(0, MAX_LOG_LINES)
        for (line in log.takeLast(room)) {
            if (y + p.textHeight > bottom) return
            p.text(MARGIN, y.toFloat(), p.clipWords(line.text, cols - MARGIN * 2), BarLayout.toneColor(line))
            y += LINE
        }
    }

    private data class Stat(val label: String, val value: String, val rightLabel: String, val rightValue: String)
}
