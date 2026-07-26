package dev.taskbarhero.preview

import dev.taskbarhero.engine.Balance
import dev.taskbarhero.engine.GameEvent
import dev.taskbarhero.engine.GameState
import dev.taskbarhero.engine.IdleEngine
import dev.taskbarhero.engine.Loot
import dev.taskbarhero.engine.Ticker
import dev.taskbarhero.engine.Tone
import dev.taskbarhero.paint.BarLayout
import dev.taskbarhero.paint.DungeonLayout
import dev.taskbarhero.paint.Palette
import dev.taskbarhero.paint.PixelPainter
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders the shipped layouts to PNG so the design can be reviewed — and diffed —
 * without an emulator. The only thing that differs from the phone is the surface
 * underneath; every dot position comes from the same code the widget runs.
 */
private const val DENSITY = 3f
private val BALANCE = Balance()
private const val T0 = 1_700_000_000_000L

fun main(args: Array<String>) {
    val outDir = File(args.firstOrNull() ?: "build/preview").apply { mkdirs() }

    val frames = listOf(
        Frame("bar-4x1-idle", 4, 1, state("fresh"), null),
        Frame("bar-4x1-ready", 4, 1, state("ready"), null),
        Frame("bar-4x1-boss", 4, 1, state("boss"), GameEvent.BossDown(3, Loot.roll(3, 2)).toTicker()),
        Frame("bar-4x1-down", 4, 1, state("down"), null),
        Frame("bar-4x1-deep", 4, 1, state("deep"), Ticker(Loot.roll(10, 1).label, Tone.LOOT, 9)),
        Frame("bar-4x2", 4, 2, state("idled"), null),
        Frame("bar-5x2", 5, 2, state("deep"), Ticker("LEVEL 240", Tone.GOOD)),
    )

    for (frame in frames) {
        val image = renderBar(frame)
        ImageIO.write(image, "png", File(outDir, "${frame.name}.png"))
    }
    ImageIO.write(renderDungeon(state("idled")), "png", File(outDir, "dungeon.png"))
    ImageIO.write(renderSheet(frames), "png", File(outDir, "sheet.png"))

    println("wrote ${frames.size + 2} previews to ${outDir.absolutePath}")
}

private data class Frame(
    val name: String,
    val cellsWide: Int,
    val cellsTall: Int,
    val state: GameState,
    val event: Ticker?,
) {
    /** Roughly what one home-screen cell measures out to, in dp. */
    val widthDp: Int get() = cellsWide * 72 - 12
    val heightDp: Int get() = cellsTall * 78 - 8
}

private fun renderBar(frame: Frame): BufferedImage {
    val w = (frame.widthDp * DENSITY).toInt()
    val h = (frame.heightDp * DENSITY).toInt()
    val unit = BarLayout.unitPx(h.toFloat(), DENSITY)
    val rows = BarLayout.rowsFor(h.toFloat(), DENSITY)
    val cols = (w / unit).toInt()

    val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    BarLayout.draw(PixelPainter(AwtSurface(g), unit), cols, rows, frame.state, T0, frame.event, BALANCE)
    g.dispose()
    return image
}

private fun renderDungeon(state: GameState): BufferedImage {
    val w = 1080
    val h = 1900
    val unit = w.toFloat() / DungeonLayout.TARGET_COLS
    val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.color = java.awt.Color(Palette.STONE_DARK, true)
    g.fillRect(0, 0, w, h)
    DungeonLayout.draw(
        p = PixelPainter(AwtSurface(g), unit),
        cols = DungeonLayout.TARGET_COLS,
        rows = (h / unit).toInt(),
        state = state,
        log = listOf(
            GameEvent.RuneGained(14).toTicker(),
            GameEvent.LevelUp(63).toTicker(),
            GameEvent.BossDown(4, Loot.roll(4, 1)).toTicker(),
            GameEvent.ActCleared(4).toTicker(),
            GameEvent.HeroDown(5, 8).toTicker(),
        ),
        nowMs = T0,
        b = BALANCE,
    )
    g.dispose()
    return image
}

/** One contact sheet with every bar state, labelled in the same dot font. */
private fun renderSheet(frames: List<Frame>): BufferedImage {
    val bars = frames.map { it to renderBar(it) }
    val margin = 48
    val labelHeight = 44
    val width = bars.maxOf { it.second.width } + margin * 2
    val height = margin + bars.sumOf { it.second.height + labelHeight + margin }

    val sheet = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = sheet.createGraphics()
    g.color = java.awt.Color(Palette.VOID, true)
    g.fillRect(0, 0, width, height)

    var y = margin
    for ((frame, bar) in bars) {
        val painter = PixelPainter(AwtSurface(g), 3.4f)
        val label = frame.name.removePrefix("bar-").replace('-', ' ').uppercase()
        // Dot-font labels, drawn with a translated surface so the painter keeps
        // working in its own dot space.
        g.translate(margin, y)
        painter.text(0f, 0f, label, Palette.PARCHMENT_DIM)
        g.translate(-margin, -y)
        y += labelHeight

        g.drawImage(bar, margin, y, null)
        y += bar.height + margin
    }
    g.dispose()
    return sheet
}

private fun state(kind: String): GameState {
    val fresh = GameState.newRun(T0, BALANCE)
    return when (kind) {
        "fresh" -> fresh.copy(gold = 12.0, heroHp = BALANCE.heroMaxHp(1) * 0.82, enemyHp = BALANCE.enemyMaxHp(1, 1) * 0.4)
        "ready" -> fresh.copy(
            level = 14, gold = 640.0, act = 2, wave = 4, runes = 6,
            heroHp = BALANCE.heroMaxHp(14) * 0.63, enemyHp = BALANCE.enemyMaxHp(2, 4) * 0.71, kills = 214L,
        )
        "boss" -> fresh.copy(
            level = 28, gold = 4_180.0, act = 3, wave = 10, runes = 11,
            heroHp = BALANCE.heroMaxHp(28) * 0.34, enemyHp = BALANCE.enemyMaxHp(3, 10) * 0.18,
            kills = 806L, bossKills = 2L, deepestAct = 3, deepestWave = 10,
        )
        // A wipe revives the hero at full health, then holds it out of combat.
        "down" -> fresh.copy(
            level = 31, gold = 900.0, act = 5, wave = 1, deaths = 3L,
            heroHp = BALANCE.heroMaxHp(31), downUntilMs = T0 + 2_500L,
            deepestAct = 5, deepestWave = 7,
        )
        "deep" -> fresh.copy(
            level = 240, runes = 48, gold = 1.42e12, act = 12, wave = 9,
            heroHp = BALANCE.heroMaxHp(240) * 0.91, enemyHp = BALANCE.enemyMaxHp(12, 9) * 0.55,
            kills = 986_400L, bossKills = 71L, deaths = 34L, deepestAct = 12, deepestWave = 10,
        )
        // A real half-hour of idling, so the numbers are the ones the engine produces.
        "idled" -> IdleEngine.advance(fresh.copy(autoLevel = true), T0 + 1_800_000L, BALANCE).state
        else -> fresh
    }
}
