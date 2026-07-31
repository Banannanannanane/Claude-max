package dev.taskbarhero.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val T0 = 1_700_000_000_000L
private val B = Balance()

class IdleEngineTest {

    @Test
    fun `chopping time into pieces gives the same world as one long jump`() {
        // The property everything else rests on: the widget advances the game in
        // ragged 30-second lumps, the open app in 250 ms ones, and a phone that was
        // off does it in a single leap. All three have to agree.
        val start = GameState.newRun(T0, B).copy(autoLevel = true)
        val oneGo = IdleEngine.advance(start, T0 + 3_600_000L, B).state

        var chopped = start
        var clock = T0
        val steps = listOf(1_000L, 37_000L, 250L, 600_000L, 13_500L)
        var i = 0
        while (clock < T0 + 3_600_000L) {
            val step = minOf(steps[i++ % steps.size], T0 + 3_600_000L - clock)
            clock += step
            chopped = IdleEngine.advance(chopped, clock, B).state
        }
        assertEquals(oneGo.act, chopped.act, "act")
        assertEquals(oneGo.wave, chopped.wave, "wave")
        assertEquals(oneGo.kills, chopped.kills, "kills")
        assertEquals(oneGo.partyLevel, chopped.partyLevel, "level")
        assertEquals(oneGo.gold.toLong(), chopped.gold.toLong(), "gold")
    }

    @Test
    fun `time before the last tick changes nothing`() {
        val s = GameState.newRun(T0, B)
        assertEquals(s, IdleEngine.advance(s, T0 - 5_000L, B).state, "a clock that went backwards")
        assertEquals(s, IdleEngine.advance(s, T0 + 10L, B).state, "less than one tick")
    }

    @Test
    fun `a long absence is credited up to the cap and no further`() {
        val s = GameState.newRun(T0, B)
        val threeDays = 3 * 24 * 60 * 60 * 1000L
        val capped = IdleEngine.advance(s, T0 + threeDays, B)

        assertEquals(threeDays - B.offlineCapMs, capped.skippedMs, "the excess must be reported")
        assertEquals(T0 + threeDays, capped.state.lastTickMs, "the clock still moves to now")

        val exact = IdleEngine.advance(s, T0 + B.offlineCapMs, B).state
        assertEquals(exact.kills, capped.state.kills, "and the cap is what was simulated")
    }

    @Test
    fun `an hour of idling clears the first act and takes a boss with it`() {
        val result = IdleEngine.advance(
            GameState.newRun(T0, B).copy(autoLevel = true),
            T0 + 3_600_000L,
            B,
        )
        assertTrue(result.state.act > 1, "act ${result.state.act} after an hour")
        assertTrue(result.state.bossKills >= 1, "no boss died in an hour")
        assertTrue(result.state.gold >= 0.0, "auto-levelling spent gold it did not have")
    }

    @Test
    fun `the second hero joins on the act that recruits them`() {
        var s = GameState.newRun(T0, B).copy(autoLevel = true)
        s = IdleEngine.advance(s, T0 + 2 * 3_600_000L, B).state
        assertTrue(s.act >= 2, "test needs to reach act 2, reached ${s.act}")
        assertEquals(
            HeroClass.entries.count { it.unlockAct <= s.act }.coerceAtMost(s.party.size),
            s.unlocked,
            "the roster does not match the act",
        )
    }

    @Test
    fun `a wipe costs the act, never the gold or the levels`() {
        // A level 1 party against act 6 is not a fight, it is an execution.
        val doomed = GameState.newRun(T0, B).copy(
            act = 6, wave = 4, unlocked = 1, gold = 500.0,
            enemyHp = B.enemyMaxHp(6, 4),
            loadout = Loadout(stash = List(3) { Item(Slot.WEAPON, grade = 2, level = 4) }),
        )
        val after = IdleEngine.advance(doomed, T0 + 60_000L, B)

        assertTrue(after.state.wipes >= 1, "the party should have fallen")
        assertEquals(6, after.state.act, "a wipe must not cost the act itself")
        assertEquals(1, after.state.wave, "but it does cost the progress inside it")
        assertEquals(500.0, after.state.gold, "gold survives a wipe")
        assertEquals(doomed.loadout, after.state.loadout, "and so does the gear")
        assertTrue(after.events.any { it is GameEvent.Wiped })
    }

    @Test
    fun `a wiped party is out of the fight, and a potion puts it straight back`() {
        val wiped = GameState.newRun(T0, B).copy(
            downUntilMs = T0 + 4_000L,
            gold = 1_000_000.0,
            party = GameState.newRun(T0, B).party.map { it.copy(hp = 1.0) },
        )
        assertTrue(wiped.isDown(T0 + 1_000L))

        val healed = IdleEngine.drinkPotion(wiped, B)
        assertNotNull(healed)
        assertFalse(healed.isDown(T0 + 1_000L), "a potion must lift the party off the floor")
        assertTrue(healed.roster.all { it.hp == it.maxHp(B) })
    }

    @Test
    fun `levelling buys the hero that is furthest behind`() {
        val rich = GameState.newRun(T0, B).copy(unlocked = 3, gold = 1_000.0)
            .let { s -> s.copy(party = s.party.mapIndexed { i, h -> h.copy(level = 1 + i * 3) }) }

        val after = IdleEngine.levelUp(rich, B)
        assertNotNull(after)
        assertEquals(2, after.party[0].level, "the cheapest hero should have gone up")
        assertEquals(4, after.party[1].level)
        assertTrue(after.gold < rich.gold, "and it should have cost something")
    }

    @Test
    fun `a level-up is not a heal`() {
        val hurt = GameState.newRun(T0, B).let { s ->
            s.copy(gold = 10_000.0, party = s.party.map { it.copy(hp = 1.0) })
        }
        val after = IdleEngine.levelUp(hurt, B)!!
        assertEquals(1.0, after.party[0].hp, "buying a level must not top the hero up")
        assertTrue(after.party[0].maxHp(B) > hurt.party[0].maxHp(B), "but the pool grows")
    }

    @Test
    fun `nothing is bought without the gold for it`() {
        val broke = GameState.newRun(T0, B).copy(gold = 0.0)
        assertNull(IdleEngine.levelUp(broke, B))
        assertNull(IdleEngine.drinkPotion(broke, B))
        assertNull(IdleEngine.cube(broke, B))
    }

    @Test
    fun `the cube eats nine of the lowest grade that can afford it`() {
        val pile = List(4) { Item(Slot.RING, grade = 0, level = 1) } +
            List(9) { Item(Slot.HELM, grade = 2, level = 5 + it) } +
            List(9) { Item(Slot.BOOTS, grade = 3, level = 2) }
        val s = GameState.newRun(T0, B).copy(loadout = Loadout(stash = pile))

        val (after, event) = IdleEngine.cube(s, B)!!
        val stash = after.loadout.stash
        assertEquals(0, stash.count { it.grade == 2 }, "grade 2 should have been eaten")
        assertEquals(4, stash.count { it.grade == 0 }, "the grade that could not fuse is untouched")
        assertEquals(GameEvent.Cubed(3), event)

        // The piece it made is worn or held, and it is never worse than its input.
        val made = after.loadout.worn.flatMap { it.values } + stash
        assertTrue(made.any { it.grade == 3 && it.level == 13 }, "fusing lost the best level it ate")
    }

    @Test
    fun `a drop is worn when it beats what is worn, and piled up when it does not`() {
        val good = Item(Slot.WEAPON, grade = 5, level = 20)
        val poor = Item(Slot.WEAPON, grade = 0, level = 1)

        val first = Loadout().take(good, unlocked = 1, b = B)
        assertEquals(good, first.worn[0][Slot.WEAPON], "an empty slot takes anything")
        assertTrue(first.stash.isEmpty())

        val second = first.take(poor, unlocked = 1, b = B)
        assertEquals(good, second.worn[0][Slot.WEAPON], "a worse piece must not replace a better one")
        assertEquals(listOf(poor), second.stash, "but it is kept — the Cube eats from that pile")
    }

    @Test
    fun `gear lifts the party, and a grade is worth more than the one below`() {
        val bare = Loadout()
        assertEquals(1.0, bare.power(0, B), "no gear is no bonus at all")

        val rare = bare.take(Item(Slot.WEAPON, grade = 2, level = 1), 1, B)
        val cosmic = bare.take(Item(Slot.WEAPON, grade = 9, level = 1), 1, B)
        assertTrue(rare.power(0, B) > 1.0)
        assertTrue(cosmic.power(0, B) > rare.power(0, B), "a cosmic piece must beat a rare one")

        // Grade beats depth: the ladder is the grade, the level is only the rung.
        val deepCommon = bare.take(Item(Slot.WEAPON, grade = 0, level = 60), 1, B)
        assertTrue(rare.power(0, B) > deepCommon.power(0, B))
    }

    @Test
    fun `gear survives being written down and read back`() {
        var loadout = Loadout()
        for (i in 0 until 12) loadout = loadout.take(Item.roll(act = 3 + i, seed = i.toLong()), 3, B)
        assertEquals(loadout, Loadout.decode(loadout.encode()))
        assertEquals(Loadout(), Loadout.decode(Loadout().encode()), "an empty run round-trips too")
    }
}

class HudTest {

    @Test
    fun `the hud says what the buttons are waiting for`() {
        val s = GameState.newRun(T0, B).copy(gold = 0.0)
        val hud = Hud.of(s, B)

        assertFalse(hud.canLevelUp)
        assertFalse(hud.canCube)
        assertEquals("0/9", hud.cubeLabel, "a mute button teaches nothing")
        assertEquals("1-01", hud.stage)
        assertEquals("LV 1", hud.level)
    }

    @Test
    fun `a full stack turns the cube button on`() {
        val pile = List(9) { Item(Slot.RING, grade = 1, level = 3) }
        val hud = Hud.of(GameState.newRun(T0, B).copy(loadout = Loadout(stash = pile)), B)
        assertTrue(hud.canCube)
        assertEquals("READY", hud.cubeLabel)
        assertEquals(2, hud.cubeGrade, "the button shows the grade it is about to make")
    }

    @Test
    fun `boss waves are named, and every name is a boss`() {
        for (act in 1..12) {
            val boss = Bestiary.mob(act, B.wavesPerAct, 0, B)
            assertTrue(boss.isBoss, "act $act wave ${B.wavesPerAct} should be a boss")
            assertEquals(dev.taskbarhero.assets.Art.BOSS, boss.sprite)
            assertFalse(Bestiary.mob(act, B.wavesPerAct - 1, 0, B).isBoss)
        }
    }

    @Test
    fun `the same slot always shows the same monster, and an act shows several`() {
        repeat(3) { assertEquals(Bestiary.mob(3, 4, 2, B), Bestiary.mob(3, 4, 2, B)) }
        val variety = (1..9).flatMap { w -> (0..3).map { Bestiary.mob(5, w, it, B).name } }.toSet()
        assertTrue(variety.size >= 3, "act 5 showed only $variety")
    }

    @Test
    fun `deep acts stop showing the chaff`() {
        val deep = (1..9).flatMap { w -> (0..5).map { Bestiary.mob(12, w, it, B).name } }.toSet()
        assertFalse("SLIME" in deep, "act 12 is still throwing slimes")
        assertTrue(deep.size >= 3, "and it needs more than one monster: $deep")
    }

    @Test
    fun `every monster the bestiary can name is a sprite the sheet is asked for`() {
        val known = dev.taskbarhero.assets.Art.dungeonKeys.toSet()
        for (act in 1..15) for (wave in 1..10) for (i in 0..3) {
            val mob = Bestiary.mob(act, wave, i, B)
            assertTrue(mob.sprite in known, "${mob.name} draws ${mob.sprite}, which is not mapped")
        }
    }

    @Test
    fun `short numbers read like an idle game`() {
        assertEquals("0", Fmt.short(0.0))
        assertEquals("942", Fmt.short(942.7))
        assertEquals("1.0K", Fmt.short(1_000.0))
        assertEquals("12.4K", Fmt.short(12_499.0))
        assertEquals("999K", Fmt.short(999_400.0))
        assertEquals("3.0M", Fmt.short(3_000_000.0))
        assertEquals("1.4T", Fmt.short(1.42e12))
    }
}

class BalanceTest {

    /**
     * The curve, pinned at six points.
     *
     * Every constant in [Balance] is chosen against the others, so a lone tweak to
     * one of them is almost always a mistake — it shows up here as a run that
     * either laps itself or stalls in act 2. The ranges are wide on purpose: this
     * guards the shape, not the exact numbers.
     */
    @Test
    fun `the idle curve keeps its shape`() {
        val expected = listOf(
            5L to 1..2,
            15L to 2..5,
            30L to 4..9,
            60L to 10..22,
            240L to 25..48,
            720L to 35..70,
        )
        for ((minutes, acts) in expected) {
            val s = IdleEngine.advance(
                GameState.newRun(T0, B).copy(autoLevel = true),
                T0 + minutes * 60_000L,
                B,
            ).state
            assertTrue(s.act in acts, "after $minutes min the run reached act ${s.act}, wanted $acts")
            assertTrue(s.gold.isFinite(), "gold overflowed after $minutes min")
            assertTrue(s.partyLevel > 0)
        }
    }

    @Test
    fun `the run is pushed back often enough to matter, and not every wave`() {
        val s = IdleEngine.advance(
            GameState.newRun(T0, B).copy(autoLevel = true),
            T0 + 12 * 3_600_000L,
            B,
        ).state
        assertTrue(s.wipes > 0, "a run that never falls has no difficulty at all")
        assertTrue(s.kills / maxOf(s.wipes, 1) >= 4, "the party wipes every ${s.kills / maxOf(s.wipes, 1)} kills")
    }

    @Test
    fun `big numbers never run off the end of the suffixes`() {
        assertEquals("MAX", Fmt.short(Double.POSITIVE_INFINITY))
        assertFalse(Fmt.short(1e60).contains("E"), "scientific notation is not an idle-game number")
        assertTrue(Fmt.short(1e60).length <= 7, "'${Fmt.short(1e60)}' will not fit a widget")
    }
}

class RuneTest {

    @Test
    fun `runes come from bosses and from nowhere else`() {
        val s = IdleEngine.advance(
            GameState.newRun(T0, B).copy(autoLevel = true), T0 + 3_600_000L, B,
        ).state
        assertEquals(s.bossKills.toInt(), s.runes.earned, "one rune per boss, no more and no fewer")
    }

    @Test
    fun `a rank costs one more than the last, and cannot be bought without them`() {
        val broke = RuneState(earned = 0)
        assertNull(broke.buy(Rune.POWER), "nothing is bought with nothing")

        var runes = RuneState(earned = 6)
        runes = runes.buy(Rune.POWER)!!
        assertEquals(1, runes.rank(Rune.POWER))
        assertEquals(5, runes.available, "rank one costs one")

        runes = runes.buy(Rune.POWER)!!
        assertEquals(3, runes.available, "rank two costs two")
        assertEquals(3, runes.used)
    }

    @Test
    fun `a rune cannot be pushed past its last rank`() {
        var runes = RuneState(earned = 500)
        repeat(Rune.HASTE.ranks) { runes = runes.buy(Rune.HASTE)!! }
        assertEquals(Rune.HASTE.ranks, runes.rank(Rune.HASTE))
        assertNull(runes.buy(Rune.HASTE), "a maxed rune has nothing left to sell")
    }

    @Test
    fun `ranks change the run, each in its own direction`() {
        val bare = GameState.newRun(T0, B).copy(unlocked = 3)
        val strong = bare.copy(runes = RuneState(earned = 99, spent = mapOf(Rune.POWER to 3)))
        assertTrue(strong.partyDps(B) > bare.partyDps(B), "POWER should hit harder")

        val tough = bare.copy(runes = RuneState(earned = 99, spent = mapOf(Rune.VIGOUR to 3)))
        assertTrue(tough.heroMaxHp(tough.party[0], B) > bare.heroMaxHp(bare.party[0], B))

        val thrifty = bare.copy(runes = RuneState(earned = 99, spent = mapOf(Rune.HASTE to 3)))
        assertTrue(thrifty.levelCost(B) < bare.levelCost(B), "HASTE should make levels cheaper")

        // And the discount stops well short of free, or the game would end there.
        val maxed = RuneState(earned = 99, spent = mapOf(Rune.HASTE to Rune.HASTE.ranks))
        assertTrue(maxed.levelCost >= 0.5)
    }

    @Test
    fun `REST buys real offline time`() {
        val rested = GameState.newRun(T0, B)
            .copy(runes = RuneState(earned = 99, spent = mapOf(Rune.REST to 3)))
        val plain = GameState.newRun(T0, B)
        val away = B.offlineCapMs + 5 * 60 * 60 * 1000L

        val restedRun = IdleEngine.advance(rested, T0 + away, B)
        val plainRun = IdleEngine.advance(plain, T0 + away, B)
        assertTrue(restedRun.skippedMs < plainRun.skippedMs, "REST should waste less of an absence")
        assertTrue(restedRun.state.kills > plainRun.state.kills, "and should have played longer")
    }

    @Test
    fun `runes survive being written down and read back`() {
        val runes = RuneState(earned = 40, spent = mapOf(Rune.POWER to 2, Rune.REST to 5))
        assertEquals(runes, RuneState.decode(runes.encode()))
        assertEquals(RuneState(), RuneState.decode(""), "a save with no runes yet reads as none")
    }
}

class PortalTest {

    @Test
    fun `the portal only goes back, and only where the party has been`() {
        val s = GameState.newRun(T0, B).copy(act = 8, deepestAct = 8)
        assertNull(IdleEngine.travel(s, 9, B), "forward would skip the fight that pays for it")
        assertNull(IdleEngine.travel(s, 8, B), "there is nowhere to go from where you are")
        assertNull(IdleEngine.travel(s, 0, B))
        assertNotNull(IdleEngine.travel(s, 3, B))
    }

    @Test
    fun `arriving through the portal starts the act, on your feet`() {
        val battered = GameState.newRun(T0, B).copy(
            act = 12, wave = 7, deepestAct = 12, downUntilMs = T0 + 9_000L,
        ).let { s -> s.copy(party = s.party.map { it.copy(hp = 1.0) }) }

        val after = IdleEngine.travel(battered, 4, B)!!
        assertEquals(4, after.act)
        assertEquals(1, after.wave, "a portal lands at the start of an act")
        assertEquals(B.enemyMaxHp(4, 1), after.enemyHp)
        assertTrue(after.party.all { it.hp == after.heroMaxHp(it, B) }, "and not into a slower wipe")
        assertFalse(after.isDown(T0 + 1_000L))
    }

    @Test
    fun `the deepest act is remembered across a trip back`() {
        val s = GameState.newRun(T0, B).copy(act = 12, wave = 3, deepestAct = 12, deepestWave = 4)
        val back = IdleEngine.travel(s, 5, B)!!
        assertEquals(12, back.deepestAct, "going back must not cost the record")
        assertNotNull(IdleEngine.travel(back, 11, B), "and the way forward again stays open")
    }
}
