package dev.dotmatrix.taskbarhero.engine

/**
 * Pixel art as string art: '#' is drawn in the foreground colour, '+' in the
 * Nothing red accent, '.' is transparent. Squares, not circles — the dots are
 * reserved for text and bars, so the fighters read as sprites and the chrome
 * reads as a matrix display.
 */
object Sprites {

    data class Art(val rows: List<String>) {
        val size: Int get() = rows.size

        fun cell(x: Int, y: Int): Char = rows[y][x]
    }

    fun of(key: SpriteKey): Art = ART.getValue(key)

    /** Two-frame swing animation, driven by wall clock so every redraw agrees. */
    fun heroFrame(nowMs: Long, downMs: Long = 0L): Art = when {
        downMs > 0L -> of(SpriteKey.GRAVE)
        (nowMs / 500L) % 2L == 0L -> of(SpriteKey.HERO)
        else -> of(SpriteKey.HERO_SWING)
    }

    private val ART: Map<SpriteKey, Art> = mapOf(
        SpriteKey.HERO to art(
            "............",
            "...####.....",
            "..######...#",
            "..##..##..#.",
            "..######.#..",
            "+..####.#...",
            "++.#####....",
            "+.######....",
            "..#####.....",
            "...##.##....",
            "..###.###...",
            "............",
        ),
        SpriteKey.HERO_SWING to art(
            "............",
            "...####.....",
            "..######....",
            "..##..##....",
            "..##########",
            "+..####..#..",
            "++.#####....",
            "+.######....",
            "..#####.....",
            "...##.##....",
            ".###...###..",
            "............",
        ),
        SpriteKey.SLIME to art(
            "............",
            "............",
            "............",
            "....####....",
            "..########..",
            ".##########.",
            ".##.####.##.",
            ".##########.",
            "############",
            "############",
            ".##########.",
            "..##....##..",
        ),
        SpriteKey.BAT to art(
            "............",
            "#..........#",
            "##........##",
            "###.####.###",
            "############",
            "#.##.##.##.#",
            "...#++#.....",
            "...#.##.#...",
            "....####....",
            ".....##.....",
            "............",
            "............",
        ),
        SpriteKey.SKELETON to art(
            "............",
            "...######...",
            "..########..",
            "..##.##.##..",
            "..########..",
            "...#.##.#...",
            "....####....",
            "..##.##.##..",
            "..#.####.#..",
            "....####....",
            "...##..##...",
            "..##....##..",
        ),
        SpriteKey.GOLEM to art(
            "..########..",
            ".##########.",
            "##.+####+.##",
            "############",
            "##.##..##.##",
            "############",
            ".##########.",
            ".##.####.##.",
            ".##########.",
            "..##....##..",
            "..##....##..",
            ".###....###.",
        ),
        SpriteKey.BOSS to art(
            "#..............#",
            "##....####....##",
            "###..######..###",
            "####.##++##.####",
            "#####.####.#####",
            "################",
            "##.##########.##",
            "...##########...",
            "...####++####...",
            "...##########...",
            "....########....",
            "...#.######.#...",
            "..##..####..##..",
            "..#....##....#..",
            ".......##.......",
            "................",
        ),
        SpriteKey.GRAVE to art(
            "............",
            "...######...",
            "..###..###..",
            ".####..####.",
            ".##......##.",
            ".##......##.",
            ".####..####.",
            ".####..####.",
            ".##########.",
            "############",
            "############",
            "............",
        ),
    )

    private fun art(vararg rows: String): Art {
        val n = rows.size
        rows.forEach {
            require(it.length == n) { "sprite must be square ($n): '$it' is ${it.length}" }
        }
        return Art(rows.toList())
    }
}
