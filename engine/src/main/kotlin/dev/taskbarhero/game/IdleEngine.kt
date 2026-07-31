package dev.taskbarhero.game

/**
 * The game, as a pure function of elapsed time.
 *
 * An Android widget has no render loop: its refresh floor is half an hour, and it
 * is redrawn at moments nobody chooses. So nothing here counts frames. Ask what
 * the world looks like *now* and the engine replays the fight in fixed ticks from
 * where it left off — which makes an hour on screen and an hour with the screen
 * off produce exactly the same state, and makes the whole thing testable without
 * waiting for any of it.
 */
object IdleEngine {

    /** One simulated beat. Small enough to feel continuous, large enough to replay a day. */
    const val TICK_MS = 250L

    /** Ceiling on events reported for one advance, so a long absence cannot flood the caller. */
    private const val MAX_EVENTS = 24

    data class Result(
        val state: GameState,
        val events: List<GameEvent>,
        /** Time deliberately not simulated, because the absence ran past the cap. */
        val skippedMs: Long = 0L,
    )

    /**
     * Advances [state] to [nowMs].
     *
     * Only whole ticks are consumed, and `lastTickMs` moves by whole ticks too, so
     * advancing in one call and advancing in fifty give the same answer — the
     * property the tests lean on hardest, because it is what makes the widget and
     * the open app agree.
     */
    fun advance(state: GameState, nowMs: Long, b: Balance = Balance()): Result {
        if (nowMs <= state.lastTickMs) return Result(state, emptyList())

        var elapsed = nowMs - state.lastTickMs
        var skipped = 0L
        if (elapsed > b.offlineCapMs) {
            skipped = elapsed - b.offlineCapMs
            elapsed = b.offlineCapMs
        }

        val ticks = elapsed / TICK_MS
        if (ticks <= 0L) return Result(state, emptyList(), skipped)

        var s = state.copy(lastTickMs = state.lastTickMs + ticks * TICK_MS + skipped)
        val events = mutableListOf<GameEvent>()
        var clock = state.lastTickMs

        repeat(ticks.toInt()) {
            clock += TICK_MS
            s = tick(s, clock, b, events)
        }
        return Result(s, events.takeLast(MAX_EVENTS), skipped)
    }

    private fun tick(state: GameState, nowMs: Long, b: Balance, events: MutableList<GameEvent>): GameState {
        var s = state

        // A wiped party is out of the fight, and heals while it is.
        if (s.isDown(nowMs)) return s.copy(party = healed(s, b, rate = 0.25))

        s = s.copy(party = healed(s, b, rate = 0.04))

        val seconds = TICK_MS / 1000.0
        val dps = s.partyDps(b)
        var enemyHp = s.enemyHp - dps * seconds

        // The monster hits the front hero only; the rest recover behind him.
        val incoming = b.enemyDps(s.act, s.wave) * seconds
        val front = s.frontIndex
        val hurt = s.party.toMutableList()
        val hero = hurt[front]
        hurt[front] = hero.copy(hp = (hero.hp - incoming).coerceAtLeast(0.0))
        s = s.copy(party = hurt)

        if (s.roster.none { it.hp > 0.0 }) return wipe(s, nowMs, b, events)

        if (enemyHp > 0.0) return s.copy(enemyHp = enemyHp)

        // The monster is dead: pay out, then walk to the next one.
        val boss = b.isBossWave(s.wave)
        s = s.copy(
            gold = s.gold + b.goldFor(s.act, s.wave),
            kills = s.kills + 1,
            bossKills = if (boss) s.bossKills + 1 else s.bossKills,
        )
        if (boss) {
            val drop = Loot.roll(s.act, s.bossKills)
            val stash = s.stash.toMutableList()
            stash[drop.grade] = stash[drop.grade] + 1
            s = s.copy(stash = stash)
            events += GameEvent.BossDown(s.act, drop)
            s = advanceAct(s, b, events)
        } else {
            s = s.copy(wave = s.wave + 1, enemyIndex = s.enemyIndex + 1)
        }
        s = s.copy(
            enemyHp = b.enemyMaxHp(s.act, s.wave),
            deepestAct = maxOf(s.deepestAct, s.act),
            deepestWave = if (s.act > s.deepestAct) s.wave else maxOf(s.deepestWave, s.wave),
        )

        if (s.autoLevel) s = autoSpend(s, b, events)
        return s
    }

    private fun advanceAct(state: GameState, b: Balance, events: MutableList<GameEvent>): GameState {
        events += GameEvent.ActCleared(state.act)
        var s = state.copy(act = state.act + 1, wave = 1, enemyIndex = 0)

        val ready = HeroClass.entries.count { it.unlockAct <= s.act }.coerceAtMost(s.party.size)
        if (ready > s.unlocked) {
            val joined = s.party[s.unlocked].cls
            s = s.copy(unlocked = ready)
            events += GameEvent.HeroJoined(joined)
        }
        return s
    }

    /** A wipe costs the act's progress, never the gold, the levels or the stash. */
    private fun wipe(state: GameState, nowMs: Long, b: Balance, events: MutableList<GameEvent>): GameState {
        events += GameEvent.Wiped(state.act)
        return state.copy(
            wave = 1,
            enemyIndex = 0,
            enemyHp = b.enemyMaxHp(state.act, 1),
            wipes = state.wipes + 1,
            downUntilMs = nowMs + b.downMs,
            party = state.party.map { it.copy(hp = it.maxHp(b) * 0.35) },
        )
    }

    /** Regeneration, as a fraction of the pool per tick. The dead do not heal. */
    private fun healed(state: GameState, b: Balance, rate: Double): List<Hero> =
        state.party.map { hero ->
            val max = hero.maxHp(b)
            when {
                hero.hp <= 0.0 && !state.isDown(state.lastTickMs) -> hero
                hero.hp >= max -> hero
                else -> hero.copy(hp = (hero.hp + max * rate * (TICK_MS / 1000.0)).coerceAtMost(max))
            }
        }

    /** AUTO: spend on levels while it is affordable, cheapest hero first. */
    private fun autoSpend(state: GameState, b: Balance, events: MutableList<GameEvent>): GameState {
        var s = state
        var guard = 0
        while (s.canLevelUp(b) && guard++ < 40) {
            s = levelUp(s, b) ?: break
            events += GameEvent.LevelUp(s.partyLevel)
        }
        return s
    }

    /** Buys one level for the hero that is furthest behind. Null when it is unaffordable. */
    fun levelUp(state: GameState, b: Balance = Balance()): GameState? {
        if (!state.canLevelUp(b)) return null
        val index = state.cheapestHeroIndex()
        val cost = b.levelCost(state.roster[index].level)
        val party = state.party.toMutableList()
        val hero = party[index]
        // The new pool is granted, not the health: levelling is not a heal.
        party[index] = hero.copy(level = hero.level + 1)
        return state.copy(gold = state.gold - cost, party = party)
    }

    /** Heals everyone, and puts a wiped party straight back on its feet. */
    fun drinkPotion(state: GameState, b: Balance = Balance()): GameState? {
        if (!state.canDrinkPotion(b)) return null
        return state.copy(
            gold = state.gold - state.potionCost(b),
            party = state.party.map { it.copy(hp = it.maxHp(b)) },
            downUntilMs = 0L,
        )
    }

    /** Nine of a grade become one of the next. Always the lowest grade that can. */
    fun cube(state: GameState, b: Balance = Balance()): Pair<GameState, GameEvent>? {
        val grade = state.fusableGrade(b) ?: return null
        val stash = state.stash.toMutableList()
        stash[grade] = stash[grade] - b.cubeInput
        stash[grade + 1] = stash[grade + 1] + 1
        return state.copy(stash = stash) to GameEvent.Cubed(grade + 1)
    }
}
