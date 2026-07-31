package dev.taskbarhero

import android.app.Activity
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.taskbarhero.assets.AssetManifest
import dev.taskbarhero.data.GameStore
import dev.taskbarhero.game.GameState
import dev.taskbarhero.paint.BarLayout
import dev.taskbarhero.paint.Painter
import dev.taskbarhero.render.AndroidSurface
import dev.taskbarhero.render.GameArt
import dev.taskbarhero.widget.TaskbarHeroWidget

/**
 * What the bar opens into: the same run, at full size, ticking live.
 *
 * It owns no state of its own — [GameStore] is the single writer — so the widget
 * and this screen can never disagree about where the run is.
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var scene: SceneView? = null

    private val tick = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (val art = GameArt.load(this)) {
            is GameArt.Load.Broken -> setContentView(refusal(art.reasons))
            is GameArt.Load.Ready -> {
                val view = SceneView(this, art)
                scene = view
                setContentView(view)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (scene != null) handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        // Leave the bar showing whatever the player just did.
        TaskbarHeroWidget.refreshAll(this)
    }

    private fun step() {
        GameStore.tick(this)
        scene?.invalidate()
    }

    /**
     * The full-screen room.
     *
     * It is the widget's own layout, drawn into a whole screen — the same code,
     * the same proportions, four times the size. Taps land on the same painted
     * buttons because the layout is what decides where they are.
     */
    private inner class SceneView(
        ctx: android.content.Context,
        private val art: GameArt.Load.Ready,
    ) : View(ctx) {

        override fun onDraw(canvas: Canvas) {
            val density = resources.displayMetrics.density
            BarLayout.draw(
                Painter(AndroidSurface(canvas, art), art.dungeon, art.ui),
                width.toFloat(), height.toFloat(), density,
                GameStore.peek(context),
                System.currentTimeMillis(),
                GameStore.recentTicker(context, BarLayout.EVENT_MS),
                GameStore.balance,
            )
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_UP) return true
            val density = resources.displayMetrics.density
            val deck = BarLayout.deckHeight(density)
            if (event.y < height - deck) return true

            // The deck is split exactly as it is painted, so a tap and the button
            // under the finger are the same thing.
            var x = 0f
            for ((index, share) in BarLayout.DECK_SPLIT.withIndex()) {
                val w = width * share
                if (event.x >= x && event.x < x + w) {
                    press(index)
                    return true
                }
                x += w
            }
            return true
        }

        private fun press(slot: Int) {
            when (slot) {
                0 -> GameStore.levelUp(context)
                1 -> GameStore.drinkPotion(context)
                2 -> GameStore.cube(context)
                else -> GameStore.toggleAuto(context)
            }
            invalidate()
            TaskbarHeroWidget.refreshAll(context)
        }
    }

    /**
     * The screen shown when the pack is incomplete: what is missing, and why the
     * game wants it. Built out of plain text views, because a refusal that needed
     * a drawable could not be shown by an app whose drawables are missing.
     */
    private fun refusal(reasons: List<String>): ViewGroup {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF12121A.toInt())
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }
        column.addView(
            TextView(this).apply {
                text = "NO ART"
                setTextColor(0xFFC4453A.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            },
        )
        column.addView(
            TextView(this).apply {
                text = (reasons + listOf("", "Every file goes in ${AssetManifest.DUNGEON.substringBeforeLast('/')}/."))
                    .joinToString("\n")
                setTextColor(0xFFEDE6D2.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(16), 0, 0)
            },
        )
        return ScrollView(this).apply { addView(column) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        /** Four frames a second: the same beat the simulation ticks at. */
        const val REFRESH_MS = 250L

        @Suppress("unused")
        val unusedTypes = listOf(GameState::class, FrameLayout::class)
    }
}
