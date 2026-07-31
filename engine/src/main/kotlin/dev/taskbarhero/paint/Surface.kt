package dev.taskbarhero.paint

/**
 * Everything the game can put on a screen, in four calls.
 *
 * Small on purpose: the layouts live in this module and run untouched on a
 * phone's Canvas, on a desktop's Graphics2D for design review, and on a recorder
 * in the tests. Anything richer than this would be a second implementation to
 * keep honest.
 *
 * Coordinates are device pixels, and callers are expected to hand over whole
 * ones — a sprite blitted onto a half pixel is a blurred sprite.
 */
interface Surface {

    /** Which sheet a blit reads from. Two atlases, named rather than numbered. */
    enum class Sheet { DUNGEON, UI }

    fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int)

    /** Blits a source rectangle, stretched to the destination, never smoothed. */
    fun image(
        sheet: Sheet,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    )

    /** Draws [text] with its top-left corner at ([x], [y]). */
    fun text(x: Float, y: Float, text: String, sizePx: Float, color: Int)

    /** Width [text] will occupy at [sizePx]. */
    fun measure(text: String, sizePx: Float): Float

    /** Distance from the top of a line to the top of the next. */
    fun lineHeight(sizePx: Float): Float
}

/**
 * The colours the game chooses for itself.
 *
 * Deliberately few, and each with a meaning rather than a name: red is health,
 * blue is progress, gold is money, and the ten grade colours belong to loot and
 * to nothing else, so a colour on screen is never decorative.
 */
object Palette {
    const val VOID = 0xFF12121A.toInt()
    const val INK = 0xFF1D1B26.toInt()
    const val PARCHMENT = 0xFFEDE6D2.toInt()
    const val PARCHMENT_DIM = 0xFF9A9382.toInt()
    const val GOLD = 0xFFF2B233.toInt()
    const val HP = 0xFFC4453A.toInt()

    /** Ours. The one colour a health line uses when the fighter is on our side. */
    const val ALLY = 0xFF5FA84E.toInt()

    /** What a health line shows where the health is gone. */
    const val EMPTY = 0xFF2A2733.toInt()
    const val MAGIC = 0xFF4E8FD8.toInt()
    const val SHADOW = 0x55000000

    /** One per loot grade, Common to Cosmic. The only place these are allowed. */
    val GRADES = intArrayOf(
        0xFFB9BCC4.toInt(), 0xFF5FA84E.toInt(), 0xFF4E8FD8.toInt(), 0xFFB061D8.toInt(),
        0xFFE0603A.toInt(), 0xFFE0C23A.toInt(), 0xFF3AD0C0.toInt(), 0xFFF06CB0.toInt(),
        0xFFFFF0A0.toInt(), 0xFFFF4A6E.toInt(),
    )

    fun grade(index: Int): Int = GRADES[index.coerceIn(0, GRADES.lastIndex)]
}
