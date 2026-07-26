package dev.taskbarhero.engine

/**
 * The whole save file. Flat and primitive-heavy on purpose: it round-trips
 * through SharedPreferences without a serialization dependency, and it makes the
 * simulation a pure function of (state, elapsed time).
 */
data class GameState(
    /** Wall clock of the last simulated tick. 0 means "never ticked yet". */
    val lastTickMs: Long = 0L,
    /** Always three entries; [unlocked] says how many are actually fighting. */
    val party: List<Hero> = emptyList(),
    /** Party members recruited so far, 1..3. */
    val unlocked: Int = 1,
    /** Items held per loot grade, lowest first — the Cube's raw material. */
    val stash: List<Int> = List(Loot.GRADES.size) { 0 },
    val runes: Int = 0,
    val gold: Double = 0.0,
    val xp: Double = 0.0,
    val act: Int = 1,
    val wave: Int = 1,
    /** Index of the current enemy inside the wave. */
    val enemyIdx: Int = 0,
    val enemyHp: Double = 0.0,
    /** Wall clock until which the party is wiped; 0 while fighting. */
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

    /** The heroes actually in the fight. */
    val roster: List<Hero> get() = party.take(unlocked)

    val living: List<Hero> get() = roster.filterNot { it.isDown }

    /** Whoever the monsters are chewing on: the first hero still standing. */
    val frontIndex: Int get() = roster.indexOfFirst { !it.isDown }

    val partyLevel: Int get() = roster.sumOf { it.level }

    /** Damage multiplier earned by everything sitting in the stash. */
    fun gearPower(b: Balance): Double = b.gearPower(stash)

    fun partyDps(b: Balance): Double {
        val gear = gearPower(b)
        return living.sumOf { it.dps(b, runes, gear) }
    }

    fun enemyHpFraction(b: Balance): Double =
        (enemyHp / b.enemyMaxHp(act, wave)).coerceIn(0.0, 1.0)

    fun runeProgress(b: Balance): Double = (xp / b.runeCost(runes)).coerceIn(0.0, 1.0)

    /** The hero LV UP would buy for: the cheapest, so the party levels evenly. */
    fun cheapestHeroIndex(): Int {
        var best = 0
        for (i in roster.indices) if (roster[i].level < roster[best].level) best = i
        return best
    }

    fun levelCost(b: Balance): Double = b.levelCost(roster[cheapestHeroIndex()].level)

    fun canLevelUp(b: Balance): Boolean = gold >= levelCost(b)

    /** A potion is worth buying only when it would actually do something. */
    fun canDrinkPotion(b: Balance): Boolean =
        gold >= b.potionCost(partyLevel) && (isDown || roster.any { it.hp < it.maxHp(b) - 1e-9 })

    /** The fusable grade holding the most items — what the Cube is waiting on. */
    fun fullestGrade(): Int? =
        stash.indices.filter { it < stash.lastIndex && stash[it] > 0 }.maxByOrNull { stash[it] }

    /** The lowest grade holding enough items to fuse, or null when none does. */
    fun fusableGrade(b: Balance): Int? =
        stash.indices.firstOrNull { it < stash.lastIndex && stash[it] >= b.cubeInput }

    companion object {
        fun newRun(nowMs: Long, b: Balance = Balance()): GameState = GameState(
            lastTickMs = nowMs,
            party = Hero.startingParty(b),
            enemyHp = b.enemyMaxHp(1, 1),
        )
    }
}
