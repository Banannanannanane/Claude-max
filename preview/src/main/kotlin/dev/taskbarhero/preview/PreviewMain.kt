package dev.taskbarhero.preview

import dev.taskbarhero.assets.Art
import dev.taskbarhero.assets.AssetManifest
import dev.taskbarhero.assets.Atlas
import dev.taskbarhero.game.Balance
import dev.taskbarhero.game.GameEvent
import dev.taskbarhero.game.GameState
import dev.taskbarhero.game.IdleEngine
import dev.taskbarhero.game.Loot
import dev.taskbarhero.game.Ticker
import dev.taskbarhero.game.Tone
import dev.taskbarhero.game.toTicker
import dev.taskbarhero.paint.BarLayout
import dev.taskbarhero.paint.Painter
import dev.taskbarhero.paint.Palette
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private const val DENSITY = 3f
private const val T0 = 1_700_000_000_000L
private val B = Balance()

/**
 * Renders every widget size the launcher can hand out, to PNG.
 *
 * The point is not documentation: it is that a layout bug — a bar over a face, a
 * word clipped mid-syllable, a sprite standing in the wall — is invisible in code
 * review and obvious in a picture.
 */
fun main(args: Array<String>) {
    val outDir = File(args.firstOrNull() ?: "docs/preview").apply { mkdirs() }
    val art = Art3(File(args.getOrNull(1) ?: "app/src/main/assets"))

    val shots = listOf(
        Shot("bar-4x1", 4, 1, state("fresh"), null),
        Shot("bar-4x1-boss", 4, 1, state("boss"), null),
        Shot("bar-4x2", 4, 2, state("mid"), null),
        Shot("bar-4x2-loot", 4, 2, state("boss"), GameEvent.BossDown(4, Loot.roll(4, 2)).toTicker()),
        Shot("bar-4x2-down", 4, 2, state("down"), null),
        Shot("bar-5x2", 5, 2, state("deep"), Ticker("LEVEL 240", Tone.GOOD)),
        Shot("bar-4x3", 4, 3, state("deep"), null),
    )

    for (shot in shots) ImageIO.write(render(shot, art), "png", File(outDir, "${shot.name}.png"))
    ImageIO.write(contactSheet(shots, art), "png", File(outDir, "sheet.png"))
    ImageIO.write(flipbook(shots.first { it.name == "bar-4x2" }, art), "png", File(outDir, "anim.png"))

    println("wrote ${shots.size + 2} previews to ${outDir.absolutePath}")
    println("art: ${art.dungeon.size} dungeon frames on a ${art.dungeon.unit}px grid, ${art.ui.size} ui pieces")
    val holes = Art.missingFrom(art.dungeon, Art.dungeonKeys) + Art.missingFrom(art.ui, Art.uiKeys)
    if (holes.isNotEmpty()) println("MISSING: ${holes.joinToString()}")
}

/** The three files the app loads, loaded the same way. */
private class Art3(dir: File) {
    val dungeon: Atlas
    val ui: Atlas
    val font: Font
    val dungeonImage: BufferedImage
    val uiImage: BufferedImage

    init {
        fun read(path: String) = File(dir, path).also {
            require(it.isFile) { "missing $path — the app would refuse to start" }
        }
        dungeon = Atlas.parse(read("art/dungeon.txt").readText()).also { report(it, "dungeon.txt") }.atlas
        ui = Atlas.parse(read("art/ui.txt").readText()).also { report(it, "ui.txt") }.atlas
        dungeonImage = ImageIO.read(read(AssetManifest.DUNGEON))
        uiImage = ImageIO.read(read(AssetManifest.UI))
        font = Font.createFont(Font.TRUETYPE_FONT, read(AssetManifest.FONT))
    }

    private fun report(result: Atlas.Result, name: String) {
        result.problems.forEach { println("$name: $it") }
    }
}

private data class Shot(
    val name: String,
    val cellsWide: Int,
    val cellsTall: Int,
    val state: GameState,
    val event: Ticker?,
) {
    /** Roughly what a launcher cell measures out to, in dp. */
    val widthDp: Int get() = cellsWide * 72 - 12
    val heightDp: Int get() = cellsTall * 78 - 8
}

private fun render(shot: Shot, art: Art3, nowMs: Long = T0): BufferedImage {
    val w = (shot.widthDp * DENSITY).toInt()
    val h = (shot.heightDp * DENSITY).toInt()
    val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    val surface = AwtSurface(g, art.dungeonImage, art.uiImage, art.font)
    BarLayout.draw(
        Painter(surface, art.dungeon, art.ui),
        w.toFloat(), h.toFloat(), DENSITY,
        shot.state, nowMs, shot.event, B,
    )
    g.dispose()
    return image
}

/** Every size on one page, labelled. */
private fun contactSheet(shots: List<Shot>, art: Art3): BufferedImage {
    val bars = shots.map { it to render(it, art) }
    val margin = 40
    val label = 40
    val width = bars.maxOf { it.second.width } + margin * 2
    val height = margin + bars.sumOf { it.second.height + label + margin }

    val sheet = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = sheet.createGraphics()
    g.color = java.awt.Color(Palette.VOID, true)
    g.fillRect(0, 0, width, height)
    val surface = AwtSurface(g, art.dungeonImage, art.uiImage, art.font)

    var y = margin
    for ((shot, bar) in bars) {
        surface.text(margin.toFloat(), y.toFloat(), shot.name.uppercase(), 32f, Palette.PARCHMENT_DIM)
        y += label
        g.drawImage(bar, margin, y, null)
        y += bar.height + margin
    }
    g.dispose()
    return sheet
}

/** The same bar at four consecutive beats — a still cannot show a lunge. */
private fun flipbook(shot: Shot, art: Art3): BufferedImage {
    val beats = (0..3).map { render(shot, art, T0 + it * 500L) }
    val gap = 20
    val width = beats.maxOf { it.width } + gap * 2
    val height = gap + beats.sumOf { it.height + gap }
    val sheet = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = sheet.createGraphics()
    g.color = java.awt.Color(Palette.VOID, true)
    g.fillRect(0, 0, width, height)
    var y = gap
    for (beat in beats) {
        g.drawImage(beat, gap, y, null)
        y += beat.height + gap
    }
    g.dispose()
    return sheet
}

private fun state(kind: String): GameState {
    val fresh = GameState.newRun(T0, B)
    return when (kind) {
        "fresh" -> fresh.copy(gold = 12.0, enemyHp = B.enemyMaxHp(1, 1) * 0.55)

        "boss" -> fresh.copy(
            act = 4, wave = 10, unlocked = 2, gold = 4_180.0, kills = 806, bossKills = 3,
            deepestAct = 4, deepestWave = 10,
            enemyHp = B.enemyMaxHp(4, 10) * 0.42,
            stash = grades(3 to 6, 1 to 2),
        ).levelled(28, 24).hurt(0.34)

        "down" -> fresh.copy(
            act = 5, wave = 1, unlocked = 3, gold = 900.0, wipes = 3,
            downUntilMs = T0 + 2_500L, deepestAct = 5, deepestWave = 7,
        ).levelled(31, 29, 22)

        "deep" -> fresh.copy(
            act = 12, wave = 9, unlocked = 3, gold = 1.42e12, kills = 986_400,
            bossKills = 71, wipes = 34, deepestAct = 12, deepestWave = 10,
            enemyHp = B.enemyMaxHp(12, 9) * 0.62,
            stash = grades(9 to 3, 7 to 9, 5 to 4, 2 to 1),
        ).levelled(240, 236, 231).hurt(0.91)

        // A real half-hour of idling, so the numbers are the ones the engine makes.
        else -> IdleEngine.advance(fresh.copy(autoLevel = true), T0 + 1_800_000L, B).state.hurt(0.42)
    }
}

private fun GameState.levelled(vararg levels: Int): GameState = copy(
    party = party.mapIndexed { i, hero ->
        val level = levels.getOrElse(i) { hero.level }
        hero.copy(level = level, hp = B.heroMaxHp(hero.cls, level))
    },
)

private fun GameState.hurt(front: Double): GameState = copy(
    party = party.mapIndexed { i, hero -> if (i == 0) hero.copy(hp = hero.maxHp(B) * front) else hero },
)

private fun grades(vararg counts: Pair<Int, Int>): List<Int> {
    val stash = MutableList(Loot.GRADES.size) { 0 }
    for ((grade, count) in counts) stash[grade] = count
    return stash
}
