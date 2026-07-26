package dev.taskbarhero.paint

/**
 * Dungeon-crawler palette: warm stone, parchment lettering, gold numbers and the
 * loot-grade ramp every ARPG player already knows how to read.
 *
 * Two rules keep it coherent: nothing is pure black or pure white — the darkest
 * value is the outline every sprite shares — and hue carries meaning. Red is
 * health, blue is progress, gold is currency, grade colours are loot only.
 */
object Palette {
    /** Behind the panel: the taskbar window's own shadow. */
    const val VOID = 0xFF0B0A0C.toInt()

    /** The line every sprite is outlined with, and what text is shadowed in. */
    const val OUTLINE = 0xFF14101A.toInt()

    const val STONE_DARK = 0xFF171418.toInt()
    const val STONE = 0xFF241F26.toInt()
    const val STONE_LIT = 0xFF332C36.toInt()
    const val FLOOR = 0xFF2E2731.toInt()

    /** Chunky panel bevel: lit from the top-left, as pixel art always is. */
    const val BEVEL_LIGHT = 0xFF5A4E5F.toInt()
    const val BEVEL_DARK = 0xFF0E0C10.toInt()

    const val PARCHMENT = 0xFFE8DCC0.toInt()
    const val PARCHMENT_DIM = 0xFF8E8272.toInt()

    const val GOLD = 0xFFF2B233.toInt()
    const val GOLD_DARK = 0xFF8C6218.toInt()

    /** Hero health. */
    const val HP = 0xFFC43A2E.toInt()
    const val HP_SOCKET = 0xFF3A1512.toInt()

    /** Monster health — bone, so the two bars are never confused at a glance. */
    const val FOE = 0xFFB9AE9A.toInt()
    const val FOE_SOCKET = 0xFF2A2622.toInt()

    /** Rune progress: this game's mana blue. */
    const val MANA = 0xFF3C6FD1.toInt()
    const val MANA_SOCKET = 0xFF14203A.toInt()

    /** The blob a fighter stands on. Translucent, so the flagstone shows through. */
    const val SHADOW = 0x99100C12.toInt()

    const val TORCH = 0xFFF2A03C.toInt()
    const val TORCH_CORE = 0xFFF8E08A.toInt()

    /**
     * The ten loot grades, lowest first. The wiki names the grades but never
     * publishes their hexes, so this is the classic ARPG ramp.
     */
    val GRADES = intArrayOf(
        0xFFB9B2A6.toInt(), // COMMON
        0xFF5FA84E.toInt(), // UNCOMMON
        0xFF4A7FD4.toInt(), // RARE
        0xFFE0A32E.toInt(), // LEGENDARY
        0xFFC43A2E.toInt(), // IMMORTAL
        0xFF9A5BD1.toInt(), // ARCANA
        0xFF38B2A8.toInt(), // BEYOND
        0xFFEDE6D2.toInt(), // CELESTIAL
        0xFFF2E28A.toInt(), // DIVINE
        0xFFE85AB8.toInt(), // COSMIC
    )

    fun grade(index: Int): Int = GRADES[index.coerceIn(0, GRADES.lastIndex)]
}

/**
 * The smallest drawing sink pixel art needs: filled rectangles, plus circles for
 * the few round things — torch glow, a coin, the shadow under a fighter.
 *
 * Keeping it here — rather than in the Android module — is what lets the real
 * layout code run three places: on a phone (Canvas), in the PNG previewer
 * (Graphics2D) and in unit tests (a recorder that checks nothing escapes the bar).
 */
interface Surface {
    fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int)

    fun circle(cx: Float, cy: Float, radius: Float, color: Int)
}
