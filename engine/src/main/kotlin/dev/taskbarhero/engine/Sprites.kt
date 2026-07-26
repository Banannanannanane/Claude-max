package dev.taskbarhero.engine

/**
 * Pixel art as palette-indexed string art: '.' is transparent, '1'..'9' index the
 * sprite's own colour list. Every fighter carries its outline as index 1, which
 * is what keeps a 12-pixel sprite readable against dungeon stone.
 *
 * Authoring here rather than in PNGs is deliberate: the art is diffable, it stays
 * in the pure module, and a unit test can assert every row is square and every
 * index has a colour.
 */
object Sprites {

    /**
     * Every fighter is drawn on the same grid, so one integer scale factor serves
     * all of them. Non-integer scaling is what makes pixel art look mushy — it
     * drops source pixels — so the boss is a chunkier 12x12 rather than a bigger
     * canvas.
     */
    const val FIGHTER_SIZE = 12

    private const val OUTLINE = 0xFF14101A.toInt()
    private const val BONE = 0xFFEDE6D2.toInt()
    private const val BLADE = 0xFFEDE6D2.toInt()
    private const val BONE_SHADE = 0xFFB9AE9A.toInt()
    private const val STEEL = 0xFFB9C6DE.toInt()
    private const val SKIN = 0xFFE8B48A.toInt()
    private const val TUNIC = 0xFF3E6FA8.toInt()
    private const val SHIELD = 0xFFC43A2E.toInt()
    private const val STONE = 0xFF7A7368.toInt()
    private const val STONE_SHADE = 0xFF4E4A44.toInt()
    private const val GOLD = 0xFFF2B233.toInt()
    private const val GOLD_DARK = 0xFF8C6218.toInt()
    private const val GOLD_LIT = 0xFFF8E08A.toInt()
    private const val EMBER = 0xFFF2A03C.toInt()

    class Art(val rows: List<String>, private val colors: IntArray) {
        val size: Int get() = rows.size

        /** Colour of a cell, or null where the sprite is transparent. */
        fun colorAt(x: Int, y: Int): Int? {
            val c = rows[y][x]
            if (c == '.') return null
            return colors[c - '1']
        }

        internal val paletteSize: Int get() = colors.size
    }

    fun of(key: SpriteKey): Art = ART.getValue(key)

    fun icon(key: IconKey): Art = ICONS.getValue(key)

    /**
     * The frame a hero shows right now: a grave while down, and for the knight a
     * two-frame swing driven by wall clock so every redraw agrees.
     */
    fun heroFrame(cls: HeroClass, nowMs: Long, down: Boolean = false): Art = when {
        down -> of(SpriteKey.GRAVE)
        cls == HeroClass.KNIGHT && (nowMs / 500L) % 2L != 0L -> of(SpriteKey.KNIGHT_SWING)
        else -> of(cls.sprite)
    }

    private val ART: Map<SpriteKey, Art> = mapOf(
        // 1 outline, 2 skin, 3 tunic, 4 steel, 5 shield, 6 blade
        SpriteKey.KNIGHT to art(
            intArrayOf(OUTLINE, SKIN, TUNIC, STEEL, SHIELD, BLADE),
            "...11111....",
            "..1444441...",
            "..1444441...",
            "..1212121.6.",
            "..1222221.6.",
            "..1444441.6.",
            ".51333331.6.",
            "55133333121.",
            ".51333331...",
            "..1331331...",
            "..111.111...",
            "............",
        ),
        SpriteKey.KNIGHT_SWING to art(
            intArrayOf(OUTLINE, SKIN, TUNIC, STEEL, SHIELD, BLADE),
            "...11111....",
            "..1444441...",
            "..1444441...",
            "..1212121...",
            "..1222221...",
            "..1444441666",
            ".513333312..",
            "551333331...",
            ".51333331...",
            "..1331331...",
            ".111..111...",
            "............",
        ),
        // 1 outline, 2 skin, 3 cloak, 4 hood, 5 quiver, 6 bow
        SpriteKey.RANGER to art(
            intArrayOf(OUTLINE, SKIN, 0xFF2F6B3A.toInt(), 0xFF4E9E56.toInt(), 0xFF8C5A2E.toInt(), 0xFFC9A227.toInt()),
            "...11111....",
            "..1444441...",
            "..1422241.6.",
            "..1412141..6",
            "..14444412.6",
            "..1333331..6",
            "511333331..6",
            "..1333331.6.",
            "..1331331...",
            "..11..11....",
            "............",
            "............",
        ),
        // 1 outline, 2 skin, 3 robe, 4 hat, 5 trim, 6 staff, 7 orb
        SpriteKey.MAGE to art(
            intArrayOf(OUTLINE, SKIN, 0xFF4B3A8C.toInt(), 0xFF6B54C6.toInt(), 0xFFC9A227.toInt(), 0xFF8C5A2E.toInt(), 0xFF6ED0F2.toInt()),
            ".....1......",
            "....141.....",
            "...14441....",
            "..1444441.7.",
            "..1555551.6.",
            "..1212121.6.",
            "..1333331.6.",
            ".13333331.6.",
            ".1333333316.",
            ".1333333316.",
            ".111111111..",
            "............",
        ),
        // 1 outline, 2 body, 3 highlight, 4 eye, 5 pupil
        SpriteKey.SLIME to art(
            intArrayOf(0xFF0E2A16.toInt(), 0xFF5FA84E.toInt(), 0xFF8FD070.toInt(), BONE, OUTLINE),
            "............",
            "............",
            "............",
            "....1111....",
            "..11322211..",
            ".1332222221.",
            "132244224421",
            "132255225521",
            "122222222221",
            "122222222221",
            "122222222221",
            ".1111111111.",
        ),
        // 1 outline, 2 wing, 3 body, 4 eye
        SpriteKey.BAT to art(
            intArrayOf(OUTLINE, 0xFF5A4E6F.toInt(), 0xFF8A7AA8.toInt(), SHIELD),
            "............",
            "11........11",
            "121......121",
            "1221....1221",
            "122211112221",
            "122213331221",
            "...134431...",
            "...133331...",
            "....1331....",
            ".....11.....",
            "............",
            "............",
        ),
        // 1 outline, 2 bone, 3 bone shade
        SpriteKey.SKELETON to art(
            intArrayOf(OUTLINE, BONE, BONE_SHADE),
            "............",
            "...11111....",
            "..1222221...",
            "..1211121...",
            "..1222221...",
            "...12321....",
            "..1122211...",
            ".1213332121.",
            "..1233321...",
            "...12321....",
            "..12.1.21...",
            "..11...11...",
        ),
        // 1 outline, 2 skin, 3 skin shade, 4 eye, 5 rags
        SpriteKey.GOBLIN to art(
            intArrayOf(OUTLINE, 0xFF6E9B3E.toInt(), 0xFF4E7028.toInt(), 0xFFE8DC4A.toInt(), 0xFF8C5A2E.toInt()),
            "............",
            "....1111....",
            "...122221...",
            "..12422421..",
            "...123321...",
            "...122221...",
            "..12222221..",
            ".1122222211.",
            "..15555551..",
            "..12211221..",
            "..111..111..",
            "............",
        ),
        // 1 outline, 2 stone, 3 stone shade, 4 rune glow
        SpriteKey.GOLEM to art(
            intArrayOf(OUTLINE, STONE, STONE_SHADE, EMBER),
            "...111111...",
            "..12222221..",
            "..12414121..",
            "..12222221..",
            ".1222222221.",
            "112333333211",
            "112333333211",
            ".1233333321.",
            ".1222222221.",
            "..12.11.21..",
            "..122..221..",
            ".1122..2211.",
        ),
        // 1 outline, 2 hide, 3 body, 4 horn, 5 eye
        SpriteKey.BOSS to art(
            intArrayOf(OUTLINE, 0xFF8C2A22.toInt(), 0xFFC43A2E.toInt(), BONE, 0xFFF2E28A.toInt()),
            ".4........4.",
            ".41......14.",
            "..14111141..",
            "..12222221..",
            "..12522521..",
            "..12333321..",
            "...123321...",
            ".1113333111.",
            "112333333211",
            ".1233333321.",
            "..12333321..",
            "..11....11..",
        ),
        // 1 outline, 2 stone, 3 engraving
        SpriteKey.GRAVE to art(
            intArrayOf(OUTLINE, STONE, STONE_SHADE),
            "............",
            "...11111....",
            "..1222221...",
            ".1222332221.",
            ".1222332221.",
            ".1233333321.",
            ".1233333321.",
            ".1222332221.",
            ".1222332221.",
            ".1222222221.",
            "112222222211",
            "111111111111",
        ),
    )

    private val ICONS: Map<IconKey, Art> = mapOf(
        // 1 rim, 2 face, 3 shine
        IconKey.COIN to art(
            intArrayOf(GOLD_DARK, GOLD, GOLD_LIT),
            ".111.",
            "12321",
            "12221",
            "12221",
            ".111.",
        ),
        // 1 outline, 2 bone
        IconKey.SKULL to art(
            intArrayOf(OUTLINE, BONE),
            ".222.",
            "21212",
            "22222",
            ".212.",
            ".2.2.",
        ),
        // 1 outline, 2 blade
        IconKey.SWORD to art(
            intArrayOf(OUTLINE, BONE),
            "...12",
            "..121",
            ".121.",
            "121..",
            "21...",
        ),
    )

    private fun art(colors: IntArray, vararg rows: String): Art {
        val n = rows.size
        rows.forEach { row ->
            require(row.length == n) { "sprite must be square ($n): '$row' is ${row.length}" }
            row.forEach { c ->
                require(c == '.' || (c in '1'..'9' && c - '1' < colors.size)) {
                    "'$c' has no colour in a ${colors.size}-colour palette: '$row'"
                }
            }
        }
        return Art(rows.toList(), colors)
    }
}

enum class IconKey { COIN, SKULL, SWORD }
