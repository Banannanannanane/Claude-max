package dev.taskbarhero.engine

import kotlin.math.min

/**
 * The auto-battler. Nothing here touches Android, a clock, or the disk: given a
 * state and a wall-clock instant it replays the fight tick by tick, which is what
 * lets a home-screen widget stay correct while being redrawn once a minute (or
 * once a day).
 *
 * Advancing in one call or in a hundred chunks yields the same state, because
 * [GameState.lastTickMs] only ever moves by whole ticks.
 */
object IdleEngine {

    const val MAX_EVENTS = 24

    fun advance(state: GameState, nowMs: Long, b: Balance = Balance()): TickResult {
        if (state.lastTickMs == 0L) {
            return TickResult(state.copy(lastTickMs = nowMs), emptyList())
        }
        val rawElapsed = (nowMs - state.lastTickMs).coerceAtLeast(0L)
        val skipped = (rawElapsed - b.maxCatchUpMs).coerceAtLeast(0L)
        val elapsed = min(rawElapsed, b.maxCatchUpMs)
        val steps = elapsed / b.tickMs
        if (steps == 0L) {
            return TickResult(state, emptyList(), skippedMs = skipped)
        }

        // Virtual clock: when the cap kicks in we pretend the run started later,
        // which also retires any stale DOWN timer for free.
        val startMs = nowMs - elapsed
        val acc = Accumulator(state)
        var s = state.copy(lastTickMs = startMs)
        val dt = b.tickMs / 1000.0

        for (i in 1..steps) {
            s = step(s, startMs + i * b.tickMs, dt, b, acc)
        }

        return TickResult(
            state = s.copy(lastTickMs = startMs + steps * b.tickMs),
            events = acc.events.toList(),
            goldGained = s.lifetimeGold - state.lifetimeGold,
            xpGained = acc.xpGained,
            killsGained = s.kills - state.kills,
            simulatedMs = steps * b.tickMs,
            skippedMs = skipped,
        )
    }

    /** Buys a level for the cheapest hero. Returns null when it is unaffordable. */
    fun levelUp(state: GameState, b: Balance = Balance()): GameState? {
        val index = state.cheapestHeroIndex()
        val hero = state.roster[index]
        val cost = b.levelCost(hero.level)
        if (state.gold < cost) return null
        val level = hero.level + 1
        // Levelling grants the extra health outright, so it is never a downgrade.
        val healed = hero.hp + (b.heroMaxHp(hero.cls, level) - hero.maxHp(b))
        return state.copy(
            gold = state.gold - cost,
            party = state.party.replaceAt(index, hero.copy(level = level, hp = min(healed, b.heroMaxHp(hero.cls, level)))),
        )
    }

    /**
     * Spends gold to refill the whole party, which doubles as an instant revive
     * after a wipe. Returns null when there is nothing to heal or nothing to pay
     * with — the caller turns that into a refusal buzz.
     */
    fun drinkPotion(state: GameState, b: Balance = Balance()): GameState? {
        if (!state.canDrinkPotion(b)) return null
        return state.copy(
            gold = state.gold - b.potionCost(state.partyLevel),
            party = state.party.map { it.copy(hp = it.maxHp(b)) },
            downUntilMs = 0L,
        )
    }

    /**
     * The Hero-dric Cube: nine items of one grade go in, one of the next grade
     * comes out. Always fuses the lowest grade that can, so the stash climbs from
     * the bottom the way it fills.
     */
    fun cube(state: GameState, b: Balance = Balance()): Pair<GameState, Int>? {
        val grade = state.fusableGrade(b) ?: return null
        val stash = state.stash.toMutableList()
        stash[grade] -= b.cubeInput
        stash[grade + 1] += 1
        return state.copy(stash = stash) to grade + 1
    }

    private fun step(s0: GameState, nowMs: Long, dt: Double, b: Balance, acc: Accumulator): GameState {
        var s = s0

        if (s.isDown) {
            if (nowMs < s.downUntilMs) return s
            s = s.copy(downUntilMs = 0L)
        }

        val enemyMax = b.enemyMaxHp(s.act, s.wave)
        var enemyHp = (if (s.enemyHp <= 0.0) enemyMax else s.enemyHp) - s.partyDps(b) * dt

        // The monster chews on the front hero; everyone else regenerates.
        val front = s.frontIndex
        if (front < 0) return onWipe(s, nowMs, b, acc)
        val incoming = b.enemyDps(s.act, s.wave) * dt
        s = s.copy(
            party = s.party.mapIndexed { i, hero ->
                if (i >= s.unlocked || hero.isDown) {
                    hero
                } else {
                    val regen = b.heroRegenPerSec(hero.cls, hero.level) * dt
                    val damage = if (i == front) incoming else 0.0
                    hero.copy(hp = (hero.hp - damage + regen).coerceIn(0.0, hero.maxHp(b)))
                }
            },
        )

        if (enemyHp <= 0.0) {
            // The party wins a simultaneous exchange: dying on the killing blow
            // reads as a bug on a home screen.
            s = onKill(s, b, acc)
            enemyHp = b.enemyMaxHp(s.act, s.wave)
        } else if (s.living.isEmpty()) {
            return onWipe(s, nowMs, b, acc)
        }

        s = s.copy(enemyHp = enemyHp)
        s = spendXp(s, b, acc)
        if (s.autoLevel) s = autoBuy(s, b, acc)
        return s
    }

    private fun onKill(s0: GameState, b: Balance, acc: Accumulator): GameState {
        var s = s0
        val boss = b.isBossWave(s.wave)
        val gold = b.goldPerKill(s.act, s.wave)
        val xp = b.xpPerKill(s.act, s.wave)
        acc.xpGained += xp
        s = s.copy(
            gold = s.gold + gold,
            lifetimeGold = s.lifetimeGold + gold,
            xp = s.xp + xp,
            kills = s.kills + 1,
            bossKills = if (boss) s.bossKills + 1 else s.bossKills,
        )
        if (boss) {
            // Bosses are the only source of loot, and loot is the only source of gear.
            val drop = Loot.roll(s.act, s.bossKills)
            s = s.copy(stash = s.stash.plusItem(drop.grade))
            acc.add(GameEvent.BossDown(s.act, drop))
        }

        val idx = s.enemyIdx + 1
        s = if (idx < b.enemiesInWave(s.wave)) {
            s.copy(enemyIdx = idx)
        } else if (s.wave < b.wavesPerAct) {
            s.copy(enemyIdx = 0, wave = s.wave + 1)
        } else {
            acc.add(GameEvent.ActCleared(s.act))
            s.copy(enemyIdx = 0, wave = 1, act = s.act + 1)
        }
        return recruit(s.withDeepest(), b, acc)
    }

    /** Reaching an act is what grows the party — the one thing gold cannot buy. */
    private fun recruit(s: GameState, b: Balance, acc: Accumulator): GameState {
        if (s.unlocked >= s.party.size) return s
        val next = s.party[s.unlocked]
        if (s.act < next.cls.unlockAct) return s
        acc.add(GameEvent.HeroJoined(next.cls))
        return s.copy(
            unlocked = s.unlocked + 1,
            party = s.party.replaceAt(s.unlocked, next.copy(hp = next.maxHp(b))),
        )
    }

    private fun onWipe(s0: GameState, nowMs: Long, b: Balance, acc: Accumulator): GameState {
        acc.add(GameEvent.HeroDown(s0.act, s0.wave))
        // Retreat to the start of the act: gold, levels and loot are kept, the push is not.
        return s0.copy(
            deaths = s0.deaths + 1,
            wave = 1,
            enemyIdx = 0,
            party = s0.party.map { it.copy(hp = it.maxHp(b)) },
            enemyHp = b.enemyMaxHp(s0.act, 1),
            downUntilMs = nowMs + b.downMs,
        )
    }

    private fun spendXp(s0: GameState, b: Balance, acc: Accumulator): GameState {
        var s = s0
        var guard = b.maxAutoBuysPerTick
        while (guard-- > 0 && s.xp >= b.runeCost(s.runes)) {
            s = s.copy(xp = s.xp - b.runeCost(s.runes), runes = s.runes + 1)
            acc.add(GameEvent.RuneGained(s.runes))
        }
        return s
    }

    private fun autoBuy(s0: GameState, b: Balance, acc: Accumulator): GameState {
        var s = s0
        var guard = b.maxAutoBuysPerTick
        while (guard-- > 0) {
            val next = levelUp(s, b) ?: break
            s = next
            acc.add(GameEvent.LevelUp(s.partyLevel))
        }
        return s
    }

    private fun GameState.withDeepest(): GameState =
        if (act > deepestAct || (act == deepestAct && wave > deepestWave)) {
            copy(deepestAct = act, deepestWave = wave)
        } else {
            this
        }

    private fun List<Hero>.replaceAt(index: Int, hero: Hero): List<Hero> =
        mapIndexed { i, existing -> if (i == index) hero else existing }

    private fun List<Int>.plusItem(grade: Int): List<Int> =
        mapIndexed { i, count -> if (i == grade) count + 1 else count }

    private class Accumulator(state: GameState) {
        val events = ArrayDeque<GameEvent>()
        var xpGained = 0.0

        init {
            require(state.party.isNotEmpty()) { "a run needs a party" }
        }

        fun add(e: GameEvent) {
            events.addLast(e)
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
    }
}
