package dev.taskbarhero

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.taskbarhero.assets.AssetManifest

/**
 * For now: the asset gate, and nothing else.
 *
 * The game draws no art of its own, so it will not start on a half-filled pack —
 * it says which files it is waiting for and stops. That screen is deliberately
 * built out of plain text views: an error screen that needed a drawable could not
 * be shown by an app whose drawables are missing.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report = AssetManifest.check(present = assetPaths())
        setContentView(if (report.isPlayable) playableView(report) else missingView(report))
    }

    /** Every file actually packed under `assets/`, as manifest-style paths. */
    private fun assetPaths(): Set<String> {
        val found = mutableSetOf<String>()
        fun walk(dir: String) {
            val entries = runCatching { assets.list(dir) }.getOrNull().orEmpty()
            if (entries.isEmpty()) {
                if (dir.isNotEmpty()) found += dir
                return
            }
            for (entry in entries) walk(if (dir.isEmpty()) entry else "$dir/$entry")
        }
        walk("")
        return found
    }

    private fun missingView(report: AssetManifest.Report) = screen(
        title = "NO ART",
        titleColor = 0xFFE05A4A.toInt(),
        body = report.lines(),
    )

    private fun playableView(report: AssetManifest.Report) = screen(
        title = "READY",
        titleColor = 0xFF6FAE4B.toInt(),
        body = report.lines() + listOf("", "The game itself is not built yet."),
    )

    private fun screen(title: String, titleColor: Int, body: List<String>): ViewGroup {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF14101A.toInt())
            val pad = dp(24)
            setPadding(pad, dp(48), pad, pad)
        }
        column.addView(
            TextView(this).apply {
                text = title
                setTextColor(titleColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                gravity = Gravity.START
            },
        )
        column.addView(
            TextView(this).apply {
                text = body.joinToString("\n")
                setTextColor(0xFFEDE6D2.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(0, dp(16), 0, 0)
            },
        )
        return ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(column)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
