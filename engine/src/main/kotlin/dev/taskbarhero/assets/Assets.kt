package dev.taskbarhero.assets

/**
 * Everything the game must be given before it will run.
 *
 * The game draws no art of its own — not a sprite, not a glyph, not an icon. That
 * is a deliberate constraint, and this file is what makes it enforceable: it is at
 * once the shopping list, the loader's contract, and the text of the error screen
 * shown when something is missing. There is no fallback and no placeholder, so a
 * pack that is half there cannot quietly ship.
 */
object AssetManifest {

    /** What a file is for, which decides how it is validated once present. */
    enum class Kind {
        /** A PNG atlas: many sprites in one image. */
        ATLAS,

        /** A text mapping saying which rectangle of an atlas is which sprite. */
        MAPPING,

        /** A TrueType file, drawn straight rather than baked into an image. */
        FONT,

        /** A licence or attribution file that must travel with the art. */
        LICENCE,
    }

    /**
     * One required file.
     *
     * [why] is written for a human deciding whether a candidate pack fits, and is
     * printed verbatim when the file is missing — a build that stops should say
     * what it is waiting for, not just that it is waiting.
     */
    data class Asset(
        val path: String,
        val kind: Kind,
        val why: String,
    )

    const val DUNGEON = "art/dungeon.png"
    const val UI = "art/ui.png"
    const val FONT = "art/m5x7.ttf"

    /** Files without which there is no game at all. */
    val required: List<Asset> = listOf(
        Asset(
            path = DUNGEON,
            kind = Kind.ATLAS,
            why = "The cast and the room: three hero classes, a grave, eight monsters, " +
                "a boss, wall and floor tiles, and the props the numbers stand for.",
        ),
        Asset(
            path = "art/dungeon.txt",
            kind = Kind.MAPPING,
            why = "Which rectangle of dungeon.png is which sprite, one line each.",
        ),
        Asset(
            path = UI,
            kind = Kind.ATLAS,
            why = "The interface: panels, buttons in their three states, and the " +
                "three slices a bar is built from.",
        ),
        Asset(
            path = "art/ui.txt",
            kind = Kind.MAPPING,
            why = "Which rectangle of ui.png is which piece of interface.",
        ),
        Asset(
            path = FONT,
            kind = Kind.FONT,
            why = "Every letter and number on screen. A pixel font, drawn without " +
                "anti-aliasing at whole multiples of 16px so the glyphs stay square.",
        ),
        Asset(
            path = "art/ATTRIBUTION.txt",
            kind = Kind.LICENCE,
            why = "Where the art comes from and under which licence. The repository " +
                "is public, so the licence has to allow redistribution.",
        ),
    )

    val all: List<Asset> get() = required

    /**
     * Checks a set of file names against the manifest. Takes the names rather than
     * a filesystem so the same call answers for an APK's assets, a directory on
     * disk, or a test.
     */
    fun check(present: Set<String>): Report = Report(
        missing = required.filterNot { it.path in present },
    )

    /**
     * What the app found. [isPlayable] is the gate: false means the app shows this
     * report instead of a game, because a game missing its art is not a game.
     */
    data class Report(val missing: List<Asset>) {
        val isPlayable: Boolean get() = missing.isEmpty()

        /** The error screen's text, and the one place its wording lives. */
        fun lines(): List<String> = buildList {
            if (missing.isEmpty()) {
                add("All required art is present.")
                return@buildList
            }
            add("Missing ${missing.size} required file(s):")
            missing.forEach {
                add("")
                add(it.path)
                add(it.why)
            }
        }
    }
}
