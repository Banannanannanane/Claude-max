package dev.taskbarhero.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * What makes the bar look alive.
 *
 * A widget's own refresh has a floor of half an hour, which would show a fight
 * frozen mid-swing. So it reschedules itself every thirty seconds — as an
 * *inexact* alarm, which Android is free to slide around and to batch with other
 * work, and which therefore never wakes the phone on this game's account. The
 * simulation is a function of the clock, so a late alarm costs nothing: the room
 * simply shows more progress when it does arrive.
 */
object WidgetTicker {

    private const val PERIOD_MS = 30_000L
    private const val ACTION = "dev.taskbarhero.TICK"

    fun schedule(ctx: Context) {
        val alarms = ctx.getSystemService(AlarmManager::class.java) ?: return
        alarms.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + PERIOD_MS,
            pending(ctx),
        )
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx))
    }

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, TickReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Redraws every widget, then books the next tick. */
class TickReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        TaskbarHeroWidget.refreshAll(ctx)
        WidgetTicker.schedule(ctx)
    }
}
