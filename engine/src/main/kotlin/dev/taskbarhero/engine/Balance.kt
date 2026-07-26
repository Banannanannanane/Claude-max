package dev.taskbarhero.engine

import kotlin.math.pow

/**
 * Every tunable number of the idle loop. Kept as a value class so tests can
 * shrink the curves instead of simulating hours of real time.
 */
data class Balance(
    /** Simulation granularity. Small enough to feel alive, coarse enough to catch up on 12 h fast. */
    val tickMs: Long = 250L,
    /** Offline progress is generous but bounded, so a widget that slept a week still redraws instantly. */
    val maxCatchUpMs: Long = 12L * 60L * 60L * 1000L,
    val enemiesPerWave: Int = 4,
    val wavesPerAct: Int = 10,
    /** Time the party stays wiped before restarting the act. */
    val downMs: Long = 4_000L,
    /** Safety valve: how many auto purchases a single tick may resolve. */
    val maxAutoBuysPerTick: Int = 8,
    /** Items of one grade the Cube swallows to hand back one of the next. */
    val cubeInput: Int = 9,
) {
    fun isBossWave(wave: Int): Boolean = wave >= wavesPerAct

    fun enemiesInWave(wave: Int): Int = if (isBossWave(wave)) 1 else enemiesPerWave

    fun heroDps(cls: HeroClass, level: Int, runes: Int, gear: Double): Double =
        5.0 * cls.dps * 1.16.pow(level - 1) * (1.0 + 0.02 * runes) * gear

    fun heroMaxHp(cls: HeroClass, level: Int): Double = 70.0 * cls.hp * 1.12.pow(level - 1)

    fun heroRegenPerSec(cls: HeroClass, level: Int): Double = heroMaxHp(cls, level) * 0.05

    fun enemyMaxHp(act: Int, wave: Int): Double {
        val base = 26.0 * 3.0.pow(act - 1) * 1.17.pow(wave - 1)
        return if (isBossWave(wave)) base * 5.0 else base
    }

    fun enemyDps(act: Int, wave: Int): Double {
        val base = 2.6 * 2.7.pow(act - 1) * 1.12.pow(wave - 1)
        return if (isBossWave(wave)) base * 1.8 else base
    }

    fun goldPerKill(act: Int, wave: Int): Double {
        val base = 4.0 * 2.6.pow(act - 1) * 1.14.pow(wave - 1)
        return if (isBossWave(wave)) base * 8.0 else base
    }

    fun xpPerKill(act: Int, wave: Int): Double {
        val base = 3.0 * 2.4.pow(act - 1) * 1.12.pow(wave - 1)
        return if (isBossWave(wave)) base * 6.0 else base
    }

    /** Gold price of one hero's next level. */
    fun levelCost(level: Int): Double = 15.0 * 1.17.pow(level - 1)

    /** XP price of the next rune, the passive damage multiplier that accrues on its own. */
    fun runeCost(runes: Int): Double = 40.0 * 1.28.pow(runes)

    /**
     * Gold price of a full party heal. Scales with the party's combined level, so
     * it stays around half a level: cheap enough to save a boss fight, dear enough
     * that spamming it costs you the next level.
     */
    fun potionCost(partyLevel: Int): Double = 8.0 * 1.17.pow(partyLevel / 2.0)

    /**
     * What the stash is worth in damage. Each grade is worth far more than the one
     * below it, which is the whole reason to feed the Cube rather than hoard.
     */
    fun gearPower(stash: List<Int>): Double {
        var power = 1.0
        for (grade in stash.indices) power += stash[grade] * 0.02 * 1.7.pow(grade)
        return power
    }
}
