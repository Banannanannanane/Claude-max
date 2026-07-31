package dev.taskbarhero.assets

/**
 * Which rectangle of an image is which sprite.
 *
 * The engine never loads the image — that is the platform's job — it only says
 * where to cut. Keeping it that way is what lets the same layout code run on a
 * phone's Canvas and on a desktop's Graphics2D, and be tested with neither.
 */
class Atlas(private val frames: Map<String, Frame>) {

    /**
     * A source rectangle, plus how many screen pixels one source pixel is worth
     * *relative to the rest of the sheet*.
     *
     * [scale] exists because a pack rarely contains a monster bigger than a hero:
     * a boss is then the same 16x16 drawing shown at twice the size. Whole numbers
     * only — 1.5x a pixel sprite is a smear.
     */
    data class Frame(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val scale: Int = 1,
    ) {
        init {
            require(width > 0 && height > 0) { "a frame needs a size: $this" }
            require(scale >= 1) { "a frame cannot shrink below its own pixels: $this" }
        }

        /** How much room the frame takes once its own scale is applied. */
        val drawnWidth: Int get() = width * scale
        val drawnHeight: Int get() = height * scale
    }

    operator fun get(key: String): Frame? = frames[key]

    /** Throws rather than returning null: a name the game asks for and the sheet
     *  does not have is a broken pack, and the app refuses to start with one. */
    fun require(key: String): Frame =
        frames[key] ?: error("$key is not in the sheet")

    val size: Int get() = frames.size

    val keys: Set<String> get() = frames.keys

    /**
     * The grid the sheet is drawn on: its tallest *drawn* frame.
     *
     * Everything is scaled by `box / unit`, one factor for the whole cast, so the
     * pack keeps the proportions its artist drew — the boss looms, the grave stays
     * a marker on the floor. Fitting each frame to its own box instead would blow
     * that grave up to the size of a knight.
     */
    val unit: Int = frames.values.maxOfOrNull { maxOf(it.drawnWidth, it.drawnHeight) } ?: 1

    companion object {

        /**
         * Reads a mapping. Deliberately not JSON: no dependency, and a line a human
         * can edit while looking at the sheet in an image editor.
         *
         * ```
         * # name = x, y, width, height[, scale]
         * KNIGHT = 0, 129, 16, 15
         * BOSS   = 48, 113, 16, 15, 2
         * ```
         *
         * A malformed line is reported rather than thrown — the caller decides what
         * to do about it, and the app's answer is to name every bad line at once
         * instead of stopping at the first.
         */
        fun parse(text: String): Result {
            val frames = linkedMapOf<String, Frame>()
            val problems = mutableListOf<String>()

            text.lineSequence().forEachIndexed { index, raw ->
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) return@forEachIndexed

                val name = line.substringBefore('=', "").trim().uppercase()
                val numbers = line.substringAfter('=', "").split(',').map { it.trim() }
                if (name.isEmpty() || numbers.size !in 4..5) {
                    problems += "line ${index + 1}: expected NAME = x, y, width, height[, scale]"
                    return@forEachIndexed
                }
                val n = numbers.map { it.toIntOrNull() }
                if (n.any { it == null } || n[2]!! <= 0 || n[3]!! <= 0 || (n.getOrNull(4) ?: 1)!! < 1) {
                    problems += "line ${index + 1}: '$line' is not a rectangle"
                    return@forEachIndexed
                }
                frames[name] = Frame(n[0]!!, n[1]!!, n[2]!!, n[3]!!, n.getOrNull(4) ?: 1)
            }
            return Result(Atlas(frames), problems)
        }
    }

    data class Result(val atlas: Atlas, val problems: List<String>)
}
