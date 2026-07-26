package dev.dotmatrix.taskbarhero.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import dev.dotmatrix.taskbarhero.R
import dev.dotmatrix.taskbarhero.data.GameStore
import dev.dotmatrix.taskbarhero.engine.GameEvent
import dev.dotmatrix.taskbarhero.fx.Fx
import dev.dotmatrix.taskbarhero.widget.TaskbarHeroWidget

/**
 * What the bar opens into: the same run, at full size, ticking live. It owns no
 * game state of its own — [GameStore] is the single writer, so the widget and
 * this screen can never disagree.
 */
class DungeonActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var scene: DungeonView
    private lateinit var levelUpButton: Button
    private lateinit var autoButton: Button
    private lateinit var resetButton: Button

    /** Session-only event feed; the widget keeps just the latest caption. */
    private val log = ArrayDeque<String>()
    private var resetArmedUntil = 0L

    private val tick = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dungeon)
        scene = findViewById(R.id.scene)
        levelUpButton = findViewById(R.id.level_up)
        autoButton = findViewById(R.id.auto)
        resetButton = findViewById(R.id.reset)

        levelUpButton.setOnClickListener {
            val result = GameStore.levelUp(this)
            if (result == null) {
                Fx.onRefused(this)
            } else {
                Fx.onEvent(this, GameEvent.LevelUp(result.state.level))
                push(GameEvent.LevelUp(result.state.level).caption)
            }
            step()
            TaskbarHeroWidget.refreshAll(this)
        }

        autoButton.setOnClickListener {
            val enabled = !GameStore.peek(this).autoLevel
            GameStore.setAutoLevel(this, enabled)
            push(if (enabled) "AUTO LEVELLING ON" else "AUTO LEVELLING OFF")
            step()
            TaskbarHeroWidget.refreshAll(this)
        }

        // Two taps instead of a dialog: the second one within the window commits.
        resetButton.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now < resetArmedUntil) {
                resetArmedUntil = 0L
                log.clear()
                GameStore.reset(this)
                push("NEW RUN")
                step()
                TaskbarHeroWidget.refreshAll(this)
            } else {
                resetArmedUntil = now + RESET_WINDOW_MS
                resetButton.text = getString(R.string.reset_confirm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        // Leave the bar in step with what the player just did.
        TaskbarHeroWidget.refreshAll(this)
    }

    private fun step() {
        val result = GameStore.tick(this)
        result.events.forEach { push(it.caption) }
        Fx.onBackgroundEvents(this, result.events)
        if (result.skippedMs > 0L) push("IDLE CAP REACHED")

        val state = result.state
        scene.bind(state, log.toList())
        levelUpButton.isEnabled = state.canLevelUp(GameStore.balance)
        autoButton.text = getString(if (state.autoLevel) R.string.auto_on else R.string.auto_off)
        if (resetArmedUntil != 0L && System.currentTimeMillis() > resetArmedUntil) {
            resetArmedUntil = 0L
            resetButton.text = getString(R.string.reset)
        }
    }

    private fun push(line: String) {
        log.addLast(line)
        while (log.size > MAX_LOG) log.removeFirst()
    }

    private companion object {
        const val REFRESH_MS = 250L
        const val RESET_WINDOW_MS = 3_000L
        const val MAX_LOG = 12
    }
}
