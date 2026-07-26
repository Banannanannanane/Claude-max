package dev.taskbarhero.engine

import java.util.Locale
import kotlin.math.floor

/** Number and duration formatting, kept ASCII-uppercase so [PixelFont] can draw all of it. */
object Fmt {

    private val SUFFIXES = listOf("", "K", "M", "B", "T", "AA", "AB", "AC", "AD", "AE", "AF")

    /** 942 -> "942", 12_400 -> "12.4K", 3_000_000 -> "3M". */
    fun short(value: Double): String {
        if (value.isNaN() || value <= 0.0) return "0"
        if (value < 1000.0) return floor(value).toInt().toString()
        var v = value
        var i = 0
        while (v >= 1000.0 && i < SUFFIXES.lastIndex) {
            v /= 1000.0
            i++
        }
        val suffix = SUFFIXES[i]
        // One decimal below 100 keeps "12.4K" readable; above it the bar has no room.
        return if (v < 100.0) {
            val truncated = floor(v * 10.0) / 10.0
            String.format(Locale.ROOT, "%.1f%s", truncated, suffix)
        } else {
            "${floor(v).toInt()}$suffix"
        }
    }

    /** "ACT 3-07" style location label. */
    fun stage(act: Int, wave: Int): String = "$act-${wave.pad2()}"

    /** 8_400_000 -> "2H 20M", 95_000 -> "1M 35S". */
    fun duration(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return when {
            h > 0 -> "${h}H ${m.pad2()}M"
            m > 0 -> "${m}M ${s.pad2()}S"
            else -> "${s}S"
        }
    }

    fun percent(fraction: Double): String =
        "${floor(fraction.coerceIn(0.0, 1.0) * 100.0).toInt()}%"

    private fun Long.pad2(): String = if (this < 10L) "0$this" else toString()
}
