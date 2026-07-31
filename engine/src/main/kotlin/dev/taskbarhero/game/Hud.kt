package dev.taskbarhero.game

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Every string and every fraction the screen shows, worked out once.
 *
 * The painter is given this and nothing else, so it never reaches into the save
 * and never decides what a number means — and so a test can assert on exactly the
 * text a player will read.
 */
data class Hud(
    val stage: String,
    val level: String,
    val gold: String,
    val enemyName: String,
    val enemySprite: String,
    val enemyFraction: Double,
    val party: List<HeroHud>,
    val frontFraction: Double,
    val isDown: Boolean,
    val levelCost: String,
    val canLevelUp: Boolean,
    val potionCost: String,
    val canDrinkPotion: Boolean,
    val cubeLabel: String,
    val canCube: Boolean,
    val cubeGrade: Int,
    val autoOn: Boolean,
    val gearBonus: String,
    val deepest: String,
    val kills: String,
    val loadout: Loadout,
) {
    companion object {
        fun of(state: GameState, b: Balance = Balance(), nowMs: Long = state.lastTickMs): Hud {
            val mob = Bestiary.mob(state.act, state.wave, state.enemyIndex, b)
            val down = state.isDown(nowMs)
            val fusable = state.fusableGrade(b)
            val (towards, held) = state.loadout.towardsFusion(b)
            val front = state.roster.getOrNull(state.frontIndex)

            return Hud(
                stage = "${state.act}-${state.wave.toString().padStart(2, '0')}",
                level = "LV ${state.partyLevel}",
                gold = Fmt.short(state.gold),
                enemyName = mob.name,
                enemySprite = mob.sprite,
                enemyFraction = (state.enemyHp / b.enemyMaxHp(state.act, state.wave)).coerceIn(0.0, 1.0),
                party = state.roster.map { hero ->
                    HeroHud(
                        cls = hero.cls,
                        level = "LV ${hero.level}",
                        fraction = (hero.hp / hero.maxHp(b)).coerceIn(0.0, 1.0),
                        down = hero.hp <= 0.0,
                    )
                },
                frontFraction = front?.let { (it.hp / it.maxHp(b)).coerceIn(0.0, 1.0) } ?: 0.0,
                isDown = down,
                levelCost = Fmt.short(state.levelCost(b)),
                canLevelUp = state.canLevelUp(b),
                potionCost = Fmt.short(state.potionCost(b)),
                canDrinkPotion = state.canDrinkPotion(b),
                // The button explains what it is waiting for rather than sitting mute.
                cubeLabel = if (fusable != null) "READY" else "$held/${b.cubeInput}",
                canCube = fusable != null,
                cubeGrade = fusable?.plus(1) ?: towards,
                autoOn = state.autoLevel,
                gearBonus = "+${((state.gear(b) - 1.0) * 100).toInt()}%",
                deepest = "${state.deepestAct}-${state.deepestWave.toString().padStart(2, '0')}",
                kills = Fmt.short(state.kills.toDouble()),
                loadout = state.loadout,
            )
        }
    }
}

data class HeroHud(
    val cls: HeroClass,
    val level: String,
    val fraction: Double,
    val down: Boolean,
)

/** Idle-game numbers: short enough for a bar, precise enough to watch tick up. */
object Fmt {

    /**
     * "", K, M, B, T, then aa, ab, ac … zz — the idle-game convention, and enough
     * range that a twelve-hour run cannot walk off the end of it.
     */
    private val SUFFIXES: List<String> = buildList {
        addAll(listOf("", "K", "M", "B", "T"))
        for (a in 'a'..'z') for (b in 'a'..'z') add("$a$b")
    }

    fun short(value: Double): String {
        if (!value.isFinite()) return "MAX"
        val v = abs(value)
        if (v < 1_000.0) return v.toLong().toString()

        // Clamped, not wrapped: a number past the last suffix is still a number,
        // and toLong() on a Double that big silently prints Long.MAX_VALUE.
        val tier = floor(log10(v) / 3.0).toInt().coerceIn(1, SUFFIXES.lastIndex)
        val scaled = v / 1000.0.pow(tier)
        // One decimal only while it buys precision: 12.4K, but 999K not 999.4K.
        val text = if (scaled < 100.0) {
            val tenths = (scaled * 10).toLong()
            "${tenths / 10}.${tenths % 10}"
        } else {
            scaled.toLong().toString()
        }
        return text + SUFFIXES[tier]
    }
}
