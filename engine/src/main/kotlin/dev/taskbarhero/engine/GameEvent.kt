package dev.taskbarhero.engine

/**
 * Notable things that happened during a simulated span. The bar turns these into
 * a one-line ticker, the dungeon into a log, and [GameEvent.BossDown] into a
 * haptic flourish.
 */
sealed interface GameEvent {
    /** Short caption, uppercase and ASCII so [PixelFont] can draw all of it. */
    val caption: String

    /** How the caption should read: the paint layer maps tone to colour. */
    val tone: Tone get() = Tone.NEUTRAL

    /** Loot grade behind the caption, for the tone that carries one. */
    val grade: Int get() = 0

    fun toTicker(): Ticker = Ticker(caption, tone, grade)

    data class LevelUp(val level: Int) : GameEvent {
        override val caption get() = "LEVEL $level"
        override val tone get() = Tone.GOOD
    }

    data class RuneGained(val runes: Int) : GameEvent {
        override val caption get() = "RUNE $runes +${runes * 2}%"
        override val tone get() = Tone.MAGIC
    }

    data class BossDown(val act: Int, val drop: Loot.Drop) : GameEvent {
        override val caption get() = "BOSS $act / ${drop.label}"
        override val tone get() = Tone.LOOT
        override val grade get() = drop.grade
    }

    data class Potion(val revived: Boolean) : GameEvent {
        override val caption get() = if (revived) "REVIVED" else "HEALED"
        override val tone get() = Tone.MAGIC
    }

    data class ActCleared(val act: Int) : GameEvent {
        override val caption get() = "ACT $act CLEARED"
        override val tone get() = Tone.GOOD
    }

    data class HeroDown(val act: Int, val wave: Int) : GameEvent {
        override val caption get() = "WIPED AT $act-${wave.pad2()}"
        override val tone get() = Tone.BAD
    }
}

enum class Tone { NEUTRAL, GOOD, BAD, MAGIC, LOOT }

/**
 * A caption on its way to the screen. Kept separate from [GameEvent] because the
 * widget persists only the latest line: a broadcast later there is no event
 * object left, just this.
 */
data class Ticker(val text: String, val tone: Tone = Tone.NEUTRAL, val grade: Int = 0)

data class TickResult(
    val state: GameState,
    /** Capped, oldest first. Long offline spans keep only the last [IdleEngine.MAX_EVENTS]. */
    val events: List<GameEvent>,
    val goldGained: Double = 0.0,
    val xpGained: Double = 0.0,
    val killsGained: Long = 0L,
    val simulatedMs: Long = 0L,
    /** Time thrown away by the offline cap — shown as "IDLE CAP" in the UI. */
    val skippedMs: Long = 0L,
) {
    val didAnything: Boolean get() = simulatedMs > 0L
}

internal fun Int.pad2(): String = if (this < 10) "0$this" else toString()
