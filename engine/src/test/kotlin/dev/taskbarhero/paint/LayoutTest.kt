package dev.taskbarhero.paint

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutTest {

    @Test
    fun `the painted deck and the tappable deck are the same deck`() {
        // BarLayout paints four buttons across these fractions; widget_tall.xml
        // declares four touch zones with the matching weights. Nothing at runtime
        // can notice they have drifted apart — the button simply stops being where
        // it looks, which is why this is asserted here.
        assertEquals(4, BarLayout.DECK_SPLIT.size)
        assertTrue(
            abs(BarLayout.DECK_SPLIT.sum() - 1f) < 0.0001f,
            "the deck covers ${BarLayout.DECK_SPLIT.sum()} of the width",
        )
        assertEquals(listOf(0.28f, 0.24f, 0.24f, 0.24f), BarLayout.DECK_SPLIT.toList())
    }

    @Test
    fun `a widget only grows a deck once there is room for one`() {
        val density = 3f
        val deck = BarLayout.deckHeight(density)
        assertTrue(BarLayout.hasDeck(BarLayout.TALL_DP * density, density))
        assertTrue(!BarLayout.hasDeck(BarLayout.TALL_DP * density - 1f, density))
        assertTrue(deck < BarLayout.TALL_DP * density, "the deck would fill the whole widget")
    }

    @Test
    fun `text is always a whole multiple of the font's own size`() {
        // m5x7 is a pixel font: at 16px a capital is 7px tall, at 32px 14px. Any
        // size in between lands glyphs on half pixels and undoes the whole look.
        for (density in listOf(1f, 1.5f, 2f, 2.625f, 3f, 3.5f, 4f)) {
            val size = BarLayout.textSize(density)
            assertEquals(0f, size % 16f, "density $density asks for ${size}px text")
            assertTrue(size >= 16f)
        }
    }
}
