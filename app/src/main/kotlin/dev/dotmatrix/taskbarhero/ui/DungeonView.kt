package dev.dotmatrix.taskbarhero.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import dev.dotmatrix.taskbarhero.data.GameStore
import dev.dotmatrix.taskbarhero.engine.GameState
import dev.dotmatrix.taskbarhero.paint.DotPainter
import dev.dotmatrix.taskbarhero.paint.DungeonLayout
import dev.dotmatrix.taskbarhero.render.AndroidSurface

/**
 * Hosts [DungeonLayout] on a real View, so the dungeon animates at 4 fps instead
 * of going through RemoteViews like the bar does.
 */
class DungeonView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
) : View(ctx, attrs) {

    private var state: GameState = GameState.newRun(0L)
    private var log: List<String> = emptyList()

    fun bind(state: GameState, log: List<String>) {
        this.state = state
        this.log = log
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val unit = width.toFloat() / DungeonLayout.TARGET_COLS
        if (unit < 1f) return
        DungeonLayout.draw(
            p = DotPainter(AndroidSurface(canvas), unit),
            cols = DungeonLayout.TARGET_COLS,
            rows = (height / unit).toInt(),
            state = state,
            log = log,
            nowMs = System.currentTimeMillis(),
            b = GameStore.balance,
        )
    }
}
