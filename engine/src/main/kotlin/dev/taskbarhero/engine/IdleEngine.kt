package dev.taskbarhero.engine

import kotlin.math.min

/**
 * The auto-battler. Nothing here touches Android, a clock, or the disk: given a
 * state and a wall-clock instant it replays the fight tick by tick, which is
 * what lets a home-screen widget stay correct while being redrawn once a minute
 * (or once a day).
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

    /** Buys one hero level if it is affordable. Returns null when it is not. */
    fun levelUp(state: GameState, b: Balance = Balance()): GameState? {
        val cost = b.levelCost(state.level)
        if (state.gold < cost) return null
        val level = state.level + 1
        // Levelling heals the missing share of the new, larger health pool.
        val healed = state.heroHp + (b.heroMaxHp(level) - b.heroMaxHp(state.level))
        return state.copy(
            gold = state.gold - cost,
            level = level,
            heroHp = min(healed, b.heroMaxHp(level)),
        )
    }

    private fun step(s0: GameState, nowMs: Long, dt: Double, b: Balance, acc: Accumulator): GameState {
        var s = s0

        if (s.isDown) {
            if (nowMs < s.downUntilMs) return s
            s = s.copy(downUntilMs = 0L)
        }

        val heroDps = b.heroDps(s.level, s.runes)
        val enemyMax = b.enemyMaxHp(s.act, s.wave)
        val heroMax = b.heroMaxHp(s.level)

        var enemyHp = (if (s.enemyHp <= 0.0) enemyMax else s.enemyHp) - heroDps * dt
        var heroHp = min(
            heroMax,
            (if (s.heroHp <= 0.0) heroMax else s.heroHp) - b.enemyDps(s.act, s.wave) * dt + b.heroRegenPerSec(s.level) * dt,
        )

        if (enemyHp <= 0.0) {
            // The hero wins a simultaneous exchange: dying on the killing blow
            // reads as a bug on a home screen.
            s = onKill(s, b, acc)
            enemyHp = b.enemyMaxHp(s.act, s.wave)
            heroHp = heroHp.coerceAtLeast(1.0)
        } else if (heroHp <= 0.0) {
            s = onWipe(s, nowMs, b, acc)
            return s
        }

        s = s.copy(heroHp = heroHp, enemyHp = enemyHp)
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
        if (boss) acc.add(GameEvent.BossDown(s.act, Loot.roll(s.act, s.bossKills)))

        val idx = s.enemyIdx + 1
        s = if (idx < b.enemiesInWave(s.wave)) {
            s.copy(enemyIdx = idx)
        } else if (s.wave < b.wavesPerAct) {
            s.copy(enemyIdx = 0, wave = s.wave + 1)
        } else {
            acc.add(GameEvent.ActCleared(s.act))
            s.copy(enemyIdx = 0, wave = 1, act = s.act + 1)
        }
        return s.withDeepest()
    }

    private fun onWipe(s0: GameState, nowMs: Long, b: Balance, acc: Accumulator): GameState {
        acc.add(GameEvent.HeroDown(s0.act, s0.wave))
        // Retreat to the start of the act: gold and levels are kept, the push is not.
        return s0.copy(
            deaths = s0.deaths + 1,
            wave = 1,
            enemyIdx = 0,
            heroHp = b.heroMaxHp(s0.level),
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
            acc.add(GameEvent.LevelUp(s.level))
        }
        return s
    }

    private fun GameState.withDeepest(): GameState =
        if (act > deepestAct || (act == deepestAct && wave > deepestWave)) {
            copy(deepestAct = act, deepestWave = wave)
        } else {
            this
        }

    private class Accumulator(state: GameState) {
        val events = ArrayDeque<GameEvent>()
        var xpGained = 0.0

        init {
            require(state.level >= 1) { "level must be >= 1" }
        }

        fun add(e: GameEvent) {
            events.addLast(e)
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
    }
}
