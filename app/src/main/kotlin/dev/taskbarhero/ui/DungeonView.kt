package dev.taskbarhero.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import dev.taskbarhero.data.GameStore
import dev.taskbarhero.engine.GameState
import dev.taskbarhero.engine.Ticker
import dev.taskbarhero.paint.PixelPainter
import dev.taskbarhero.paint.DungeonLayout
import dev.taskbarhero.render.AndroidSurface

/**
 * Hosts [DungeonLayout] on a real View, so the dungeon animates at 4 fps instead
 * of going through RemoteViews like the bar does.
 */
class DungeonView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
) : View(ctx, attrs) {

    private var state: GameState = GameState.newRun(0L)
    private var log: List<Ticker> = emptyList()

    fun bind(state: GameState, log: List<Ticker>) {
        this.state = state
        this.log = log
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val unit = width.toFloat() / DungeonLayout.TARGET_COLS
        if (unit < 1f) return
        DungeonLayout.draw(
            p = PixelPainter(AndroidSurface(canvas), unit),
            cols = DungeonLayout.TARGET_COLS,
            rows = (height / unit).toInt(),
            state = state,
            log = log,
            nowMs = System.currentTimeMillis(),
            b = GameStore.balance,
        )
    }
}
