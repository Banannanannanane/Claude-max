package dev.taskbarhero.engine

/**
 * Flavour layer. Deterministic on purpose: the same (act, wave, index) always
 * shows the same monster, so a widget redrawn twice in a row does not flicker
 * between two sprites.
 */
object Bestiary {

    /** Weakest first: the roster is a ladder, and the act decides which rungs. */
    private val MOBS = listOf(
        Mob("SLIME", SpriteKey.SLIME),
        Mob("BAT", SpriteKey.BAT),
        Mob("SKELETON", SpriteKey.SKELETON),
        Mob("GOBLIN", SpriteKey.GOBLIN),
        Mob("WRAITH", SpriteKey.WRAITH),
        Mob("ORC", SpriteKey.ORC),
        Mob("ZOMBIE", SpriteKey.ZOMBIE),
        Mob("GOLEM", SpriteKey.GOLEM),
    )

    private val BOSSES = listOf(
        "SLIME KING", "NIGHT MOTH", "BONE PRIOR", "RUST GOLEM",
        "PIT WYRM", "SOUL EATER", "DEEP WARDEN",
    )

    data class Mob(val name: String, val sprite: SpriteKey)

    fun mob(act: Int, wave: Int, enemyIdx: Int, b: Balance = Balance()): Mob {
        if (b.isBossWave(wave)) {
            return Mob(BOSSES[(act - 1).mod(BOSSES.size)], SpriteKey.BOSS)
        }
        // The window slides up the ladder: deeper acts unlock the heavier monsters
        // and stop showing the chaff, but always keep three of them in play so a
        // wave never turns into one monster on repeat.
        val pool = (2 + act).coerceAtMost(MOBS.size)
        val floor = ((act - 2) / 2).coerceIn(0, MOBS.size - 3)
        val span = pool - floor
        return MOBS[floor + hash(act, wave, enemyIdx).mod(span)]
    }

    private fun hash(a: Int, w: Int, i: Int): Int {
        var h = a * 73_856_093 xor w * 19_349_663 xor i * 83_492_791
        h = h xor (h ushr 13)
        return h and 0x7fff_ffff
    }
}

enum class SpriteKey {
    KNIGHT, KNIGHT_SWING, RANGER, MAGE, GRAVE,
    SLIME, BAT, SKELETON, GOBLIN, WRAITH, ORC, ZOMBIE, GOLEM, BOSS,
}

/**
 * Boss drops. Pure garnish — no stats behind them — but the grade drives the
 * colour the caption is printed in, which is the one ARPG convention nobody needs
 * explained.
 */
object Loot {

    /** The ten grades of TBH, lowest first. */
    val GRADES = listOf(
        "COMMON", "UNCOMMON", "RARE", "LEGENDARY", "IMMORTAL",
        "ARCANA", "BEYOND", "CELESTIAL", "DIVINE", "COSMIC",
    )

    private val ITEMS = listOf(
        "BLADE", "PLATE", "CLOAK", "RING", "SHARD", "IDOL", "TOTEM", "RELIC",
    )

    /** A grade index plus an item name. [label] is what the ticker prints. */
    data class Drop(val grade: Int, val item: String) {
        val label: String get() = "${GRADES[grade]} $item"
    }

    /** Deeper acts drop better: act 1 is COMMON, act 10 and beyond COSMIC. */
    fun roll(act: Int, bossKills: Long): Drop {
        val item = ITEMS[(act * 31 + bossKills.toInt() * 17).mod(ITEMS.size)]
        return Drop((act - 1).coerceIn(0, GRADES.lastIndex), item)
    }
}
