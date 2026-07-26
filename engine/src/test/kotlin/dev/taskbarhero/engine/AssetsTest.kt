package dev.taskbarhero.engine

import dev.taskbarhero.paint.Palette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssetsTest {

    @Test
    fun `every glyph is 5 by 7`() {
        for (c in PixelFont.supported) {
            val g = PixelFont.glyph(c)
            assertEquals(PixelFont.HEIGHT, g.size, "glyph '$c' height")
            g.forEach { assertEquals(PixelFont.WIDTH, it.length, "glyph '$c' row width") }
        }
    }

    @Test
    fun `the font covers everything the hud can print`() {
        val b = Balance()
        val state = GameState.newRun(0L, b).copy(
            runes = 7, gold = 12_400_000.0, act = 5, wave = 10, deepestAct = 6, unlocked = 3,
            stash = List(Loot.GRADES.size) { 9 },
        )
        val texts = Hud.of(state, b, GameEvent.BossDown(5, Loot.roll(5, 3))).let {
            listOf(
                it.stage, it.level, it.gold, it.enemyName, it.enemyShort, it.runes,
                it.deepest, it.buttonLabel, it.buttonCost, it.potionCost,
                it.cubeLabel, it.gearBonus, it.ticker,
            ) + it.party.flatMap { hero -> listOf(hero.cls.label, hero.level) }
        }
        val known = PixelFont.supported
        for (text in texts) {
            for (c in text) {
                assertTrue(c.uppercaseChar() in known, "no glyph for '$c' in \"$text\"")
            }
        }
    }

    @Test
    fun `every boss name and loot string is printable`() {
        val known = PixelFont.supported
        for (act in 1..12) {
            val names = buildList {
                add(Bestiary.mob(act, 10, 0).name)
                for (w in 1..9) for (i in 0..3) add(Bestiary.mob(act, w, i).name)
                for (k in 0L..6L) add(Loot.roll(act, k).label)
            }
            names.flatMap { it.toList() }.forEach {
                assertTrue(it.uppercaseChar() in known, "no glyph for '$it'")
            }
        }
    }

    @Test
    fun `text measurement matches the laid out dots`() {
        assertEquals(0, PixelFont.measure(""))
        assertEquals(PixelFont.WIDTH, PixelFont.measure("A"))
        assertEquals(PixelFont.WIDTH * 2 + 1, PixelFont.measure("AB"))

        val cells = PixelFont.cells("ACT 3-07")
        val widest = cells.maxOf { it.x }
        assertTrue(widest < PixelFont.measure("ACT 3-07"), "cells must fit the measured box")
        assertTrue(cells.all { it.y in 0 until PixelFont.HEIGHT })
    }

    @Test
    fun `clip trims to the available width`() {
        val long = "GLYPH EATER DOWN / IMMORTAL DOT BLADE"
        val clipped = PixelFont.clip(long, 60)
        assertTrue(PixelFont.measure(clipped) <= 60)
        assertTrue(long.startsWith(clipped))
        assertEquals("HI", PixelFont.clip("HI", 999))
    }

    @Test
    fun `unknown characters fall back instead of crashing`() {
        assertEquals(PixelFont.glyph('?'), PixelFont.glyph('é'))
    }

    @Test
    fun `every sprite is square, painted and fully coloured`() {
        for (key in SpriteKey.entries) {
            assertPixelArt(key.name, Sprites.of(key), minSize = 12)
        }
        for (key in IconKey.entries) {
            assertPixelArt(key.name, Sprites.icon(key), minSize = 5)
        }
    }

    private fun assertPixelArt(name: String, art: Sprites.Art, minSize: Int) {
        assertTrue(art.size >= minSize, "$name is ${art.size} tall")
        art.rows.forEach { assertEquals(art.size, it.length, "$name row width: '$it'") }
        assertTrue(art.rows.any { row -> row.any { it != '.' } }, "$name is blank")
        // Every index must resolve, or the sprite would throw while being drawn.
        for (y in 0 until art.size) {
            for (x in 0 until art.size) {
                val transparent = art.rows[y][x] == '.'
                assertEquals(transparent, art.colorAt(x, y) == null, "$name cell ($x,$y)")
            }
        }
        val outlined = art.rows.any { row -> row.any { it == '1' } }
        assertTrue(outlined, "$name has no outline pixels")
    }

    @Test
    fun `the cube button reports progress before it can fuse`() {
        val b = Balance()
        val empty = GameState.newRun(0L, b)
        assertEquals("0/${b.cubeInput}", Hud.of(empty, b).cubeLabel)
        assertEquals(null, Hud.of(empty, b).cubeGrade, "nothing to fuse yet")

        val partway = empty.copy(stash = empty.stash.toMutableList().also { it[1] = 6 })
        assertEquals("6/${b.cubeInput}", Hud.of(partway, b).cubeLabel)
        assertEquals(null, Hud.of(partway, b).cubeGrade)

        val ready = empty.copy(stash = empty.stash.toMutableList().also { it[1] = b.cubeInput })
        assertEquals(2, Hud.of(ready, b).cubeGrade, "fusing grade 1 yields grade 2")
        assertTrue(Hud.of(ready, b).cubeEnabled)
    }

    @Test
    fun `loot grades climb with the act and stay inside the ramp`() {
        assertEquals(0, Loot.roll(1, 0).grade)
        assertEquals("COMMON", Loot.GRADES[Loot.roll(1, 0).grade])
        assertEquals("COSMIC", Loot.GRADES[Loot.roll(30, 0).grade])
        for (act in 1..30) {
            val drop = Loot.roll(act, act.toLong())
            assertTrue(drop.grade in Loot.GRADES.indices)
            assertEquals(Palette.grade(drop.grade), Palette.GRADES[drop.grade])
            assertTrue(drop.label.startsWith(Loot.GRADES[drop.grade]))
        }
    }

    @Test
    fun `every event carries a tone, and loot carries its grade`() {
        assertEquals(Tone.GOOD, GameEvent.LevelUp(3).tone)
        assertEquals(Tone.BAD, GameEvent.HeroDown(2, 4).tone)
        assertEquals(Tone.MAGIC, GameEvent.RuneGained(5).tone)
        val boss = GameEvent.BossDown(6, Loot.roll(6, 0))
        assertEquals(Tone.LOOT, boss.tone)
        assertEquals(boss.drop.grade, boss.toTicker().grade)
        assertEquals(boss.caption, boss.toTicker().text)
    }

    @Test
    fun `the knight swings, the others hold still, and the fallen get a grave`() {
        assertEquals(Sprites.of(SpriteKey.KNIGHT), Sprites.heroFrame(HeroClass.KNIGHT, 0L))
        assertEquals(Sprites.of(SpriteKey.KNIGHT_SWING), Sprites.heroFrame(HeroClass.KNIGHT, 500L))
        assertEquals(Sprites.of(SpriteKey.GRAVE), Sprites.heroFrame(HeroClass.KNIGHT, 0L, down = true))
        // The other classes have one frame, so their sprite must not depend on time.
        for (cls in listOf(HeroClass.RANGER, HeroClass.MAGE)) {
            assertEquals(Sprites.of(cls.sprite), Sprites.heroFrame(cls, 0L))
            assertEquals(Sprites.of(cls.sprite), Sprites.heroFrame(cls, 500L))
        }
    }

    @Test
    fun `every class has its own sprite and joins at its own act`() {
        val sprites = HeroClass.entries.map { it.sprite }
        assertEquals(sprites.size, sprites.toSet().size, "two classes share a sprite")
        assertEquals(1, HeroClass.KNIGHT.unlockAct, "someone has to start the run")
        val acts = HeroClass.entries.map { it.unlockAct }
        assertEquals(acts.sorted(), acts, "recruits should arrive in roster order")
    }

    @Test
    fun `boss waves always show the boss sprite`() {
        for (act in 1..9) {
            assertEquals(SpriteKey.BOSS, Bestiary.mob(act, 10, 0).sprite)
            assertFalse(Bestiary.mob(act, 9, 0).sprite == SpriteKey.BOSS)
        }
    }

    @Test
    fun `mob choice is stable for a given slot`() {
        repeat(3) {
            assertEquals(Bestiary.mob(3, 4, 2), Bestiary.mob(3, 4, 2))
        }
        val variety = (1..9).flatMap { w -> (0..3).map { Bestiary.mob(5, w, it).name } }.toSet()
        assertTrue(variety.size > 1, "act 5 should not be a single monster")
    }

    @Test
    fun `short numbers read like an idle game`() {
        assertEquals("0", Fmt.short(0.0))
        assertEquals("942", Fmt.short(942.7))
        assertEquals("1.0K", Fmt.short(1_000.0))
        assertEquals("12.4K", Fmt.short(12_499.0))
        assertEquals("999K", Fmt.short(999_400.0))
        assertEquals("3.0M", Fmt.short(3_000_000.0))
        assertEquals("1.0AA", Fmt.short(1e15))
    }

    @Test
    fun `durations and percents are compact`() {
        assertEquals("0S", Fmt.duration(0L))
        assertEquals("35S", Fmt.duration(35_000L))
        assertEquals("1M 35S", Fmt.duration(95_000L))
        assertEquals("2H 20M", Fmt.duration(8_400_000L))
        assertEquals("0%", Fmt.percent(0.0))
        assertEquals("100%", Fmt.percent(1.4))
        assertEquals("50%", Fmt.percent(0.5))
    }

    @Test
    fun `hud switches its ticker between auto idle down and events`() {
        val b = Balance()
        val s = GameState.newRun(0L, b)
        assertTrue(Hud.of(s, b).ticker.startsWith("ACT 1-01"))
        assertEquals("AUTO / IDLING", Hud.of(s.copy(autoLevel = true), b).ticker)
        assertTrue(Hud.of(s.copy(downUntilMs = 5L), b).ticker.contains("WIPED"))
        assertEquals("PARTY LV 9", Hud.of(s, b, GameEvent.LevelUp(9)).ticker)
        assertEquals("AUTO", Hud.of(s.copy(autoLevel = true), b).buttonLabel)
    }
}
