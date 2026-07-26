package dev.dotmatrix.taskbarhero.paint

/** Nothing OS-ish monochrome palette: one accent, everything else is ink on black. */
object Palette {
    const val BG = 0xFF000000.toInt()
    const val INK = 0xFFF5F5F5.toInt()
    const val MID = 0xFF7A7A7A.toInt()
    const val DIM = 0xFF2A2A2A.toInt()
    const val GRID = 0xFF121212.toInt()
    const val RED = 0xFFD71921.toInt()
}

/**
 * The smallest drawing sink the widget needs: ARGB colours, pixel coordinates.
 *
 * Keeping it here — rather than in the Android module — is what lets the real
 * layout code run three places: on a phone (Canvas), in the PNG previewer
 * (Graphics2D) and in unit tests (a recorder that checks nothing escapes the
 * bar).
 */
interface Surface {
    fun circle(cx: Float, cy: Float, radius: Float, color: Int)

    fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int)

    fun roundRectFill(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int)

    fun roundRectStroke(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        strokeWidth: Float,
        color: Int,
    )
}
