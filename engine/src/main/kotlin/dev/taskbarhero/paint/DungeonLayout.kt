package dev.taskbarhero.paint

import dev.taskbarhero.engine.Balance
import dev.taskbarhero.engine.Fmt
import dev.taskbarhero.engine.GameState
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

    /** Four stat pairs, the rune bar, and the "next level" price. */
    private const val STAT_ROWS = 6

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

        p.text(MARGIN, 2f, "TASKBAR HERO", Palette.PARCHMENT)
        val mode = if (state.autoLevel) "AUTO" else "MANUAL"
        p.text(cols - MARGIN - PixelFont.measure(mode), 2f, mode, if (state.autoLevel) Palette.GOLD else Palette.PARCHMENT_DIM)
        val headerBottom = 2 + TEXT + 3
        p.divider(MARGIN, headerBottom.toFloat(), cols - MARGIN * 2)

        // Reserve the bottom blocks first; the room lives on what remains.
        val logHeight = if (log.isEmpty()) 0 else GAP + minOf(log.size, MAX_LOG_LINES) * LINE
        val statsHeight = GAP + STAT_ROWS * LINE
        val roomTop = headerBottom + 3

        var roomBottom = bottom - logHeight - statsHeight - GAP
        var showLog = logHeight > 0
        var showStats = true
        if (roomBottom - roomTop < MIN_ROOM) {
            roomBottom += logHeight
            showLog = false
        }
        if (roomBottom - roomTop < MIN_ROOM) {
            roomBottom += statsHeight
            showStats = false
        }
        roomBottom = roomBottom.coerceAtMost(bottom)

        drawRoom(p, hud, cols, nowMs, top = roomTop, bottom = roomBottom)

        var y = roomBottom + GAP
        if (showStats) {
            drawStats(p, hud, state, cols, b, top = y, bottom = bottom)
            y += statsHeight
        }
        if (showLog) drawLog(p, log, cols, top = y, bottom = bottom)
    }

    /** The room: brick, torches, flagstone, the duel, a meter under each fighter. */
    private fun drawRoom(p: PixelPainter, hud: Hud, cols: Int, nowMs: Long, top: Int, bottom: Int) {
        val height = bottom - top
        if (height < MIN_ROOM) return

        val width = cols - MARGIN * 2
        val captionY = top + 4f
        val meterHeight = 5f
        val meterY = bottom - meterHeight - 3f
        val feet = meterY - 3f
        val slot = Sprites.FIGHTER_SIZE
        // Integer scale only, same rule as the bar — here there is room for 3x or 4x.
        val scale = minOf((feet - (captionY + TEXT + 4f)) / slot, (width - 24f) / 2f / slot, MAX_SCALE)
            .toInt().coerceAtLeast(1).toFloat()
        val side = slot * scale
        val floorTop = feet - 2f

        p.bricks(MARGIN, top.toFloat(), width, floorTop - top, courseHeight = 8, brickWidth = 17)
        p.floor(MARGIN, floorTop, width, bottom - floorTop)
        p.frame(MARGIN, top.toFloat(), width, height.toFloat(), Palette.BEVEL_DARK)
        p.torch(MARGIN + width * 0.18f, top + 10f, nowMs)
        p.torch(MARGIN + width * 0.82f, top + 10f, nowMs + 200L)

        val caption = if (hud.isDown) "HERO DEFEATED" else hud.enemyName
        val text = PixelFont.clipWords(caption, (width - 8f).toInt())
        val textWidth = PixelFont.measure(text).toFloat()
        p.fill(cols / 2f - textWidth / 2f - 3f, captionY - 2f, textWidth + 6f, TEXT + 4f, Palette.STONE_DARK)
        p.text(cols / 2f - textWidth / 2f, captionY, text, if (hud.isDown) Palette.HP else Palette.PARCHMENT)
        val heroArt = Sprites.heroFrame(nowMs, if (hud.isDown) 1L else 0L)
        val enemyArt = Sprites.of(hud.enemySprite)

        val heroX = MARGIN + 6f
        p.shadow(heroX + side / 2f, feet, side * 0.72f)
        p.sprite(heroX, feet - side, heroArt, side / heroArt.size)

        if (!hud.isDown) {
            val enemyX = cols - MARGIN - 6f - side
            p.shadow(enemyX + side / 2f, feet, side * 0.72f)
            p.sprite(enemyX, feet - side, enemyArt, side / enemyArt.size)
        }

        val meterWidth = (width / 2f - 12f).coerceAtMost(46f)
        p.bar(MARGIN + 6f, meterY, meterWidth, meterHeight, hud.heroHp, Palette.HP, Palette.HP_SOCKET)
        if (!hud.isDown) {
            p.bar(cols - MARGIN - 6f - meterWidth, meterY, meterWidth, meterHeight, hud.enemyHp, Palette.FOE, Palette.FOE_SOCKET)
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
            Stat("LEVEL", state.level.toString(), "SLAIN", Fmt.short(state.kills.toDouble())),
            Stat("GOLD", hud.gold, "BOSSES", state.bossKills.toString()),
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
            val valueColor = if (stat.label == "GOLD") Palette.GOLD else Palette.PARCHMENT
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
                Fmt.short(b.levelCost(state.level)),
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
