package dev.taskbarhero.game

import kotlin.math.pow
import kotlin.math.sqrt

/** Where a piece of gear goes. Five slots, one item each, no duplicates. */
enum class Slot(val label: String, val sprite: String) {
    WEAPON("WEAPON", dev.taskbarhero.assets.Art.SWORD),
    ARMOUR("ARMOUR", dev.taskbarhero.assets.Art.CHEST),
    HELM("HELM", dev.taskbarhero.assets.Art.GEM),
    BOOTS("BOOTS", dev.taskbarhero.assets.Art.POTION),
    RING("RING", dev.taskbarhero.assets.Art.COIN),
}

/**
 * One piece of gear.
 *
 * Two numbers decide everything: the [grade], which is the colour and the big
 * jump, and the [level], the depth it was found at.
 *
 * The level's bonus is deliberately bounded: it climbs towards one whole grade
 * and never arrives. So a Common dug out of act 40 is still worth less than a
 * Rare from act 3 — which is the only thing that keeps the Cube worth using. Let
 * depth beat grade and fusing becomes a chore nobody would bother with.
 */
data class Item(val slot: Slot, val grade: Int, val level: Int) {

    /** What this piece is worth: the grade sets the step, the level fills part of it. */
    fun power(b: Balance): Double {
        val depth = (level - 1).toDouble()
        val withinGrade = 1.0 + (b.gearPerGrade - 1.0) * (depth / (depth + 30.0))
        return b.gearPerGrade.pow(grade) * withinGrade
    }

    val label: String get() = "${Loot.GRADES[grade]} ${slot.label}"

    /** Compact enough for SharedPreferences, and readable in a bug report. */
    fun encode(): String = "${slot.ordinal}.$grade.$level"

    companion object {
        fun decode(text: String): Item? {
            val parts = text.split('.')
            if (parts.size != 3) return null
            val slot = parts[0].toIntOrNull()?.let { Slot.entries.getOrNull(it) } ?: return null
            val grade = parts[1].toIntOrNull()?.takeIf { it in Loot.GRADES.indices } ?: return null
            val level = parts[2].toIntOrNull()?.takeIf { it >= 1 } ?: return null
            return Item(slot, grade, level)
        }

        /**
         * What an act drops. The grade climbs with the act and the level *is* the
         * act, so a piece carries the depth it was found at.
         */
        fun roll(act: Int, seed: Long): Item {
            val slot = Slot.entries[(seed * 31 + act * 17).mod(Slot.entries.size.toLong()).toInt()]
            val grade = (act - 1).coerceIn(0, Loot.GRADES.lastIndex)
            return Item(slot, grade, act)
        }
    }
}

/**
 * What a party is wearing, and what it is carrying.
 *
 * Equipping is automatic: an idle game the player is not watching cannot ask them
 * to choose, so a drop that beats what is worn simply replaces it and the old
 * piece falls into the stash. The choice this game does offer is what to do with
 * the pile afterwards — which is the Cube's job.
 */
data class Loadout(
    /** One map per hero, in party order. */
    val worn: List<Map<Slot, Item>> = List(3) { emptyMap() },
    val stash: List<Item> = emptyList(),
) {

    /** The multiplier a hero's gear is worth. */
    fun power(hero: Int, b: Balance): Double {
        val score = worn.getOrNull(hero)?.values?.sumOf { it.power(b) } ?: 0.0
        // Square-rooted for the same reason everything else here is: an exponential
        // fed straight back into damage makes the run lap itself.
        return 1.0 + sqrt(score) * 0.30
    }

    /** The party's average, for the one number the bar has room to show. */
    fun bonus(unlocked: Int, b: Balance): Double =
        (0 until unlocked.coerceAtLeast(1)).sumOf { power(it, b) } / unlocked.coerceAtLeast(1)

    /**
     * Takes a drop. It goes on the hero who gains most from it, and whatever it
     * replaces goes to the stash — nothing is ever thrown away without passing
     * through the pile the Cube eats from.
     */
    fun take(item: Item, unlocked: Int, b: Balance): Loadout {
        val heroes = (0 until unlocked.coerceIn(1, worn.size))
        val best = heroes.maxByOrNull { hero ->
            val current = worn[hero][item.slot]
            item.power(b) - (current?.power(b) ?: 0.0)
        } ?: 0

        val current = worn[best][item.slot]
        if (current != null && current.power(b) >= item.power(b)) {
            return copy(stash = (stash + item).takeLast(STASH_LIMIT))
        }
        val next = worn.toMutableList()
        next[best] = next[best] + (item.slot to item)
        val pile = if (current == null) stash else stash + current
        return copy(worn = next, stash = pile.takeLast(STASH_LIMIT))
    }

    /** The lowest grade with enough copies in the stash to fuse. */
    fun fusableGrade(b: Balance): Int? = (0 until Loot.GRADES.lastIndex).firstOrNull { grade ->
        stash.count { it.grade == grade } >= b.cubeInput
    }

    /** How many of the closest-to-fusing grade are in the pile, for the button. */
    fun towardsFusion(b: Balance): Pair<Int, Int> {
        val counts = (0 until Loot.GRADES.lastIndex).map { it to stash.count { i -> i.grade == it } }
        val best = counts.maxByOrNull { it.second } ?: (0 to 0)
        return best.first to best.second
    }

    /**
     * The Hero-dric Cube: nine of a grade become one of the next.
     *
     * Always the lowest grade that can, so the pile rises from the bottom and a
     * heap of commons is worth keeping instead of being a nuisance.
     */
    fun cube(b: Balance): Pair<Loadout, Item>? {
        val grade = fusableGrade(b) ?: return null
        val eaten = stash.filter { it.grade == grade }.take(b.cubeInput)
        val made = Item(
            slot = eaten.first().slot,
            grade = grade + 1,
            // The new piece keeps the best level that went into it: fusing must
            // never hand back something worse than what it ate.
            level = eaten.maxOf { it.level },
        )
        var left = stash.toMutableList()
        for (item in eaten) left.remove(item)
        return copy(stash = left + made) to made
    }

    fun encode(): String = buildString {
        append(worn.joinToString("|") { map -> map.values.joinToString(",") { it.encode() } })
        append('#')
        append(stash.joinToString(",") { it.encode() })
    }

    companion object {
        /** The pile is bounded: an idle game left running for a week must not grow forever. */
        const val STASH_LIMIT = 60

        fun decode(text: String): Loadout {
            val (wornText, stashText) = text.split('#').let {
                (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "")
            }
            val worn = wornText.split('|').map { hero ->
                hero.split(',').mapNotNull(Item::decode).associateBy { it.slot }
            }
            return Loadout(
                worn = if (worn.size == 3) worn else List(3) { emptyMap() },
                stash = stashText.split(',').mapNotNull(Item::decode),
            )
        }
    }
}
