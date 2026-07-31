package dev.taskbarhero.game

import kotlin.math.pow

/**
 * The three classes, and what each is for.
 *
 * A party is not one hero with a bigger number: the knight is what keeps the
 * other two alive, and the mage is what kills things before they kill him. The
 * multipliers are the whole of that difference.
 */
enum class HeroClass(
    val label: String,
    val sprite: String,
    val dps: Double,
    val hp: Double,
    /** The act that recruits them. The only progress gold cannot buy. */
    val unlockAct: Int,
) {
    KNIGHT("KNIGHT", dev.taskbarhero.assets.Art.KNIGHT, dps = 0.80, hp = 1.70, unlockAct = 1),
    RANGER("RANGER", dev.taskbarhero.assets.Art.RANGER, dps = 1.15, hp = 0.85, unlockAct = 2),
    MAGE("MAGE", dev.taskbarhero.assets.Art.MAGE, dps = 1.45, hp = 0.60, unlockAct = 4),
}

/**
 * Every curve in the game, in one place and with no state, so a test can shrink a
 * ten-hour run into a millisecond instead of simulating one.
 */
data class Balance(
    // An idle curve is one equation: what the player gains in an act has to be
    // worth slightly more than what the act asks for. Everything below is that
    // equation, and every number is chosen against the others.
    //
    // Gold grows goldActGrowth per act, and a level costs levelCostGrowth more
    // than the last, so the levels an act can buy is a constant:
    //   L = ln(4.3) / ln(1.20) = 8 levels per act, forever.
    // Which fixes what the party is worth an act later: heroGrowth^L = 1.14^8 =
    // 2.9x. So the monsters have to grow by about that much too — and slightly
    // more, or the run accelerates until it laps itself. 3.0 against 2.9 is the
    // whole difficulty curve: each act costs a little more than the last paid for,
    // and the gap is what the stash and the Cube are for.
    //
    // Comparing per-act numbers is the trap here: the wave factor resets at every
    // act, so it never compounds across them. Only these three do.
    val heroBaseHp: Double = 34.0,
    val heroBaseDps: Double = 3.0,
    val heroGrowth: Double = 1.14,

    val enemyBaseHp: Double = 34.0,
    val enemyActGrowth: Double = 3.05,
    val enemyWaveGrowth: Double = 1.06,
    val enemyBaseDps: Double = 2.3,
    val enemyDpsActGrowth: Double = 2.72,

    val bossHp: Double = 5.0,
    val bossDps: Double = 1.8,

    val goldBase: Double = 7.0,
    val goldActGrowth: Double = 4.0,
    val bossGold: Double = 8.0,

    val levelCostBase: Double = 22.0,
    val levelCostGrowth: Double = 1.20,

    /** Waves in an act. The last is the boss. */
    val wavesPerAct: Int = 10,

    /** Nine of a grade make one of the next. The Hero-dric Cube, unchanged. */
    val cubeInput: Int = 9,

    /** What one grade of gear is worth. */
    val gearPerGrade: Double = 1.7,

    /** How long a wiped party stays down. */
    val downMs: Long = 4_000L,

    /** Idle is credited up to here and no further. */
    val offlineCapMs: Long = 12 * 60 * 60 * 1000L,
) {

    fun heroMaxHp(cls: HeroClass, level: Int): Double =
        heroBaseHp * cls.hp * heroGrowth.pow(level - 1)

    /** A hero's damage, gear included — gear is the stash, so it lifts the party as one. */
    fun heroDps(cls: HeroClass, level: Int, gear: Double): Double =
        heroBaseDps * cls.dps * heroGrowth.pow(level - 1) * gear

    fun isBossWave(wave: Int): Boolean = wave >= wavesPerAct

    fun enemyMaxHp(act: Int, wave: Int): Double {
        val base = enemyBaseHp * enemyActGrowth.pow(act - 1) * enemyWaveGrowth.pow(wave - 1)
        return if (isBossWave(wave)) base * bossHp else base
    }

    fun enemyDps(act: Int, wave: Int): Double {
        val base = enemyBaseDps * enemyDpsActGrowth.pow(act - 1) * (1.0 + 0.06 * (wave - 1))
        return if (isBossWave(wave)) base * bossDps else base
    }

    fun goldFor(act: Int, wave: Int): Double {
        val base = goldBase * goldActGrowth.pow(act - 1) * (1.0 + 0.12 * (wave - 1))
        return if (isBossWave(wave)) base * bossGold else base
    }

    /** What the next level of a hero costs. Levels are bought one at a time. */
    fun levelCost(level: Int): Double = levelCostBase * levelCostGrowth.pow(level - 1)

    /** A potion heals the whole party, so it is priced off the whole party. */
    fun potionCost(partyLevel: Int): Double = levelCostBase * 2.4 * levelCostGrowth.pow(partyLevel - 1)

    /**
     * Damage multiplier from the stash. A grade is worth 1.7 of the one below, so
     * the raw score is exponential — and feeding an exponential straight back into
     * damage is what turns an idle game into a runaway: more damage, faster acts,
     * better loot, more damage. The square root is the brake. Gear is then a real
     * reward that never becomes the whole engine.
     */
    fun gear(stash: List<Int>): Double {
        var score = 0.0
        for ((grade, count) in stash.withIndex()) score += count * gearPerGrade.pow(grade)
        return 1.0 + kotlin.math.sqrt(score) * 0.22
    }
}

/** Boss drops: a grade, and a name for it. The grade is what the colour shows. */
object Loot {

    /** The ten grades, lowest first. */
    val GRADES = listOf(
        "COMMON", "UNCOMMON", "RARE", "LEGENDARY", "IMMORTAL",
        "ARCANA", "BEYOND", "CELESTIAL", "DIVINE", "COSMIC",
    )

    private val ITEMS = listOf("BLADE", "PLATE", "CLOAK", "RING", "SHARD", "IDOL", "TOTEM", "RELIC")

    data class Drop(val grade: Int, val item: String) {
        val label: String get() = "${GRADES[grade]} $item"
    }

    /** Deeper acts drop better: act 1 is COMMON, act 10 and beyond COSMIC. */
    fun roll(act: Int, bossKills: Long): Drop {
        val item = ITEMS[(act * 31 + bossKills.toInt() * 17).mod(ITEMS.size)]
        return Drop((act - 1).coerceIn(0, GRADES.lastIndex), item)
    }
}

/**
 * Which monster stands in a given slot.
 *
 * Deterministic on purpose: the same (act, wave, index) always gives the same
 * monster, so a widget redrawn twice in a row does not flicker between two
 * sprites. The window of eligible monsters slides up the ladder with the act, and
 * never narrows below three — a wave should not become one monster on repeat.
 */
object Bestiary {

    private val NAMES = dev.taskbarhero.assets.Art.MONSTERS

    private val BOSSES = listOf(
        "SLIME KING", "PLAGUE RAT", "BROOD MOTHER", "CARAPACE",
        "THE PALE ONE", "DEEP CRAB", "WARLORD", "THE FIRST",
    )

    data class Mob(val name: String, val sprite: String, val isBoss: Boolean = false)

    fun mob(act: Int, wave: Int, index: Int, b: Balance = Balance()): Mob {
        if (b.isBossWave(wave)) {
            return Mob(BOSSES[(act - 1).mod(BOSSES.size)], dev.taskbarhero.assets.Art.BOSS, isBoss = true)
        }
        val top = (2 + act).coerceAtMost(NAMES.size)
        val floor = ((act - 2) / 2).coerceIn(0, NAMES.size - 3)
        val pick = floor + hash(act, wave, index).mod(top - floor)
        return Mob(NAMES[pick], NAMES[pick])
    }

    private fun hash(a: Int, w: Int, i: Int): Int {
        var h = a * 73_856_093 xor w * 19_349_663 xor i * 83_492_791
        h = h xor (h ushr 13)
        return h and 0x7fff_ffff
    }
}
