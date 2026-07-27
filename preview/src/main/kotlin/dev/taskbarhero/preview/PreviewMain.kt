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
    val art = DropInArt.load(args.getOrNull(1)?.let(::File))

    val frames = listOf(
        Frame("bar-4x1-idle", 4, 1, state("fresh"), null),
        Frame("bar-4x1-ready", 4, 1, state("ready"), null),
        Frame("bar-4x1-boss", 4, 1, state("boss"), GameEvent.BossDown(3, Loot.roll(3, 2)).toTicker()),
        Frame("bar-4x1-down", 4, 1, state("down"), null),
        Frame("bar-4x1-deep", 4, 1, state("deep"), Ticker(Loot.roll(10, 1).label, Tone.LOOT, 9)),
        Frame("bar-4x2", 4, 2, state("idled"), null),
        Frame("bar-5x2", 5, 2, state("deep"), Ticker("LEVEL 240", Tone.GOOD)),
        // Three rows: tall enough that the info lines earn their place.
        Frame("bar-4x3", 4, 3, state("boss"), null),
    )

    for (frame in frames) {
        val image = renderBar(frame, art)
        ImageIO.write(image, "png", File(outDir, "${frame.name}.png"))
    }
    ImageIO.write(renderDungeon(state("idled"), art), "png", File(outDir, "dungeon.png"))
    ImageIO.write(renderSheet(frames, art), "png", File(outDir, "sheet.png"))

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

private fun renderBar(frame: Frame, art: DropInArt?): BufferedImage {
    val w = (frame.widthDp * DENSITY).toInt()
    val h = (frame.heightDp * DENSITY).toInt()
    val unit = BarLayout.unitPx(h.toFloat(), DENSITY)
    val rows = BarLayout.rowsFor(h.toFloat(), DENSITY)
    val cols = (w / unit).toInt()

    val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    BarLayout.draw(
        PixelPainter(AwtSurface(g, art?.image), unit, sheet = art?.sheet),
        cols,
        rows,
        frame.state,
        T0,
        frame.event,
        BALANCE,
        BarLayout.deckRows(unit, DENSITY),
    )
    g.dispose()
    return image
}

private fun renderDungeon(state: GameState, art: DropInArt?): BufferedImage {
    val w = 1080
    val h = 1900
    val unit = w.toFloat() / DungeonLayout.TARGET_COLS
    val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.color = java.awt.Color(Palette.STONE_DARK, true)
    g.fillRect(0, 0, w, h)
    DungeonLayout.draw(
        p = PixelPainter(AwtSurface(g, art?.image), unit, sheet = art?.sheet),
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
private fun renderSheet(frames: List<Frame>, art: DropInArt?): BufferedImage {
    val bars = frames.map { it to renderBar(it, art) }
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
        "fresh" -> fresh.copy(gold = 12.0, enemyHp = BALANCE.enemyMaxHp(1, 1) * 0.4)
            .hurt(front = 0.82)

        "ready" -> fresh.copy(
            gold = 640.0, act = 2, wave = 4, runes = 6, kills = 214L, unlocked = 2,
            enemyHp = BALANCE.enemyMaxHp(2, 4) * 0.71,
        ).levelled(14, 11).hurt(front = 0.63)

        "boss" -> fresh.copy(
            gold = 4_180.0, act = 3, wave = 10, runes = 11, unlocked = 2,
            kills = 806L, bossKills = 2L, deepestAct = 3, deepestWave = 10,
            enemyHp = BALANCE.enemyMaxHp(3, 10) * 0.18,
            stash = grades(4 to 6, 1 to 2),
        ).levelled(28, 24).hurt(front = 0.34)

        // A wipe revives the party at full health, then holds it out of combat.
        "down" -> fresh.copy(
            gold = 900.0, act = 5, wave = 1, deaths = 3L, unlocked = 3,
            downUntilMs = T0 + 2_500L, deepestAct = 5, deepestWave = 7,
        ).levelled(31, 29, 22)

        "deep" -> fresh.copy(
            runes = 48, gold = 1.42e12, act = 12, wave = 9, unlocked = 3,
            kills = 986_400L, bossKills = 71L, deaths = 34L, deepestAct = 12, deepestWave = 10,
            enemyHp = BALANCE.enemyMaxHp(12, 9) * 0.55,
            stash = grades(9 to 3, 7 to 9, 5 to 4, 2 to 1),
        ).levelled(240, 236, 231).hurt(front = 0.91)

        // A real half-hour of idling, so the numbers are the ones the engine produces.
        "idled" -> IdleEngine.advance(fresh.copy(autoLevel = true), T0 + 1_800_000L, BALANCE).state
            .hurt(front = 0.42)

        else -> fresh
    }
}

/** Sets the party's levels, refilling each hero for the new pool. */
private fun GameState.levelled(vararg levels: Int): GameState = copy(
    party = party.mapIndexed { i, hero ->
        val level = levels.getOrElse(i) { hero.level }
        hero.copy(level = level, hp = BALANCE.heroMaxHp(hero.cls, level))
    },
)

/** Knocks the front hero down to a fraction of health, so the bar shows a fight. */
private fun GameState.hurt(front: Double): GameState = copy(
    party = party.mapIndexed { i, hero ->
        if (i == 0) hero.copy(hp = hero.maxHp(BALANCE) * front) else hero
    },
)

private fun grades(vararg counts: Pair<Int, Int>): List<Int> {
    val stash = MutableList(Loot.GRADES.size) { 0 }
    for ((grade, count) in counts) stash[grade] = count
    return stash
}
