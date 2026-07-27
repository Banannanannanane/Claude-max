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
     *
     * [slotFraction] is how much of a fighter's box its art actually fills across —
     * 1 for the built-in sprites, which are drawn square, and about a half for a
     * sheet of slim 16x28 frames. Without it a pack of narrow heroes would be sized
     * as if each stood in a box twice its width, and the party would come out at
     * half the scale the room can afford.
     */
    fun scale(
        members: Int,
        maxWidth: Float,
        maxHeight: Float,
        slotFraction: Float = 1f,
        unit: Int = Sprites.FIGHTER_SIZE,
    ): Float {
        val slot = unit.coerceAtLeast(1)
        val span = 1f + (1f - OVERLAP) * (members - 1).coerceAtLeast(0)
        val byWidth = maxWidth / (slot * slotFraction.coerceIn(0.1f, 1f) * span)
        val byHeight = maxHeight / slot
        return minOf(byWidth, byHeight).toInt().coerceAtLeast(1).toFloat()
    }

    /**
     * Where the fight stands in a room running from [left] to [right].
     *
     * The party and the monster are placed as one group, centred: pinning the
     * party to one wall and the monster to the other reads as a fight on a 4x1
     * bar and as two separate scenes on a 5x2, because the gap grows with the
     * widget. A fixed gap keeps the same duel at every size, and any extra room
     * becomes wall on both sides — which is what a room is.
     */
    fun scene(
        members: Int,
        slot: Float,
        enemyWidth: Float,
        left: Float,
        right: Float,
    ): Scene {
        val party = width(members, slot)
        val gap = slot * 1.6f
        val span = party + if (enemyWidth > 0f) gap + enemyWidth else 0f
        // Centre what fits; a scene wider than the room starts at the left wall,
        // and the monster is then held inside the right one.
        val start = maxOf(left, (left + right) / 2f - span / 2f)
        val enemyCenter = minOf(start + party + gap, right - enemyWidth) + enemyWidth / 2f
        return Scene(partyLeft = start, enemyCenter = enemyCenter)
    }

    data class Scene(val partyLeft: Float, val enemyCenter: Float)

    /**
     * Left edge of member [index], counting the front hero as 0. Later members
     * stand further from the monster, so the party reads back-to-front.
     */
    fun offset(index: Int, members: Int, side: Float): Float = step(side) * (members - 1 - index)

    /** Draw order: rearmost first, so the front hero overlaps the rest. */
    fun drawOrder(members: Int): IntProgression = (members - 1) downTo 0
}
