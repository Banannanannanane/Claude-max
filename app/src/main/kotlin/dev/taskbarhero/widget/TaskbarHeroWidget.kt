package dev.taskbarhero.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import dev.taskbarhero.MainActivity
import dev.taskbarhero.R
import dev.taskbarhero.data.GameStore
import dev.taskbarhero.paint.BarLayout
import dev.taskbarhero.paint.Painter
import dev.taskbarhero.render.AndroidSurface
import dev.taskbarhero.render.GameArt

/**
 * The game on the home screen.
 *
 * A widget cannot draw: it can only be handed a bitmap and a set of tap targets.
 * So the room is rendered to a bitmap the exact size the launcher asked for — one
 * per size the launcher offers, because a bitmap made for one shape and stretched
 * to another is a blurred one — and the buttons painted into it line up with the
 * touch zones declared in the layout.
 */
class TaskbarHeroWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(ctx, manager, id)
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
            ACTION_LEVEL_UP -> GameStore.levelUp(ctx)
            ACTION_POTION -> GameStore.drinkPotion(ctx)
            ACTION_CUBE -> GameStore.cube(ctx)
            ACTION_AUTO -> GameStore.toggleAuto(ctx)
            else -> {
                super.onReceive(ctx, intent)
                return
            }
        }
        refreshAll(ctx)
    }

    companion object {
        const val ACTION_LEVEL_UP = "dev.taskbarhero.LEVEL_UP"
        const val ACTION_POTION = "dev.taskbarhero.POTION"
        const val ACTION_CUBE = "dev.taskbarhero.CUBE"
        const val ACTION_AUTO = "dev.taskbarhero.AUTO"

        fun refreshAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, TaskbarHeroWidget::class.java))
            for (id in ids) render(ctx, manager, id)
        }

        private fun render(ctx: Context, manager: AppWidgetManager, id: Int) {
            val art = GameArt.load(ctx)
            if (art !is GameArt.Load.Ready) {
                manager.updateAppWidget(id, brokenViews(ctx))
                return
            }
            val options = manager.getAppWidgetOptions(id)

            // Android 12 hands over every size the launcher may show this widget
            // at; before that there is only a min/max box to guess from.
            val sizes: List<SizeF> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(fallbackSize(options))
            } else {
                listOf(fallbackSize(options))
            }

            val views = sizes.associateWith { size -> viewsFor(ctx, art, size) }
            manager.updateAppWidget(
                id,
                if (views.size == 1) views.values.first() else RemoteViews(views),
            )
        }

        private fun fallbackSize(options: Bundle): SizeF = SizeF(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).toFloat(),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110).toFloat(),
        )

        private fun viewsFor(ctx: Context, art: GameArt.Load.Ready, sizeDp: SizeF): RemoteViews {
            val density = ctx.resources.displayMetrics.density
            val w = (sizeDp.width * density).toInt().coerceIn(120, 2400)
            val h = (sizeDp.height * density).toInt().coerceIn(60, 2400)
            val tall = BarLayout.hasDeck(h.toFloat(), density)

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            BarLayout.draw(
                Painter(AndroidSurface(Canvas(bitmap), art), art.dungeon, art.ui),
                w.toFloat(), h.toFloat(), density,
                GameStore.peek(ctx),
                System.currentTimeMillis(),
                GameStore.recentTicker(ctx, BarLayout.EVENT_MS),
                GameStore.balance,
            )

            val views = RemoteViews(ctx.packageName, if (tall) R.layout.widget_tall else R.layout.widget_bar)
            views.setImageViewBitmap(R.id.canvas, bitmap)
            views.setOnClickPendingIntent(R.id.open, openApp(ctx))
            if (tall) {
                views.setOnClickPendingIntent(R.id.slot_1, broadcast(ctx, ACTION_LEVEL_UP, 1))
                views.setOnClickPendingIntent(R.id.slot_2, broadcast(ctx, ACTION_POTION, 2))
                views.setOnClickPendingIntent(R.id.slot_3, broadcast(ctx, ACTION_CUBE, 3))
                views.setOnClickPendingIntent(R.id.slot_4, broadcast(ctx, ACTION_AUTO, 4))
            } else {
                views.setOnClickPendingIntent(R.id.slot_1, broadcast(ctx, ACTION_LEVEL_UP, 1))
            }
            return views
        }

        /** What the widget shows when the pack is missing: the same refusal, in a bar. */
        private fun brokenViews(ctx: Context): RemoteViews =
            RemoteViews(ctx.packageName, R.layout.widget_broken).apply {
                setOnClickPendingIntent(R.id.open, openApp(ctx))
            }

        private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun broadcast(ctx: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, code,
                Intent(ctx, TaskbarHeroWidget::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
