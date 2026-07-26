package dev.taskbarhero.paint

import dev.taskbarhero.engine.Balance
import dev.taskbarhero.engine.Fmt
import dev.taskbarhero.engine.GameState
import dev.taskbarhero.engine.HeroClass
import dev.taskbarhero.engine.Hud
import dev.taskbarhero.engine.IconKey
import dev.taskbarhero.engine.PixelFont
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
    const val TARGET_COLS = 130

    /** One text line plus its leading. */
    private const val LINE = 9
    private const val TEXT = PixelFont.HEIGHT

    /** Cap on the integer sprite scale: past 4x the fighters swamp the room. */
    private const val MAX_SCALE = 4f
    private const val MIN_SPRITE = 12
    private const val MAX_LOG_LINES = 6
    private const val GAP = 4
    private const val MARGIN = 3f
    private const val COIN = 5f
    private const val FIGHTER = Sprites.FIGHTER_SIZE

    /** Four stat pairs, the rune bar, and the "next level" price. */
    private const val STAT_ROWS = 6

    /** Three party lines, plus the two-row stash grid underneath. */
    private const val PARTY_ROWS = 5

    /** Below this the room cannot hold a caption, two fighters and their meters. */
    private const val MIN_ROOM = TEXT + 6 + MIN_SPRITE + 10

    fun draw(
        p: PixelPainter,
        cols: Int,
        rows: Int,
        state: GameState,
        log: List<Ticker>,
        nowMs: Long,
        b: Balance = Balance(),
    ) {
        if (cols < 60 || rows < TEXT * 2) return
        val hud = Hud.of(state, b)
        val bottom = rows - 1

        p.fill(0f, 0f, cols.toFloat(), rows.toFloat(), Palette.STONE_DARK)

        val headerBottom = drawTitleBar(p, state, cols)

        // Reserve the bottom blocks first; the room lives on what remains.
        val logHeight = if (log.isEmpty()) 0 else GAP + minOf(log.size, MAX_LOG_LINES) * LINE
        val partyHeight = GAP + PARTY_ROWS * (LINE + 3)
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
            drawParty(p, hud, state, cols, b, top = y, bottom = bottom)
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
        val height = TEXT + 6f
        p.panel(0f, 0f, cols.toFloat(), height, Palette.STONE)
        p.text(MARGIN + 1f, 3f, "TASKBAR HERO", Palette.PARCHMENT)

        var x = cols - MARGIN - 3f
        for (i in 0 until 3) {
            p.fill(x, height / 2f - 2f, 3f, 3f, Palette.BEVEL_DARK)
            p.frame(x, height / 2f - 2f, 3f, 3f, Palette.BEVEL_LIGHT)
            x -= 5f
        }

        val mode = if (state.autoLevel) "AUTO" else "MANUAL"
        p.text(x - PixelFont.measure(mode) - 2f, 3f, mode, if (state.autoLevel) Palette.GOLD else Palette.PARCHMENT_DIM)
        return (height + 2f).toInt()
    }

    /** The room: brick, torches, flagstone, the duel, a meter under each fighter. */
    private fun drawRoom(p: PixelPainter, hud: Hud, cols: Int, nowMs: Long, top: Int, bottom: Int, biome: Biome) {
        val height = bottom - top
        if (height < MIN_ROOM) return

        val width = cols - MARGIN * 2
        val captionY = top + 4f
        val meterHeight = 5f
        val meterY = bottom - meterHeight - 3f
        val feet = meterY - 3f
        // Same block rule as the bar; here there is room for a 2x or 3x party.
        val members = hud.party.size.coerceAtLeast(1)
        val scale = minOf(
            Formation.scale(members, (width - 20f) * 0.55f, feet - (captionY + TEXT + 4f)),
            MAX_SCALE,
        )
        val side = Sprites.FIGHTER_SIZE * scale
        val floorTop = feet - 2f

        p.bricks(MARGIN, top.toFloat(), width, floorTop - top, biome, courseHeight = 8, brickWidth = 17)
        p.floor(MARGIN, floorTop, width, bottom - floorTop, biome)
        p.frame(MARGIN, top.toFloat(), width, height.toFloat(), Palette.BEVEL_DARK)
        p.torch(MARGIN + width * 0.18f, top + 10f, nowMs)
        p.torch(MARGIN + width * 0.82f, top + 10f, nowMs + 200L)

        val caption = if (hud.isDown) "HERO DEFEATED" else hud.enemyName
        val text = PixelFont.clipWords(caption, (width - 8f).toInt())
        val textWidth = PixelFont.measure(text).toFloat()
        p.fill(cols / 2f - textWidth / 2f - 3f, captionY - 2f, textWidth + 6f, TEXT + 4f, biome.stoneDark)
        p.text(cols / 2f - textWidth / 2f, captionY, text, if (hud.isDown) Palette.HP else Palette.PARCHMENT)
        val roster = hud.party
        for (i in Formation.drawOrder(roster.size)) {
            val member = roster[i]
            val art = Sprites.heroFrame(member.cls, nowMs, member.down || hud.isDown)
            val x = MARGIN + 6f + Formation.offset(i, roster.size, side)
            p.shadow(x + side / 2f, feet, side * 0.72f)
            p.sprite(x, feet - side, art, side / art.size)
        }

        if (!hud.isDown) {
            val enemyArt = Sprites.of(hud.enemySprite)
            val enemyX = cols - MARGIN - 6f - side
            p.shadow(enemyX + side / 2f, feet, side * 0.72f)
            p.sprite(enemyX, feet - side, enemyArt, side / enemyArt.size)
        }

        val meterWidth = (width / 2f - 12f).coerceAtMost(46f)
        p.bar(MARGIN + 6f, meterY, meterWidth, meterHeight, hud.frontHp, Palette.HP, Palette.HP_SOCKET)
        if (!hud.isDown) {
            p.bar(cols - MARGIN - 6f - meterWidth, meterY, meterWidth, meterHeight, hud.enemyHp, Palette.FOE, Palette.FOE_SOCKET)
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
        top: Int,
        bottom: Int,
    ) {
        p.divider(MARGIN, top.toFloat(), cols - MARGIN * 2)
        val row = LINE + 3
        var y = top + GAP

        // Columns measured from the widest strings, so the bar never lands on a level.
        val labelX = MARGIN + FIGHTER + 4f
        val levelX = labelX + HeroClass.entries.maxOf { PixelFont.measure(it.label) } + 4f
        val barX = levelX + PixelFont.measure("LV 999") + 4f
        val barWidth = cols - MARGIN - barX

        for (member in hud.party) {
            if (y + FIGHTER > bottom) return
            p.sprite(MARGIN, y.toFloat(), Sprites.heroFrame(member.cls, 0L, member.down), 1f)
            p.text(labelX, y + 2f, member.cls.label, if (member.down) Palette.HP else Palette.PARCHMENT)
            p.text(levelX, y + 2f, PixelFont.clip(member.level, (barX - levelX - 4f).toInt()), Palette.PARCHMENT_DIM)
            if (barWidth > 8f) p.bar(barX, y + 3f, barWidth, 5f, member.hp, Palette.HP, Palette.HP_SOCKET)
            y += row
        }
        // Locked slots stay visible: knowing a mage is coming is the point.
        for (locked in state.party.drop(state.unlocked)) {
            if (y + FIGHTER > bottom) return
            p.text(labelX, y + 2f, locked.cls.label, Palette.BEVEL_LIGHT)
            p.text(levelX, y + 2f, "ACT ${locked.cls.unlockAct}", Palette.BEVEL_LIGHT)
            y += row
        }

        if (y + FIGHTER > bottom) return
        drawStash(p, state, cols, y.toFloat(), b)
    }

    /**
     * The stash as an inventory grid: one bordered cell per grade, its border the
     * grade's colour, with the count beside it. Two rows, because ten cells in one
     * row leaves no width for the numbers.
     */
    private fun drawStash(p: PixelPainter, state: GameState, cols: Int, y: Float, b: Balance) {
        val perRow = state.stash.size / 2
        val slot = (cols - MARGIN * 2) / perRow
        val cell = 11f
        for ((grade, count) in state.stash.withIndex()) {
            val x = MARGIN + (grade % perRow) * slot
            val row = y + (grade / perRow) * (cell + 3f)
            val color = Palette.grade(grade)
            p.itemCell(x, row, cell, color, filled = count > 0)
            if (count == 0) continue
            // A pile one fusion away wears its grade's colour; the rest stay quiet.
            val ready = count >= b.cubeInput
            p.text(x + cell + 2f, row + 2f, count.toString(), if (ready) color else Palette.PARCHMENT_DIM)
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
        val leftBudget = (rightX - MARGIN - 4f).toInt()
        val rightBudget = (cols - MARGIN - rightX).toInt()

        var y = top + GAP
        for (stat in pairs) {
            if (y + TEXT > bottom) return
            val valueColor = when (stat.label) {
                "GOLD" -> Palette.GOLD
                "GEAR" -> Palette.MANA
                else -> Palette.PARCHMENT
            }
            drawPair(p, MARGIN, y.toFloat(), stat.label, stat.value, leftBudget, valueColor)
            drawPair(p, rightX, y.toFloat(), stat.rightLabel, stat.rightValue, rightBudget, Palette.PARCHMENT)
            y += LINE
        }

        if (y + TEXT <= bottom) {
            val label = "RUNE +${state.runes * 2}%"
            p.text(MARGIN, y.toFloat(), label, Palette.MANA)
            p.bar(MARGIN + PixelFont.measure(label) + 5f, y + 1f, 30f, 5f, hud.runeProgress, Palette.MANA, Palette.MANA_SOCKET)
            y += LINE
        }
        if (y + TEXT <= bottom) {
            p.text(MARGIN, y.toFloat(), "NEXT LV", Palette.PARCHMENT_DIM)
            val x = MARGIN + PixelFont.measure("NEXT LV") + 5f
            p.sprite(x, y + 1f, Sprites.icon(IconKey.COIN))
            p.text(
                x + COIN + 2f,
                y.toFloat(),
                Fmt.short(state.levelCost(b)),
                if (hud.buttonEnabled) Palette.GOLD else Palette.GOLD_DARK,
            )
        }
    }

    /** "GOLD 4.5K": dim label, bright value, clipped as a unit. */
    private fun drawPair(p: PixelPainter, x: Float, y: Float, label: String, value: String, budget: Int, color: Int) {
        p.text(x, y, label, Palette.PARCHMENT_DIM)
        val valueX = x + PixelFont.measure(label) + 4f
        val room = budget - PixelFont.measure(label) - 4
        if (room > 0) p.text(valueX, y, PixelFont.clip(value, room), color)
    }

    /** Combat log, newest last, each line in its event's own colour. */
    private fun drawLog(p: PixelPainter, log: List<Ticker>, cols: Int, top: Int, bottom: Int) {
        if (log.isEmpty()) return
        p.divider(MARGIN, top.toFloat(), cols - MARGIN * 2)
        var y = top + GAP
        val room = ((bottom - y + 1) / LINE).coerceIn(0, MAX_LOG_LINES)
        for (line in log.takeLast(room)) {
            if (y + TEXT > bottom) return
            p.text(MARGIN, y.toFloat(), PixelFont.clipWords(line.text, (cols - MARGIN * 2).toInt()), BarLayout.toneColor(line))
            y += LINE
        }
    }

    private data class Stat(val label: String, val value: String, val rightLabel: String, val rightValue: String)
}
