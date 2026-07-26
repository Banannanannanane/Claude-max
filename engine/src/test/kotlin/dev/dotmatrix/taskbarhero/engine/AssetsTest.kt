package dev.dotmatrix.taskbarhero.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssetsTest {

    @Test
    fun `every glyph is 5 by 7`() {
        for (c in DotFont.supported) {
            val g = DotFont.glyph(c)
            assertEquals(DotFont.HEIGHT, g.size, "glyph '$c' height")
            g.forEach { assertEquals(DotFont.WIDTH, it.length, "glyph '$c' row width") }
        }
    }

    @Test
    fun `the font covers everything the hud can print`() {
        val b = Balance()
        val state = GameState.newRun(0L, b).copy(
            level = 42, runes = 7, gold = 12_400_000.0, act = 5, wave = 10, deepestAct = 6,
        )
        val texts = Hud.of(state, b, GameEvent.BossDown(5, Loot.roll(5, 3))).let {
            listOf(
                it.stage, it.level, it.gold, it.enemyName, it.runes,
                it.deepest, it.buttonLabel, it.buttonCost, it.ticker,
            )
        }
        val known = DotFont.supported
        for (text in texts) {
            for (c in text) {
                assertTrue(c.uppercaseChar() in known, "no glyph for '$c' in \"$text\"")
            }
        }
    }

    @Test
    fun `every boss name and loot string is printable`() {
        val known = DotFont.supported
        for (act in 1..12) {
            val names = buildList {
                add(Bestiary.mob(act, 10, 0).name)
                for (w in 1..9) for (i in 0..3) add(Bestiary.mob(act, w, i).name)
                for (k in 0L..6L) add(Loot.roll(act, k))
            }
            names.flatMap { it.toList() }.forEach {
                assertTrue(it.uppercaseChar() in known, "no glyph for '$it'")
            }
        }
    }

    @Test
    fun `text measurement matches the laid out dots`() {
        assertEquals(0, DotFont.measure(""))
        assertEquals(DotFont.WIDTH, DotFont.measure("A"))
        assertEquals(DotFont.WIDTH * 2 + 1, DotFont.measure("AB"))

        val dots = DotFont.dots("ACT 3-07")
        val widest = dots.maxOf { it.x }
        assertTrue(widest < DotFont.measure("ACT 3-07"), "dots must fit the measured box")
        assertTrue(dots.all { it.y in 0 until DotFont.HEIGHT })
    }

    @Test
    fun `clip trims to the available width`() {
        val long = "GLYPH EATER DOWN / IMMORTAL DOT BLADE"
        val clipped = DotFont.clip(long, 60)
        assertTrue(DotFont.measure(clipped) <= 60)
        assertTrue(long.startsWith(clipped))
        assertEquals("HI", DotFont.clip("HI", 999))
    }

    @Test
    fun `unknown characters fall back instead of crashing`() {
        assertEquals(DotFont.glyph('?'), DotFont.glyph('é'))
    }

    @Test
    fun `every sprite is square and non empty`() {
        for (key in SpriteKey.entries) {
            val art = Sprites.of(key)
            assertTrue(art.size >= 12, "$key is ${art.size} tall")
            art.rows.forEach { assertEquals(art.size, it.length, "$key row width") }
            assertTrue(art.rows.any { row -> row.any { it != '.' } }, "$key is blank")
            art.rows.forEach { row ->
                row.forEach { assertTrue(it in ".#+", "$key has an unexpected cell '$it'") }
            }
        }
    }

    @Test
    fun `hero animation alternates and yields a grave while down`() {
        assertEquals(Sprites.of(SpriteKey.HERO), Sprites.heroFrame(0L))
        assertEquals(Sprites.of(SpriteKey.HERO_SWING), Sprites.heroFrame(500L))
        assertEquals(Sprites.of(SpriteKey.GRAVE), Sprites.heroFrame(0L, downMs = 1L))
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
        assertTrue(Hud.of(s.copy(downUntilMs = 5L), b).ticker.contains("DOWN"))
        assertEquals("LEVEL 9", Hud.of(s, b, GameEvent.LevelUp(9)).ticker)
        assertEquals("AUTO", Hud.of(s.copy(autoLevel = true), b).buttonLabel)
    }
}
