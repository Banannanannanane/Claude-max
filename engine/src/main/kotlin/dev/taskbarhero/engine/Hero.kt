package dev.taskbarhero.engine

/**
 * The party. TBH fights with up to three pixel heroes rather than one, and the
 * three pull in different directions: the knight soaks, the ranger trades, the
 * mage melts things and dies to a stiff breeze.
 *
 * [unlockAct] is how the party grows — reaching an act recruits the next member,
 * which is the only progression in the game that is not bought.
 */
enum class HeroClass(
    val label: String,
    val sprite: SpriteKey,
    /** Damage, as a share of the baseline. */
    val dps: Double,
    /** Health, likewise. The sum across a full party is deliberately > 3. */
    val hp: Double,
    val unlockAct: Int,
) {
    KNIGHT("KNIGHT", SpriteKey.KNIGHT, dps = 0.80, hp = 1.70, unlockAct = 1),
    RANGER("RANGER", SpriteKey.RANGER, dps = 1.15, hp = 0.85, unlockAct = 2),
    MAGE("MAGE", SpriteKey.MAGE, dps = 1.45, hp = 0.60, unlockAct = 4),
}

/**
 * One party member. Levels are bought individually, health is tracked
 * individually, and a hero at zero is down until the party wipes or drinks.
 */
data class Hero(val cls: HeroClass, val level: Int = 1, val hp: Double = 0.0) {
    val isDown: Boolean get() = hp <= 0.0

    fun maxHp(b: Balance): Double = b.heroMaxHp(cls, level)

    fun hpFraction(b: Balance): Double = (hp / maxHp(b)).coerceIn(0.0, 1.0)

    fun dps(b: Balance, runes: Int, gear: Double): Double = b.heroDps(cls, level, runes, gear)

    companion object {
        fun startingParty(b: Balance): List<Hero> =
            HeroClass.entries.map { Hero(it, level = 1, hp = b.heroMaxHp(it, 1)) }
    }
}
