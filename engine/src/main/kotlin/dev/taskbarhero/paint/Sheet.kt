package dev.taskbarhero.paint

/**
 * A sprite sheet dropped in by the player: one image, plus a mapping that says
 * which rectangle of it is which fighter.
 *
 * The engine never loads the image — that is the platform's job — it only asks
 * where a frame lives. Everything the game draws still works with no sheet at
 * all, falling back to the built-in art, so a missing or half-filled mapping
 * degrades one sprite at a time instead of breaking the widget.
 */
interface Sheet {
    /** Source rectangle for [key], or null to fall back to the built-in art. */
    fun frame(key: String): Frame?

    /**
     * The sheet's own pixel grid: the size of its largest frame.
     *
     * Everything is scaled by `box / unit`, one factor for the whole cast, so a
     * pack keeps the proportions its artist drew — the ogre towers, the skull of a
     * fallen hero is a trinket on the floor. Scaling each frame to fill its own box
     * instead would blow that skull up to the size of a knight.
     */
    val unit: Int

    data class Frame(val x: Int, val y: Int, val width: Int, val height: Int) {
        init {
            require(width > 0 && height > 0) { "a frame needs a size: $this" }
        }
    }
}

/** A sheet backed by a parsed mapping. */
class MappedSheet(private val frames: Map<String, Sheet.Frame>) : Sheet {
    override fun frame(key: String): Sheet.Frame? = frames[key]

    override val unit: Int =
        frames.values.maxOfOrNull { maxOf(it.width, it.height) } ?: 1

    val size: Int get() = frames.size

    val keys: Set<String> get() = frames.keys
}

/**
 * Reads the mapping that ships beside the image. Deliberately not JSON: no
 * dependency, and a line a human can edit while looking at the sheet in an image
 * editor.
 *
 * ```
 * # name = x, y, width, height
 * KNIGHT = 16, 0, 16, 16
 * BOSS   = 0, 64, 32, 36
 * ```
 */
object SheetMapping {

    /**
     * Parses [text], skipping blank lines and `#` comments. Malformed lines are
     * reported rather than thrown: a typo in one row should cost that sprite, not
     * the whole sheet.
     */
    fun parse(text: String): Result {
        val frames = mutableMapOf<String, Sheet.Frame>()
        val problems = mutableListOf<String>()

        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed

            val name = line.substringBefore('=', "").trim().uppercase()
            val numbers = line.substringAfter('=', "").split(',').map { it.trim() }
            if (name.isEmpty() || numbers.size != 4) {
                problems += "line ${index + 1}: expected NAME = x, y, width, height"
                return@forEachIndexed
            }
            val parsed = numbers.map { it.toIntOrNull() }
            if (parsed.any { it == null } || parsed[2]!! <= 0 || parsed[3]!! <= 0) {
                problems += "line ${index + 1}: '$line' is not four positive numbers"
                return@forEachIndexed
            }
            frames[name] = Sheet.Frame(parsed[0]!!, parsed[1]!!, parsed[2]!!, parsed[3]!!)
        }
        return Result(MappedSheet(frames), problems)
    }

    data class Result(val sheet: MappedSheet, val problems: List<String>) {
        val isUsable: Boolean get() = sheet.size > 0
    }
}
