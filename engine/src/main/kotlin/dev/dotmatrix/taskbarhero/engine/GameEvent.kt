package dev.dotmatrix.taskbarhero.engine

/**
 * Notable things that happened during a simulated span. The widget turns these
 * into a one-line ticker, the activity into a log, and [GameEvent.BossDown]
 * into a Glyph/haptic flourish.
 */
sealed interface GameEvent {
    /** Short dot-matrix caption, uppercase and ASCII so [DotFont] can draw it. */
    val caption: String

    data class LevelUp(val level: Int) : GameEvent {
        override val caption get() = "LEVEL $level"
    }

    data class RuneGained(val runes: Int) : GameEvent {
        override val caption get() = "RUNE $runes +${runes * 2}%"
    }

    data class BossDown(val act: Int, val loot: String) : GameEvent {
        override val caption get() = "BOSS $act DOWN / $loot"
    }

    data class ActCleared(val act: Int) : GameEvent {
        override val caption get() = "ACT $act CLEARED"
    }

    data class HeroDown(val act: Int, val wave: Int) : GameEvent {
        override val caption get() = "WIPED AT $act-${wave.pad2()}"
    }
}

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
