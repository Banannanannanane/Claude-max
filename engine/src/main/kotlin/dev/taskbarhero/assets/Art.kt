package dev.taskbarhero.assets

/**
 * The names the game asks the sheets for.
 *
 * They are constants rather than raw strings so that a mapping missing a sprite
 * is caught once, at startup, by walking these lists — instead of at the moment
 * that monster first appears, four acts into a run.
 */
object Art {

    // --- dungeon.png ---------------------------------------------------------

    const val KNIGHT = "KNIGHT"
    const val RANGER = "RANGER"
    const val MAGE = "MAGE"
    const val GRAVE = "GRAVE"

    const val BOSS = "BOSS"

    const val WALL = "WALL"
    const val WALL_TOP = "WALL_TOP"
    const val FLOOR = "FLOOR"
    const val FLOOR_WORN = "FLOOR_WORN"
    const val TORCH = "TORCH"
    const val DOOR = "DOOR"
    const val CHEST = "CHEST"

    const val COIN = "COIN"
    const val POTION = "POTION"
    const val GEM = "GEM"
    const val SWORD = "SWORD"

    /** Every monster the bestiary can name, weakest first. */
    val MONSTERS = listOf("SLIME", "IMP", "SKELETON", "GOBLIN", "WRAITH", "ORC", "ZOMBIE", "OGRE")

    val dungeonKeys: List<String> = listOf(
        KNIGHT, RANGER, MAGE, GRAVE, BOSS,
        WALL, WALL_TOP, FLOOR, FLOOR_WORN, TORCH, DOOR, CHEST,
        COIN, POTION, GEM, SWORD,
    ) + MONSTERS

    // --- ui.png --------------------------------------------------------------

    const val PANEL = "PANEL"
    const val PANEL_LIT = "PANEL_LIT"
    const val PANEL_INSET = "PANEL_INSET"
    const val BUTTON = "BUTTON"
    const val BUTTON_DOWN = "BUTTON_DOWN"
    const val BUTTON_OFF = "BUTTON_OFF"
    const val CHECK = "CHECK"
    const val CROSS = "CROSS"

    /** A bar is three slices: two caps that keep their width, a middle that stretches. */
    enum class Bar(val prefix: String) {
        BACK("BAR_BACK"), RED("BAR_RED"), BLUE("BAR_BLUE"), GOLD("BAR_GOLD");

        val left get() = "${prefix}_L"
        val mid get() = "${prefix}_M"
        val right get() = "${prefix}_R"
    }

    val uiKeys: List<String> =
        listOf(PANEL, PANEL_LIT, PANEL_INSET, BUTTON, BUTTON_DOWN, BUTTON_OFF, CHECK, CROSS) +
            Bar.entries.flatMap { listOf(it.left, it.mid, it.right) }

    /**
     * Names the game will ask for and this sheet cannot answer. Empty means the
     * pack is complete; anything else is what the startup check reports.
     */
    fun missingFrom(atlas: Atlas, keys: List<String>): List<String> =
        keys.filterNot { it in atlas.keys }
}
