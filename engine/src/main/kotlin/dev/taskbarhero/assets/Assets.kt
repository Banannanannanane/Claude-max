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

    /** Files without which there is no game at all. */
    val required: List<Asset> = listOf(
        Asset(
            path = "art/fighters.png",
            kind = Kind.ATLAS,
            why = "The cast: three hero classes, their attack pose, a marker for a " +
                "fallen hero, at least six monsters and one boss.",
        ),
        Asset(
            path = "art/fighters.txt",
            kind = Kind.MAPPING,
            why = "Which rectangle of fighters.png is which fighter, one line per frame.",
        ),
        Asset(
            path = "art/ui.png",
            kind = Kind.ATLAS,
            why = "The interface: panel and window frames, buttons in their three " +
                "states, a bar frame and its fill, one inventory slot.",
        ),
        Asset(
            path = "art/ui.txt",
            kind = Kind.MAPPING,
            why = "Which rectangle of ui.png is which piece of interface.",
        ),
        Asset(
            path = "art/font.png",
            kind = Kind.ATLAS,
            why = "A bitmap pixel font: A-Z, 0-9 and + - . % / : x, as an image. " +
                "Not a TTF — the glyphs have to land on whole pixels.",
        ),
        Asset(
            path = "art/font.txt",
            kind = Kind.MAPPING,
            why = "Which rectangle of font.png is which character.",
        ),
        Asset(
            path = "art/ATTRIBUTION.txt",
            kind = Kind.LICENCE,
            why = "Where the art comes from and under which licence. The repository " +
                "is public, so the licence has to allow redistribution.",
        ),
    )

    /** Files the game runs without, each costing exactly one feature. */
    val optional: List<Asset> = listOf(
        Asset(
            path = "art/room.png",
            kind = Kind.ATLAS,
            why = "Wall and floor tiles, and props: torches, columns, doors, chests. " +
                "Without it the room is a flat colour.",
        ),
        Asset(
            path = "art/room.txt",
            kind = Kind.MAPPING,
            why = "Which rectangle of room.png is which tile.",
        ),
        Asset(
            path = "art/icons.png",
            kind = Kind.ATLAS,
            why = "Coin, potion, gem, rune. Without it those numbers stand alone.",
        ),
        Asset(
            path = "art/icons.txt",
            kind = Kind.MAPPING,
            why = "Which rectangle of icons.png is which icon.",
        ),
    )

    val all: List<Asset> get() = required + optional

    /**
     * Checks a set of file names against the manifest. Takes the names rather than
     * a filesystem so the same call answers for an APK's assets, a directory on
     * disk, or a test.
     */
    fun check(present: Set<String>): Report = Report(
        missing = required.filterNot { it.path in present },
        absentOptional = optional.filterNot { it.path in present },
    )

    /**
     * What the app found. [isPlayable] is the gate: false means the app shows this
     * report instead of a game, because a game missing its art is not a game.
     */
    data class Report(
        val missing: List<Asset>,
        val absentOptional: List<Asset>,
    ) {
        val isPlayable: Boolean get() = missing.isEmpty()

        /** The error screen's text, and the one place its wording lives. */
        fun lines(): List<String> = buildList {
            if (missing.isEmpty()) {
                add("All required art is present.")
            } else {
                add("Missing ${missing.size} required file(s):")
                missing.forEach {
                    add("")
                    add(it.path)
                    add(it.why)
                }
            }
            if (absentOptional.isNotEmpty()) {
                add("")
                add("Optional, ${absentOptional.size} not provided:")
                absentOptional.forEach { add("  ${it.path} — ${it.why}") }
            }
        }
    }
}
