package dev.taskbarhero.engine

/**
 * A 5x7 bitmap face in the tradition of small-window RPGs: one square pixel per
 * lit cell, drawn over a one-pixel shadow so parchment lettering stays readable
 * on stone.
 *
 * Kept in the pure module so the layout maths — and the glyph table itself — are
 * unit-testable without an Android device.
 */
object PixelFont {

    const val WIDTH = 5
    const val HEIGHT = 7

    /** Lit cell of a laid-out string, relative to the text origin. */
    data class Cell(val x: Int, val y: Int)

    private val GLYPHS: Map<Char, List<String>> = buildMap {
        put(' ', rows("00000", "00000", "00000", "00000", "00000", "00000", "00000"))
        put('A', rows("01110", "10001", "10001", "11111", "10001", "10001", "10001"))
        put('B', rows("11110", "10001", "10001", "11110", "10001", "10001", "11110"))
        put('C', rows("01110", "10001", "10000", "10000", "10000", "10001", "01110"))
        put('D', rows("11110", "10001", "10001", "10001", "10001", "10001", "11110"))
        put('E', rows("11111", "10000", "10000", "11110", "10000", "10000", "11111"))
        put('F', rows("11111", "10000", "10000", "11110", "10000", "10000", "10000"))
        put('G', rows("01110", "10001", "10000", "10111", "10001", "10001", "01111"))
        put('H', rows("10001", "10001", "10001", "11111", "10001", "10001", "10001"))
        put('I', rows("11111", "00100", "00100", "00100", "00100", "00100", "11111"))
        put('J', rows("00111", "00010", "00010", "00010", "00010", "10010", "01100"))
        put('K', rows("10001", "10010", "10100", "11000", "10100", "10010", "10001"))
        put('L', rows("10000", "10000", "10000", "10000", "10000", "10000", "11111"))
        put('M', rows("10001", "11011", "10101", "10101", "10001", "10001", "10001"))
        put('N', rows("10001", "11001", "10101", "10011", "10001", "10001", "10001"))
        put('O', rows("01110", "10001", "10001", "10001", "10001", "10001", "01110"))
        put('P', rows("11110", "10001", "10001", "11110", "10000", "10000", "10000"))
        put('Q', rows("01110", "10001", "10001", "10001", "10101", "10011", "01101"))
        put('R', rows("11110", "10001", "10001", "11110", "10100", "10010", "10001"))
        put('S', rows("01111", "10000", "10000", "01110", "00001", "00001", "11110"))
        put('T', rows("11111", "00100", "00100", "00100", "00100", "00100", "00100"))
        put('U', rows("10001", "10001", "10001", "10001", "10001", "10001", "01110"))
        put('V', rows("10001", "10001", "10001", "10001", "10001", "01010", "00100"))
        put('W', rows("10001", "10001", "10001", "10101", "10101", "11011", "10001"))
        put('X', rows("10001", "10001", "01010", "00100", "01010", "10001", "10001"))
        put('Y', rows("10001", "10001", "01010", "00100", "00100", "00100", "00100"))
        put('Z', rows("11111", "00001", "00010", "00100", "01000", "10000", "11111"))
        put('0', rows("01110", "10001", "10011", "10101", "11001", "10001", "01110"))
        put('1', rows("00100", "01100", "00100", "00100", "00100", "00100", "01110"))
        put('2', rows("01110", "10001", "00001", "00010", "00100", "01000", "11111"))
        put('3', rows("11111", "00010", "00100", "00010", "00001", "10001", "01110"))
        put('4', rows("00010", "00110", "01010", "10010", "11111", "00010", "00010"))
        put('5', rows("11111", "10000", "11110", "00001", "00001", "10001", "01110"))
        put('6', rows("00110", "01000", "10000", "11110", "10001", "10001", "01110"))
        put('7', rows("11111", "00001", "00010", "00100", "01000", "01000", "01000"))
        put('8', rows("01110", "10001", "10001", "01110", "10001", "10001", "01110"))
        put('9', rows("01110", "10001", "10001", "01111", "00001", "00010", "01100"))
        put('-', rows("00000", "00000", "00000", "11111", "00000", "00000", "00000"))
        put('+', rows("00000", "00100", "00100", "11111", "00100", "00100", "00000"))
        put('=', rows("00000", "00000", "11111", "00000", "11111", "00000", "00000"))
        put('.', rows("00000", "00000", "00000", "00000", "00000", "00000", "00100"))
        put(',', rows("00000", "00000", "00000", "00000", "00100", "00100", "01000"))
        put(':', rows("00000", "00100", "00000", "00000", "00000", "00100", "00000"))
        put('/', rows("00001", "00001", "00010", "00100", "01000", "10000", "10000"))
        put('%', rows("10001", "10010", "00100", "00100", "00100", "01001", "10001"))
        put('!', rows("00100", "00100", "00100", "00100", "00100", "00000", "00100"))
        put('?', rows("01110", "10001", "00001", "00010", "00100", "00000", "00100"))
        put('#', rows("01010", "01010", "11111", "01010", "11111", "01010", "01010"))
        put('*', rows("00000", "10101", "01110", "11111", "01110", "10101", "00000"))
        put('>', rows("10000", "01000", "00100", "00010", "00100", "01000", "10000"))
        put('<', rows("00001", "00010", "00100", "01000", "00100", "00010", "00001"))
        put('[', rows("01110", "01000", "01000", "01000", "01000", "01000", "01110"))
        put(']', rows("01110", "00010", "00010", "00010", "00010", "00010", "01110"))
    }

    private fun rows(vararg r: String): List<String> {
        require(r.size == HEIGHT) { "glyph needs $HEIGHT rows, got ${r.size}" }
        r.forEach { require(it.length == WIDTH) { "glyph row must be $WIDTH wide: '$it'" } }
        return r.toList()
    }

    val supported: Set<Char> get() = GLYPHS.keys

    fun glyph(c: Char): List<String> = GLYPHS[c.uppercaseChar()] ?: GLYPHS.getValue('?')

    /** Width of [text] in pixel cells, including inter-letter [spacing]. */
    fun measure(text: String, spacing: Int = 1): Int =
        if (text.isEmpty()) 0 else text.length * WIDTH + (text.length - 1) * spacing

    /**
     * Lays [text] out into lit cells, origin at the top-left of the first glyph.
     * The renderer just fills a square per cell.
     */
    fun cells(text: String, spacing: Int = 1): List<Cell> {
        val out = ArrayList<Cell>(text.length * 12)
        var ox = 0
        for (c in text) {
            val g = glyph(c)
            for (y in 0 until HEIGHT) {
                val row = g[y]
                for (x in 0 until WIDTH) {
                    if (row[x] != '0') out.add(Cell(ox + x, y))
                }
            }
            ox += WIDTH + spacing
        }
        return out
    }

    /** Truncates to fit [maxCells] wide, appending nothing — the bar has no room for ellipses. */
    fun clip(text: String, maxCells: Int, spacing: Int = 1): String {
        if (measure(text, spacing) <= maxCells) return text
        var n = text.length
        while (n > 0 && measure(text.take(n), spacing) > maxCells) n--
        return text.take(n)
    }

    /**
     * Like [clip] but drops whole words: "BOSS 3 DOWN / GLYPH CUBE SHARD" becomes
     * "BOSS 3" rather than "BOSS 3 D". Falls back to a hard clip when even the
     * first word is too long.
     */
    fun clipWords(text: String, maxCells: Int, spacing: Int = 1): String {
        if (measure(text, spacing) <= maxCells) return text
        val words = text.split(' ')
        var out = ""
        for (word in words) {
            val candidate = if (out.isEmpty()) word else "$out $word"
            if (measure(candidate, spacing) > maxCells) break
            out = candidate
        }
        // Trailing separators read as truncation artefacts once the tail is gone.
        val trimmed = out.trimEnd(' ', '/', '-', ',')
        return if (trimmed.isEmpty()) clip(text, maxCells, spacing) else trimmed
    }
}
