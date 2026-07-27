package dev.taskbarhero.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import dev.taskbarhero.R
import dev.taskbarhero.data.GameStore
import dev.taskbarhero.engine.GameEvent
import dev.taskbarhero.engine.GameState
import dev.taskbarhero.engine.Hud
import dev.taskbarhero.engine.Ticker
import dev.taskbarhero.fx.Fx
import dev.taskbarhero.paint.BarLayout
import dev.taskbarhero.render.WidgetRenderer
import dev.taskbarhero.ui.DungeonActivity

/**
 * The taskbar itself. Redraws are cheap and stateless: the engine derives the whole
 * fight from elapsed time, so a refresh once a minute is enough for the bar to
 * always show the truth — including after hours of a dark screen.
 *
 * Two shapes, two layouts. One row is a strip with a single LV UP button; two rows
 * and up get the room plus a deck of three real buttons. The launcher picks per
 * size through the RemoteViews size map, so each shape fits its cell exactly rather
 * than being stretched into blur.
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
                    Fx.onEvent(ctx, GameEvent.LevelUp(result.state.partyLevel))
                } else {
                    Fx.onRefused(ctx)
                }
                refreshAll(ctx)
            }

            ACTION_POTION -> {
                val event = GameStore.drinkPotion(ctx)?.events?.lastOrNull()
                if (event != null) Fx.onEvent(ctx, event) else Fx.onRefused(ctx)
                refreshAll(ctx)
            }

            ACTION_CUBE -> {
                val event = GameStore.cube(ctx)?.events?.lastOrNull()
                if (event != null) Fx.onEvent(ctx, event) else Fx.onRefused(ctx)
                refreshAll(ctx)
            }

            ACTION_AUTO -> {
                GameStore.setAutoLevel(ctx, !GameStore.peek(ctx).autoLevel)
                Fx.onToggle(ctx)
                refreshAll(ctx)
            }

            else -> super.onReceive(ctx, intent)
        }
    }

    private fun render(ctx: Context, manager: AppWidgetManager, id: Int) {
        val now = System.currentTimeMillis()
        val state = GameStore.peek(ctx, now)
        val recent = GameStore.recentEvent(ctx, now, BarLayout.EVENT_FLASH_MS)
        val sizes = WidgetSize.of(ctx, manager, id)

        val views = if (sizes.size == 1) {
            build(ctx, state, recent, sizes.first(), now)
        } else {
            // One bitmap per size the launcher told us about, so none is scaled.
            RemoteViews(sizes.associate { SizeF(it.widthDp, it.heightDp) to build(ctx, state, recent, it, now) })
        }
        manager.updateAppWidget(id, views)
    }

    private fun build(ctx: Context, state: GameState, recent: Ticker?, size: WidgetSize, now: Long): RemoteViews {
        val density = ctx.resources.displayMetrics.density
        val heightPx = size.heightPx(density)
        val tall = BarLayout.rowsFor(heightPx, density) >= BarLayout.ROWS_TALL
        val layout = if (tall) R.layout.widget_bar_tall else R.layout.widget_bar_compact

        val bitmap = WidgetRenderer.render(
            ctx = ctx,
            state = state,
            widthPx = size.widthPx(density).toInt(),
            heightPx = heightPx.toInt(),
            density = density,
            nowMs = now,
            recent = recent,
            b = GameStore.balance,
        )

        return RemoteViews(ctx.packageName, layout).apply {
            setImageViewBitmap(R.id.bar, bitmap)
            setContentDescription(R.id.bar, describe(ctx, state))
            setOnClickPendingIntent(R.id.zone_open, activityIntent(ctx))
            setOnClickPendingIntent(R.id.zone_level, broadcast(ctx, ACTION_LEVEL_UP, REQ_LEVEL_UP))
            if (tall) {
                setOnClickPendingIntent(R.id.zone_potion, broadcast(ctx, ACTION_POTION, REQ_POTION))
                setOnClickPendingIntent(R.id.zone_cube, broadcast(ctx, ACTION_CUBE, REQ_CUBE))
                setOnClickPendingIntent(R.id.zone_auto, broadcast(ctx, ACTION_AUTO, REQ_AUTO))
            }
        }
    }

    private fun describe(ctx: Context, state: GameState): String {
        val hud = Hud.of(state, GameStore.balance)
        return ctx.getString(R.string.widget_content_description, hud.stage, hud.level, hud.gold, hud.ticker)
    }

    private fun activityIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            REQ_OPEN,
            Intent(ctx, DungeonActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun broadcast(ctx: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            requestCode,
            Intent(ctx, TaskbarHeroWidget::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ACTION_TICK = "dev.taskbarhero.TICK"
        const val ACTION_LEVEL_UP = "dev.taskbarhero.LEVEL_UP"
        const val ACTION_POTION = "dev.taskbarhero.POTION"
        const val ACTION_CUBE = "dev.taskbarhero.CUBE"
        const val ACTION_AUTO = "dev.taskbarhero.AUTO"

        private const val REQ_OPEN = 1
        private const val REQ_LEVEL_UP = 2
        private const val REQ_POTION = 3
        private const val REQ_CUBE = 4
        private const val REQ_AUTO = 5

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

/** One shape the launcher may show this widget at, in dp. */
internal data class WidgetSize(val widthDp: Float, val heightDp: Float) {

    fun widthPx(density: Float): Float = widthDp * density

    fun heightPx(density: Float): Float = heightDp * density

    companion object {
        private const val FALLBACK_WIDTH_DP = 250f
        private const val FALLBACK_HEIGHT_DP = 148f

        /**
         * Every size the launcher intends to use. Android 12 reports them exactly
         * (OPTION_APPWIDGET_SIZES); launchers that skip it leave us the min/max
         * pair to work from.
         */
        fun of(ctx: Context, manager: AppWidgetManager, id: Int): List<WidgetSize> {
            val options = manager.getAppWidgetOptions(id)
            val declared = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            if (!declared.isNullOrEmpty()) {
                return declared.map { WidgetSize(it.width, it.height) }
            }
            // Portrait pairing: the launcher reports min width with max height.
            val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            return listOf(
                WidgetSize(
                    if (w > 0) w.toFloat() else FALLBACK_WIDTH_DP,
                    if (h > 0) h.toFloat() else FALLBACK_HEIGHT_DP,
                ),
            )
        }
    }
}
