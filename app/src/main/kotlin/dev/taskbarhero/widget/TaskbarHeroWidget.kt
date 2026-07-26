package dev.taskbarhero.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import dev.taskbarhero.R
import dev.taskbarhero.data.GameStore
import dev.taskbarhero.engine.GameEvent
import dev.taskbarhero.engine.GameState
import dev.taskbarhero.engine.Hud
import dev.taskbarhero.fx.Fx
import dev.taskbarhero.paint.BarLayout
import dev.taskbarhero.render.WidgetRenderer
import dev.taskbarhero.ui.DungeonActivity

/**
 * The taskbar itself. Redraws are cheap and stateless: the engine derives the
 * whole fight from elapsed time, so a refresh once a minute is enough for the bar
 * to always show the truth — including after hours of a dark screen.
 */
class TaskbarHeroWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        GameStore.tick(ctx)
        ids.forEach { render(ctx, manager, it) }
        WidgetTicker.schedule(ctx)
    }

    override fun onAppWidgetOptionsChanged(
        ctx: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle,
    ) {
        render(ctx, manager, id)
    }

    override fun onEnabled(ctx: Context) = WidgetTicker.schedule(ctx)

    override fun onDisabled(ctx: Context) = WidgetTicker.cancel(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TICK -> {
                Fx.onBackgroundEvents(ctx, GameStore.tick(ctx).events)
                refreshAll(ctx)
                WidgetTicker.schedule(ctx)
            }

            ACTION_LEVEL_UP -> {
                val result = GameStore.levelUp(ctx)
                if (result != null) {
                    Fx.onEvent(ctx, GameEvent.LevelUp(result.state.level))
                } else {
                    Fx.onRefused(ctx)
                }
                refreshAll(ctx)
            }

            else -> super.onReceive(ctx, intent)
        }
    }

    private fun render(ctx: Context, manager: AppWidgetManager, id: Int) {
        val size = WidgetSize.of(ctx, manager, id)
        val now = System.currentTimeMillis()
        val state = GameStore.peek(ctx, now)
        val bitmap = WidgetRenderer.render(
            state = state,
            widthPx = size.widthPx,
            heightPx = size.heightPx,
            density = ctx.resources.displayMetrics.density,
            nowMs = now,
            recent = GameStore.recentEvent(ctx, now, BarLayout.EVENT_FLASH_MS),
            b = GameStore.balance,
        )

        val views = RemoteViews(ctx.packageName, R.layout.widget_taskbar_hero).apply {
            setImageViewBitmap(R.id.bar, bitmap)
            setContentDescription(R.id.bar, describe(ctx, state))
            setOnClickPendingIntent(R.id.zone_open, openIntent(ctx))
            setOnClickPendingIntent(R.id.zone_action, levelUpIntent(ctx))
        }
        manager.updateAppWidget(id, views)
    }

    private fun describe(ctx: Context, state: GameState): String {
        val hud = Hud.of(state, GameStore.balance)
        return ctx.getString(R.string.widget_content_description, hud.stage, hud.level, hud.gold, hud.ticker)
    }

    private fun openIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            REQ_OPEN,
            Intent(ctx, DungeonActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun levelUpIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            REQ_LEVEL_UP,
            Intent(ctx, TaskbarHeroWidget::class.java).setAction(ACTION_LEVEL_UP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ACTION_TICK = "dev.taskbarhero.TICK"
        const val ACTION_LEVEL_UP = "dev.taskbarhero.LEVEL_UP"

        private const val REQ_OPEN = 1
        private const val REQ_LEVEL_UP = 2

        /** Redraws every placed instance — used after a tap or from the activity. */
        fun refreshAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, TaskbarHeroWidget::class.java))
            if (ids.isEmpty()) return
            ctx.sendBroadcast(
                Intent(ctx, TaskbarHeroWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }

        fun hasInstances(ctx: Context): Boolean =
            AppWidgetManager.getInstance(ctx)
                .getAppWidgetIds(ComponentName(ctx, TaskbarHeroWidget::class.java))
                .isNotEmpty()
    }
}

/** Pixel size of one placed widget, from the launcher's reported cell size. */
internal data class WidgetSize(val widthPx: Int, val heightPx: Int) {
    companion object {
        fun of(ctx: Context, manager: AppWidgetManager, id: Int): WidgetSize {
            val options = manager.getAppWidgetOptions(id)
            val density = ctx.resources.displayMetrics.density
            // Portrait pairing: the launcher reports min width with max height.
            val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            val w = if (wDp > 0) wDp else FALLBACK_WIDTH_DP
            val h = if (hDp > 0) hDp else FALLBACK_HEIGHT_DP
            return WidgetSize((w * density).toInt(), (h * density).toInt())
        }

        private const val FALLBACK_WIDTH_DP = 250
        private const val FALLBACK_HEIGHT_DP = 70
    }
}
