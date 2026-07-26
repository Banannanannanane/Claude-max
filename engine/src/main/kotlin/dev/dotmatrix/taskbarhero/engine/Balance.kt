package dev.dotmatrix.taskbarhero.engine

import kotlin.math.pow

/**
 * Every tunable number of the idle loop. Kept as a value class so tests can
 * shrink the curves instead of simulating hours of real time.
 */
data class Balance(
    /** Simulation granularity. Small enough to feel alive, coarse enough to catch up on 12 h fast. */
    val tickMs: Long = 250L,
    /** Offline progress is generous but bounded, so a widget that slept for a week still redraws instantly. */
    val maxCatchUpMs: Long = 12L * 60L * 60L * 1000L,
    val enemiesPerWave: Int = 4,
    val wavesPerAct: Int = 10,
    /** Time the hero stays down after a wipe, before restarting the act. */
    val downMs: Long = 4_000L,
    /** Safety valve: how many auto purchases a single tick may resolve. */
    val maxAutoBuysPerTick: Int = 8,
) {
    fun isBossWave(wave: Int): Boolean = wave >= wavesPerAct

    fun enemiesInWave(wave: Int): Int = if (isBossWave(wave)) 1 else enemiesPerWave

    fun heroDps(level: Int, runes: Int): Double =
        5.0 * 1.16.pow(level - 1) * (1.0 + 0.02 * runes)

    fun heroMaxHp(level: Int): Double = 70.0 * 1.12.pow(level - 1)

    fun heroRegenPerSec(level: Int): Double = heroMaxHp(level) * 0.05

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

    /** Gold price of the next hero level — the only thing the widget button spends. */
    fun levelCost(level: Int): Double = 15.0 * 1.17.pow(level - 1)

    /** XP price of the next rune, the passive damage multiplier that accrues on its own. */
    fun runeCost(runes: Int): Double = 40.0 * 1.28.pow(runes)
}
