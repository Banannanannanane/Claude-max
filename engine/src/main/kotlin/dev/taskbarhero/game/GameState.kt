package dev.taskbarhero.game

/** One hero, as saved. Health is absolute so a level-up cannot heal by accident. */
data class Hero(val cls: HeroClass, val level: Int = 1, val hp: Double = 0.0) {
    fun maxHp(b: Balance): Double = b.heroMaxHp(cls, level)
    fun isDown(b: Balance): Boolean = hp <= 0.0
}

/**
 * The whole save.
 *
 * Immutable, and complete: everything the simulation needs is here, so advancing
 * the game is a pure function of this plus a clock. That is what lets the widget
 * be redrawn at moments nobody chooses and still show the truth.
 */
data class GameState(
    val party: List<Hero>,
    /** How many of [party] have been recruited. The rest are shown greyed out. */
    val unlocked: Int = 1,
    val act: Int = 1,
    val wave: Int = 1,
    val enemyHp: Double = 0.0,
    val enemyIndex: Int = 0,
    val gold: Double = 0.0,
    /** What the party wears, and the pile the Cube eats from. */
    val loadout: Loadout = Loadout(),
    val kills: Long = 0,
    val bossKills: Long = 0,
    val wipes: Long = 0,
    val deepestAct: Int = 1,
    val deepestWave: Int = 1,
    /** Earned from bosses, spent on the tree. The only progress gold cannot buy. */
    val runes: RuneState = RuneState(),
    val autoLevel: Boolean = false,
    /** Wall clock the simulation has been advanced to. */
    val lastTickMs: Long = 0L,
    /** The party is out of the fight until this instant. */
    val downUntilMs: Long = 0L,
) {

    val partyLevel: Int get() = roster.minOfOrNull { it.level } ?: 1

    /** The heroes actually in the fight. */
    val roster: List<Hero> get() = party.take(unlocked)

    /** The one taking the hits, and the one the monster faces. */
    val frontIndex: Int get() = roster.indexOfFirst { it.hp > 0.0 }.let { if (it < 0) 0 else it }

    fun isDown(nowMs: Long): Boolean = nowMs < downUntilMs

    fun gear(b: Balance): Double = loadout.bonus(unlocked, b)

    fun partyDps(b: Balance): Double =
        roster.withIndex().filter { it.value.hp > 0.0 }.sumOf { (i, hero) ->
            b.heroDps(hero.cls, hero.level, loadout.power(i, b))
        } * runes.damage

    fun heroMaxHp(hero: Hero, b: Balance): Double = b.heroMaxHp(hero.cls, hero.level) * runes.health

    /** The hero a level-up should go to: the cheapest, so the party rises as a block. */
    fun cheapestHeroIndex(): Int =
        roster.indices.minByOrNull { roster[it].level } ?: 0

    fun levelCost(b: Balance): Double =
        b.levelCost(roster[cheapestHeroIndex()].level) * runes.levelCost

    fun canLevelUp(b: Balance): Boolean = gold >= levelCost(b)

    fun potionCost(b: Balance): Double = b.potionCost(partyLevel)

    fun canDrinkPotion(b: Balance): Boolean =
        gold >= potionCost(b) && roster.any { it.hp < heroMaxHp(it, b) }

    fun fusableGrade(b: Balance): Int? = loadout.fusableGrade(b)

    companion object {
        fun newRun(nowMs: Long, b: Balance = Balance()): GameState {
            val party = HeroClass.entries.map { Hero(it, level = 1, hp = b.heroMaxHp(it, 1)) }
            return GameState(
                party = party,
                enemyHp = b.enemyMaxHp(1, 1),
                lastTickMs = nowMs,
            )
        }
    }
}

/** Something worth telling the player about. The widget shows the last one. */
sealed interface GameEvent {
    val tone: Tone
    val grade: Int get() = -1

    data class LevelUp(val level: Int) : GameEvent {
        override val tone get() = Tone.GOOD
    }

    data class HeroJoined(val cls: HeroClass) : GameEvent {
        override val tone get() = Tone.MAGIC
    }

    data class BossDown(val act: Int, val drop: Item) : GameEvent {
        override val tone get() = Tone.LOOT
        override val grade get() = drop.grade
    }

    data class Cubed(override val grade: Int) : GameEvent {
        override val tone get() = Tone.LOOT
    }

    data class ActCleared(val act: Int) : GameEvent {
        override val tone get() = Tone.GOOD
    }

    data class Wiped(val act: Int) : GameEvent {
        override val tone get() = Tone.BAD
    }

    data object Potion : GameEvent {
        override val tone get() = Tone.GOOD
    }
}

/** What colour a message is, decided by the engine and not by the painter. */
enum class Tone { NEUTRAL, GOOD, BAD, LOOT, MAGIC }

/**
 * A message as it is *stored*: the tone and grade travel with the text, because a
 * broadcast later the GameEvent no longer exists and the colour would be lost.
 */
data class Ticker(val text: String, val tone: Tone, val grade: Int = -1)

fun GameEvent.toTicker(): Ticker = Ticker(
    text = when (this) {
        is GameEvent.LevelUp -> "LEVEL $level"
        is GameEvent.HeroJoined -> "${cls.label} JOINS"
        is GameEvent.BossDown -> drop.label
        is GameEvent.Cubed -> "CUBED ${Loot.GRADES[grade]}"
        is GameEvent.ActCleared -> "ACT $act CLEAR"
        is GameEvent.Wiped -> "WIPED"
        GameEvent.Potion -> "HEALED"
    },
    tone = tone,
    grade = grade,
)
