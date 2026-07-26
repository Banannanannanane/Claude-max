package dev.dotmatrix.taskbarhero.engine

/**
 * Flavour layer. Deterministic on purpose: the same (act, wave, index) always
 * shows the same monster, so a widget redrawn twice in a row does not flicker
 * between two sprites.
 */
object Bestiary {

    private val MOBS = listOf(
        Mob("SLIME", SpriteKey.SLIME),
        Mob("BAT", SpriteKey.BAT),
        Mob("SKELETON", SpriteKey.SKELETON),
        Mob("GOBLIN", SpriteKey.SKELETON),
        Mob("WRAITH", SpriteKey.BAT),
        Mob("GOLEM", SpriteKey.GOLEM),
    )

    private val BOSSES = listOf(
        "SLIME KING", "NIGHT MOTH", "BONE PRIOR", "RUST GOLEM",
        "DOT WYRM", "GLYPH EATER", "NULL WARDEN",
    )

    data class Mob(val name: String, val sprite: SpriteKey)

    fun mob(act: Int, wave: Int, enemyIdx: Int, b: Balance = Balance()): Mob {
        if (b.isBossWave(wave)) {
            return Mob(BOSSES[(act - 1).mod(BOSSES.size)], SpriteKey.BOSS)
        }
        // Deeper acts unlock the heavier half of the roster.
        val pool = (2 + act).coerceAtMost(MOBS.size)
        val floor = if (act >= 4) 2 else 0
        val span = pool - floor
        return MOBS[floor + hash(act, wave, enemyIdx).mod(span)]
    }

    private fun hash(a: Int, w: Int, i: Int): Int {
        var h = a * 73_856_093 xor w * 19_349_663 xor i * 83_492_791
        h = h xor (h ushr 13)
        return h and 0x7fff_ffff
    }
}

enum class SpriteKey { HERO, HERO_SWING, SLIME, BAT, SKELETON, GOLEM, BOSS, GRAVE }

/** Loot names for the boss ticker — pure garnish, no stats behind it. */
object Loot {
    private val TIERS = listOf("COMMON", "RARE", "GLYPH", "IMMORTAL")
    private val ITEMS = listOf(
        "DOT BLADE", "NDOT PLATE", "RED CAPE", "PIXEL RING",
        "CUBE SHARD", "DOT LAMP", "TASKBAR TOTEM",
    )

    fun roll(act: Int, bossKills: Long): String {
        val n = (act * 31 + bossKills.toInt() * 17).mod(ITEMS.size)
        val tier = TIERS[(act - 1).coerceIn(0, TIERS.lastIndex)]
        return "$tier ${ITEMS[n]}"
    }
}
