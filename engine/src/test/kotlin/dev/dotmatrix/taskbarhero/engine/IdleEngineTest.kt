package dev.dotmatrix.taskbarhero.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdleEngineTest {

    private val b = Balance()
    private val t0 = 1_700_000_000_000L

    @Test
    fun `first advance only anchors the clock`() {
        val fresh = GameState(heroHp = b.heroMaxHp(1), enemyHp = b.enemyMaxHp(1, 1))
        val r = IdleEngine.advance(fresh, t0, b)
        assertEquals(t0, r.state.lastTickMs)
        assertEquals(0L, r.state.kills)
        assertTrue(r.events.isEmpty())
    }

    @Test
    fun `chunked simulation equals one shot`() {
        val start = GameState.newRun(t0, b)
        val oneShot = IdleEngine.advance(start, t0 + 600_000L, b).state

        // Deliberately ragged chunks, so tick remainders have to carry over.
        var chunked = start
        for (offset in listOf(37L, 999L, 12_345L, 60_000L, 120_001L, 400_000L, 600_000L)) {
            chunked = IdleEngine.advance(chunked, t0 + offset, b).state
        }

        assertEquals(oneShot, chunked)
    }

    @Test
    fun `ten minutes of idling produces kills gold and xp`() {
        val r = IdleEngine.advance(GameState.newRun(t0, b), t0 + 600_000L, b)
        assertTrue(r.killsGained > 10L, "expected progress, got ${r.killsGained} kills")
        assertTrue(r.goldGained > 0.0)
        assertTrue(r.xpGained > 0.0)
        assertTrue(r.state.runes > 0, "xp should have bought at least one rune")
        assertEquals(r.state.kills, r.killsGained)
    }

    @Test
    fun `offline progress is capped and reports the skipped span`() {
        val week = 7L * 24L * 3_600_000L
        val r = IdleEngine.advance(GameState.newRun(t0, b), t0 + week, b)
        assertEquals(b.maxCatchUpMs, r.simulatedMs)
        assertEquals(week - b.maxCatchUpMs, r.skippedMs)
        // The clock still lands on "now", so the next tick does not re-earn the gap.
        assertTrue(t0 + week - r.state.lastTickMs < b.tickMs)
    }

    @Test
    fun `a long idle run clears acts and kills bosses`() {
        val r = IdleEngine.advance(
            GameState.newRun(t0, b).copy(autoLevel = true),
            t0 + 3_600_000L,
            b,
        )
        assertTrue(r.state.act > 1, "auto-levelling should clear act 1 within an hour")
        assertTrue(r.state.bossKills > 0L)
        assertTrue(r.events.size <= IdleEngine.MAX_EVENTS)
        assertTrue(r.events.any { it is GameEvent.ActCleared })
    }

    @Test
    fun `manual level up spends gold and cannot go into debt`() {
        val poor = GameState.newRun(t0, b)
        assertNull(IdleEngine.levelUp(poor, b))

        val rich = poor.copy(gold = 1_000.0)
        val up = assertNotNull(IdleEngine.levelUp(rich, b))
        assertEquals(2, up.level)
        assertEquals(1_000.0 - b.levelCost(1), up.gold, 1e-9)
        assertTrue(up.heroHp > rich.heroHp, "a new level should grant the extra health")
        assertTrue(up.heroHp <= b.heroMaxHp(up.level))
    }

    @Test
    fun `auto levelling never overspends`() {
        val r = IdleEngine.advance(
            GameState.newRun(t0, b).copy(gold = 5_000.0, autoLevel = true),
            t0 + 120_000L,
            b,
        )
        assertTrue(r.state.gold >= 0.0, "gold went negative: ${r.state.gold}")
        assertTrue(r.state.level > 1)
    }

    @Test
    fun `an overwhelming boss wipes the hero and rewinds the act`() {
        val brutal = b.copy(wavesPerAct = 1) // wave 1 is the boss, hero is level 1
        val start = GameState.newRun(t0, brutal).copy(act = 6)
        val r = IdleEngine.advance(start, t0 + 60_000L, brutal)

        assertTrue(r.state.deaths > 0L, "level 1 hero should lose to an act 6 boss")
        assertEquals(1, r.state.wave)
        assertEquals(6, r.state.act, "a wipe keeps the act, only the push is lost")
        assertTrue(r.events.any { it is GameEvent.HeroDown })
    }

    @Test
    fun `deepest push is a high water mark`() {
        val r = IdleEngine.advance(GameState.newRun(t0, b), t0 + 600_000L, b)
        assertTrue(r.state.deepestWave >= r.state.wave)
        assertTrue(r.state.deepestAct >= r.state.act)
    }

    @Test
    fun `a clock that jumps backwards is ignored`() {
        val s = IdleEngine.advance(GameState.newRun(t0, b), t0 + 60_000L, b).state
        val r = IdleEngine.advance(s, t0, b)
        assertEquals(s, r.state)
    }

    @Test
    fun `hero and enemy health stay inside their pools`() {
        var s = GameState.newRun(t0, b).copy(autoLevel = true)
        for (i in 1..40) {
            s = IdleEngine.advance(s, t0 + i * 30_000L, b).state
            assertTrue(s.heroHp <= b.heroMaxHp(s.level) + 1e-6, "hero hp overflow at step $i")
            assertTrue(s.heroHp > 0.0 || s.isDown, "hero hp underflow at step $i")
            assertTrue(s.enemyHp <= b.enemyMaxHp(s.act, s.wave) + 1e-6, "enemy hp overflow at step $i")
            assertTrue(s.heroHpFraction(b) in 0.0..1.0)
            assertTrue(s.enemyHpFraction(b) in 0.0..1.0)
        }
    }
}
