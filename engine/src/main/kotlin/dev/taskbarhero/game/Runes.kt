package dev.taskbarhero.game

/**
 * The rune tree: what a run keeps when everything else is just numbers going up.
 *
 * Runes come from bosses, one each, and never from gold — so the only way to buy
 * one is to get further than last time. That is what makes them the spine of the
 * progression rather than another thing to spend on.
 */
enum class Rune(val label: String, val icon: String, val ranks: Int, val note: String) {
    POWER("POWER", "RUNE_POWER", 6, "DAMAGE"),
    VIGOUR("VIGOUR", "RUNE_VIGOUR", 6, "HEALTH"),
    GOLD("PLUNDER", "RUNE_GOLD", 6, "GOLD"),
    HASTE("HASTE", "RUNE_HASTE", 5, "CHEAPER LEVELS"),
    REST("REST", "RUNE_REST", 6, "OFFLINE HOURS");

    /**
     * What the next rank costs. Rank one is a single rune, and each after that
     * costs one more — so a maxed rune is 21 bosses, and spreading is cheap while
     * specialising is not.
     */
    fun costOf(rank: Int): Int = rank + 1
}

/**
 * Runes earned, and where they were spent.
 *
 * [earned] is the running total, not the balance: keeping the total means a
 * refund can never lose count of what a run is owed.
 */
data class RuneState(
    val earned: Int = 0,
    val spent: Map<Rune, Int> = emptyMap(),
) {

    fun rank(rune: Rune): Int = spent[rune] ?: 0

    /** Runes actually paid out, which is what [available] is measured against. */
    val used: Int get() = Rune.entries.sumOf { rune -> (0 until rank(rune)).sumOf { rune.costOf(it) } }

    val available: Int get() = earned - used

    fun canBuy(rune: Rune): Boolean =
        rank(rune) < rune.ranks && available >= rune.costOf(rank(rune))

    fun buy(rune: Rune): RuneState? {
        if (!canBuy(rune)) return null
        return copy(spent = spent + (rune to rank(rune) + 1))
    }

    // --- what the ranks are worth --------------------------------------------

    val damage: Double get() = 1.0 + 0.12 * rank(Rune.POWER)
    val health: Double get() = 1.0 + 0.12 * rank(Rune.VIGOUR)
    val gold: Double get() = 1.0 + 0.20 * rank(Rune.GOLD)

    /** Never past a floor: a discount that reaches free would end the game. */
    val levelCost: Double get() = (1.0 - 0.07 * rank(Rune.HASTE)).coerceAtLeast(0.5)

    /** Extra hours the game keeps simulating while the phone is in a pocket. */
    val offlineBonusMs: Long get() = rank(Rune.REST) * 2L * 60L * 60L * 1000L

    fun encode(): String = "$earned:" + Rune.entries.joinToString(",") { rank(it).toString() }

    companion object {
        fun decode(text: String): RuneState {
            val head = text.substringBefore(':')
            val ranks = text.substringAfter(':', "").split(',').map { it.toIntOrNull() ?: 0 }
            return RuneState(
                earned = head.toIntOrNull() ?: 0,
                spent = Rune.entries.withIndex()
                    .associate { (i, rune) -> rune to (ranks.getOrNull(i) ?: 0).coerceIn(0, rune.ranks) }
                    .filterValues { it > 0 },
            )
        }
    }
}
