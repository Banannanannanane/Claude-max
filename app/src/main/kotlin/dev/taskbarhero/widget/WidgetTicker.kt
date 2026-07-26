package dev.taskbarhero.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Keeps the bar looking alive.
 *
 * A widget's own `updatePeriodMillis` floor is 30 minutes, so the refresh is a
 * self-rescheduling inexact alarm instead. Inexact on purpose: the simulation is
 * derived from wall-clock time, so a late alarm costs nothing but a stale frame,
 * and the phone is never woken from doze just to move a health bar.
 */
internal object WidgetTicker {

    private const val PERIOD_MS = 30_000L
    private const val REQ = 100

    fun schedule(ctx: Context) {
        if (!TaskbarHeroWidget.hasInstances(ctx)) {
            cancel(ctx)
            return
        }
        alarms(ctx).set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + PERIOD_MS,
            pendingIntent(ctx),
        )
    }

    fun cancel(ctx: Context) {
        alarms(ctx).cancel(pendingIntent(ctx))
    }

    private fun alarms(ctx: Context): AlarmManager =
        ctx.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            REQ,
            Intent(ctx, TaskbarHeroWidget::class.java).setAction(TaskbarHeroWidget.ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
