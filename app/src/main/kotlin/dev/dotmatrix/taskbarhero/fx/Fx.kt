package dev.dotmatrix.taskbarhero.fx

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.dotmatrix.taskbarhero.engine.GameEvent

/**
 * Haptics for the things worth feeling in a pocket: a boss going down, a level
 * bought, a wipe. Deliberately terse — this fires from a widget broadcast, which
 * users cannot mute individually.
 *
 * Glyph note: Nothing's light strips are driven by the Glyph Developer Kit
 * (`com.nothing.ketchum`), which is not on Maven Central and needs a signed
 * app-id from Nothing. [onEvent] is the single hook where those calls belong —
 * see README for the exact wiring. Everything here degrades to vibration on
 * non-Nothing hardware, which is what the emulator and CI see.
 */
object Fx {

    /**
     * Haptics for events nobody asked for. AUTO levelling fires constantly, so
     * only milestones are allowed to buzz a pocket — a widget's vibration cannot
     * be muted on its own.
     */
    fun onBackgroundEvents(ctx: Context, events: List<GameEvent>) {
        val notable = events.lastOrNull {
            it is GameEvent.BossDown || it is GameEvent.ActCleared || it is GameEvent.HeroDown
        } ?: return
        onEvent(ctx, notable)
    }

    /** For events the player just caused by tapping. */
    fun onEvent(ctx: Context, event: GameEvent) {
        when (event) {
            is GameEvent.BossDown -> pattern(ctx, longArrayOf(0, 40, 60, 40, 60, 120))
            is GameEvent.ActCleared -> pattern(ctx, longArrayOf(0, 30, 50, 90))
            is GameEvent.LevelUp -> click(ctx, VibrationEffect.EFFECT_CLICK)
            is GameEvent.HeroDown -> pattern(ctx, longArrayOf(0, 140))
            is GameEvent.RuneGained -> Unit // Too frequent to be worth a buzz.
        }
    }

    /** Tapping LV UP without the gold: a dull tick, so the bar still feels responsive. */
    fun onRefused(ctx: Context) = click(ctx, VibrationEffect.EFFECT_TICK)

    private fun click(ctx: Context, effectId: Int) {
        vibrator(ctx)?.takeIf { it.hasVibrator() }?.vibrate(VibrationEffect.createPredefined(effectId))
    }

    private fun pattern(ctx: Context, timings: LongArray) {
        val v = vibrator(ctx)?.takeIf { it.hasVibrator() } ?: return
        v.vibrate(VibrationEffect.createWaveform(timings, -1))
    }

    private fun vibrator(ctx: Context): Vibrator? =
        ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
}
