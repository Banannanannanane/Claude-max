package dev.taskbarhero.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdleEngineTest {

    private val b = Balance()
    private val t0 = 1_700_000_000_000L

    @Test
    fun `first advance only anchors the clock`() {
        val fresh = GameState.newRun(0L, b).copy(lastTickMs = 0L)
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
        val (state, events) = replay(GameState.newRun(t0, b).copy(autoLevel = true), minutes = 30)
        assertTrue(state.act > 1, "auto-levelling should clear act 1 within half an hour")
        assertTrue(state.bossKills > 0L)
        assertTrue(events.any { it is GameEvent.ActCleared })
    }

    /**
     * Advances in 30-second slices, the way the widget's alarm does, keeping every
     * event. A single long call would drop the milestones: only the last
     * [IdleEngine.MAX_EVENTS] survive, and auto-levelling floods that window.
     */
    private fun replay(start: GameState, minutes: Int): Pair<GameState, List<GameEvent>> {
        var state = start
        val events = mutableListOf<GameEvent>()
        for (slice in 1..minutes * 2) {
            val r = IdleEngine.advance(state, t0 + slice * 30_000L, b)
            state = r.state
            events += r.events
            assertTrue(r.events.size <= IdleEngine.MAX_EVENTS)
        }
        return state to events
    }

    @Test
    fun `manual level up spends gold and cannot go into debt`() {
        val poor = GameState.newRun(t0, b)
        assertNull(IdleEngine.levelUp(poor, b))

        val rich = poor.copy(gold = 1_000.0)
        val up = assertNotNull(IdleEngine.levelUp(rich, b))
        assertEquals(2, up.partyLevel, "one hero gained a level")
        assertEquals(1_000.0 - b.levelCost(1), up.gold, 1e-9)
        val hero = up.roster.first()
        assertTrue(hero.hp > rich.roster.first().hp, "a new level should grant the extra health")
        assertTrue(hero.hp <= hero.maxHp(b))
    }

    @Test
    fun `levelling always buys for the hero furthest behind`() {
        val rich = GameState.newRun(t0, b).copy(gold = 1e6, unlocked = 3)
        var s = rich
        repeat(6) { s = assertNotNull(IdleEngine.levelUp(s, b)) }
        val levels = s.roster.map { it.level }
        assertEquals(listOf(3, 3, 3), levels, "six levels should spread evenly over three heroes")
    }

    @Test
    fun `the party grows by reaching acts, not by paying`() {
        val start = GameState.newRun(t0, b).copy(autoLevel = true)
        assertEquals(1, start.unlocked, "a run opens with the knight alone")

        val (state, events) = replay(start, minutes = 15)
        assertTrue(state.unlocked >= 2, "act 2 should have recruited the ranger")
        val joins = events.filterIsInstance<GameEvent.HeroJoined>()
        assertTrue(joins.isNotEmpty(), "a recruit should announce itself")
        assertEquals(HeroClass.RANGER, joins.first().cls, "the ranger comes first")
        // A recruit arrives ready to fight, not at zero health.
        assertTrue(state.roster[1].hp > 0.0)
    }

    @Test
    fun `a downed hero stops fighting but the party carries on`() {
        val b2 = b.copy(wavesPerAct = 1)
        val start = GameState.newRun(t0, b2).copy(act = 5, unlocked = 3)
        val r = IdleEngine.advance(start, t0 + 30_000L, b2)
        // Against an act 5 boss the front hero should fall well before the others.
        assertTrue(r.state.deaths > 0L || r.state.roster.any { it.isDown }, "nobody took a scratch")
    }

    @Test
    fun `the cube turns nine of a grade into one of the next`() {
        val stash = MutableList(Loot.GRADES.size) { 0 }
        stash[2] = 9
        val state = GameState.newRun(t0, b).copy(stash = stash)

        val (fused, grade) = assertNotNull(IdleEngine.cube(state, b))
        assertEquals(3, grade)
        assertEquals(0, fused.stash[2])
        assertEquals(1, fused.stash[3])
        assertNull(IdleEngine.cube(fused, b), "one item is not nine")
    }

    @Test
    fun `the cube always eats the lowest grade that can fuse`() {
        val stash = MutableList(Loot.GRADES.size) { 0 }
        stash[1] = 9
        stash[4] = 12
        val (fused, grade) = assertNotNull(IdleEngine.cube(GameState.newRun(t0, b).copy(stash = stash), b))
        assertEquals(2, grade, "grade 1 should go first")
        assertEquals(12, fused.stash[4], "the higher pile is untouched")
    }

    @Test
    fun `the top grade cannot be fused further`() {
        val stash = MutableList(Loot.GRADES.size) { 0 }
        stash[Loot.GRADES.lastIndex] = 99
        assertNull(IdleEngine.cube(GameState.newRun(t0, b).copy(stash = stash), b))
    }

    @Test
    fun `gear makes the party hit harder, and higher grades much harder`() {
        val bare = GameState.newRun(t0, b)
        val common = bare.copy(stash = bare.stash.toMutableList().also { it[0] = 9 })
        val cosmic = bare.copy(stash = bare.stash.toMutableList().also { it[9] = 1 })

        assertTrue(common.partyDps(b) > bare.partyDps(b))
        assertTrue(cosmic.partyDps(b) > common.partyDps(b), "one cosmic must beat nine commons")
    }

    @Test
    fun `bosses stock the stash at their own grade`() {
        val b2 = b.copy(wavesPerAct = 1)
        val start = GameState.newRun(t0, b2).copy(gold = 1e9, autoLevel = true)
        val r = IdleEngine.advance(start, t0 + 600_000L, b2)
        assertTrue(r.state.bossKills > 0L)
        assertEquals(r.state.bossKills, r.state.stash.sum().toLong(), "every boss owes exactly one drop")
    }

    @Test
    fun `auto levelling never overspends`() {
        val r = IdleEngine.advance(
            GameState.newRun(t0, b).copy(gold = 5_000.0, autoLevel = true),
            t0 + 120_000L,
            b,
        )
        assertTrue(r.state.gold >= 0.0, "gold went negative: ${r.state.gold}")
        assertTrue(r.state.partyLevel > 1)
    }

    @Test
    fun `an overwhelming boss wipes the party and rewinds the act`() {
        val brutal = b.copy(wavesPerAct = 1) // wave 1 is the boss, hero is level 1
        val start = GameState.newRun(t0, brutal).copy(act = 6)
        val r = IdleEngine.advance(start, t0 + 60_000L, brutal)

        assertTrue(r.state.deaths > 0L, "a level 1 party should lose to an act 6 boss")
        assertEquals(1, r.state.wave)
        assertEquals(6, r.state.act, "a wipe keeps the act, only the push is lost")
        assertTrue(r.events.any { it is GameEvent.HeroDown })
    }

    @Test
    fun `a potion heals, costs gold, and is refused when it would do nothing`() {
        val full = GameState.newRun(t0, b).copy(gold = 1_000.0)
        assertNull(IdleEngine.drinkPotion(full, b), "a party at full health has nothing to heal")

        val hurt = full.copy(party = full.party.map { it.copy(hp = it.maxHp(b) * 0.2) })
        val healed = assertNotNull(IdleEngine.drinkPotion(hurt, b))
        assertTrue(healed.roster.all { it.hp == it.maxHp(b) }, "everyone should come back full")
        assertEquals(1_000.0 - b.potionCost(hurt.partyLevel), healed.gold, 1e-9)

        assertNull(IdleEngine.drinkPotion(hurt.copy(gold = 0.0), b), "no gold, no potion")
    }

    @Test
    fun `a potion revives a wiped party on the spot`() {
        val down = GameState.newRun(t0, b).copy(gold = 500.0, downUntilMs = t0 + 4_000L)
        val revived = assertNotNull(IdleEngine.drinkPotion(down, b))
        assertFalse(revived.isDown, "the down timer should be cleared")
        assertTrue(revived.roster.all { it.hp == it.maxHp(b) })

        // And the fight resumes immediately rather than after the timer.
        val r = IdleEngine.advance(revived, t0 + 1_000L, b)
        assertTrue(r.state.enemyHp < b.enemyMaxHp(r.state.act, r.state.wave))
    }

    @Test
    fun `a potion is cheaper than a level, so healing is never the greedy play`() {
        for (level in 1..40) {
            assertTrue(b.potionCost(level) < b.levelCost(level), "potion outpriced the level at $level")
        }
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
    fun `party and enemy health stay inside their pools`() {
        var s = GameState.newRun(t0, b).copy(autoLevel = true)
        for (i in 1..40) {
            s = IdleEngine.advance(s, t0 + i * 30_000L, b).state
            for (hero in s.roster) {
                assertTrue(hero.hp <= hero.maxHp(b) + 1e-6, "${hero.cls} hp overflow at step $i")
                assertTrue(hero.hp >= 0.0, "${hero.cls} hp underflow at step $i")
                assertTrue(hero.hpFraction(b) in 0.0..1.0)
            }
            assertTrue(s.living.isNotEmpty() || s.isDown, "a live party or a wipe, never neither")
            assertTrue(s.enemyHp <= b.enemyMaxHp(s.act, s.wave) + 1e-6, "enemy hp overflow at step $i")
            assertTrue(s.enemyHpFraction(b) in 0.0..1.0)
        }
    }
}
