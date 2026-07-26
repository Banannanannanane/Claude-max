package dev.taskbarhero.engine

/**
 * The whole save file. Flat and primitive-only on purpose: it round-trips
 * through SharedPreferences without a serialization dependency, and it makes
 * the simulation a pure function of (state, elapsed time).
 */
data class GameState(
    /** Wall clock of the last simulated tick. 0 means "never ticked yet". */
    val lastTickMs: Long = 0L,
    val level: Int = 1,
    val runes: Int = 0,
    val gold: Double = 0.0,
    val xp: Double = 0.0,
    val act: Int = 1,
    val wave: Int = 1,
    /** Index of the current enemy inside the wave. */
    val enemyIdx: Int = 0,
    val heroHp: Double = 0.0,
    val enemyHp: Double = 0.0,
    /** Wall clock until which the hero is DOWN; 0 when fighting. */
    val downUntilMs: Long = 0L,
    val kills: Long = 0L,
    val bossKills: Long = 0L,
    val deaths: Long = 0L,
    val deepestAct: Int = 1,
    val deepestWave: Int = 1,
    val lifetimeGold: Double = 0.0,
    /** When on, the engine spends gold on levels by itself — true taskbar idling. */
    val autoLevel: Boolean = false,
) {
    val isDown: Boolean get() = downUntilMs != 0L

    fun heroHpFraction(b: Balance): Double =
        (heroHp / b.heroMaxHp(level)).coerceIn(0.0, 1.0)

    fun enemyHpFraction(b: Balance): Double =
        (enemyHp / b.enemyMaxHp(act, wave)).coerceIn(0.0, 1.0)

    fun runeProgress(b: Balance): Double =
        (xp / b.runeCost(runes)).coerceIn(0.0, 1.0)

    fun canLevelUp(b: Balance): Boolean = gold >= b.levelCost(level)

    /** A potion is worth buying only when it would actually do something. */
    fun canDrinkPotion(b: Balance): Boolean =
        gold >= b.potionCost(level) && (isDown || heroHp < b.heroMaxHp(level) - 1e-9)

    companion object {
        fun newRun(nowMs: Long, b: Balance = Balance()): GameState = GameState(
            lastTickMs = nowMs,
            heroHp = b.heroMaxHp(1),
            enemyHp = b.enemyMaxHp(1, 1),
        )
    }
}
