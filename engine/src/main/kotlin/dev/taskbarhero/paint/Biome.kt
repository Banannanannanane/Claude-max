package dev.taskbarhero.paint

/**
 * The room's masonry, which changes as the run goes deeper.
 *
 * Acts are the only progression the player watches for hours, so they should not
 * all look the same. Five biomes cycle every three acts: the wall, its lit lip
 * and the flagstone are all that change, which keeps every sprite, bar and letter
 * legible against any of them.
 */
data class Biome(
    val label: String,
    val stoneDark: Int,
    val stone: Int,
    val stoneLit: Int,
    val floor: Int,
) {
    companion object {
        /** Acts per biome, so a biome lasts long enough to be noticed. */
        private const val ACTS_EACH = 3

        val DUNGEON = Biome("DUNGEON", 0xFF171418.toInt(), 0xFF241F26.toInt(), 0xFF332C36.toInt(), 0xFF2E2731.toInt())
        val CAVE = Biome("CAVE", 0xFF171210.toInt(), 0xFF2A211A.toInt(), 0xFF3D3024.toInt(), 0xFF33281F.toInt())
        val CATACOMB = Biome("CATACOMB", 0xFF14171A.toInt(), 0xFF1F262A.toInt(), 0xFF2E383D.toInt(), 0xFF283033.toInt())
        val INFERNO = Biome("INFERNO", 0xFF1A100F.toInt(), 0xFF2B1715.toInt(), 0xFF42211D.toInt(), 0xFF361B18.toInt())
        val VOID = Biome("VOID", 0xFF101322.toInt(), 0xFF1A1E33.toInt(), 0xFF272C4A.toInt(), 0xFF20243D.toInt())

        val ALL = listOf(DUNGEON, CAVE, CATACOMB, INFERNO, VOID)

        fun of(act: Int): Biome = ALL[((act - 1).coerceAtLeast(0) / ACTS_EACH).mod(ALL.size)]
    }
}
