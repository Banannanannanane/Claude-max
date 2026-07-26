package dev.taskbarhero.paint

import dev.taskbarhero.engine.Sprites

/**
 * How a party stands in a room.
 *
 * The party is sized as a block rather than per sprite: three heroes at the scale
 * one hero could afford would pile into an unreadable smear. They overlap by a
 * quarter — enough to read as a formation, little enough that each class stays
 * recognisable — and the front hero, the one taking the hits, stands nearest the
 * monster and on top of the others.
 */
object Formation {

    private const val OVERLAP = 0.25f

    /** Horizontal step between two members. */
    fun step(side: Float): Float = side * (1f - OVERLAP)

    /** Width the whole party occupies at a given sprite size. */
    fun width(members: Int, side: Float): Float = side + step(side) * (members - 1)

    /**
     * The largest integer scale at which [members] fit the space allowed. Integer
     * only: fractional scaling drops art pixels.
     */
    fun scale(members: Int, maxWidth: Float, maxHeight: Float): Float {
        val slot = Sprites.FIGHTER_SIZE
        val span = 1f + (1f - OVERLAP) * (members - 1).coerceAtLeast(0)
        val byWidth = maxWidth / (slot * span)
        val byHeight = maxHeight / slot
        return minOf(byWidth, byHeight).toInt().coerceAtLeast(1).toFloat()
    }

    /**
     * Left edge of member [index], counting the front hero as 0. Later members
     * stand further from the monster, so the party reads back-to-front.
     */
    fun offset(index: Int, members: Int, side: Float): Float = step(side) * (members - 1 - index)

    /** Draw order: rearmost first, so the front hero overlaps the rest. */
    fun drawOrder(members: Int): IntProgression = (members - 1) downTo 0
}
