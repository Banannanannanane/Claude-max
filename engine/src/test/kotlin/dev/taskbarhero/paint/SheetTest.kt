package dev.taskbarhero.paint

import dev.taskbarhero.engine.GameState
import dev.taskbarhero.engine.SpriteKey
import dev.taskbarhero.engine.Sprites
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A surface that answers blits, so the fallback path can be told from the real one. */
private class BlitRecorder(private val accept: Boolean) : Surface {
    val blits = mutableListOf<Sheet.Frame>()
    var rects = 0

    override fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        rects++
    }

    override fun circle(cx: Float, cy: Float, radius: Float, color: Int) = Unit

    override fun image(
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): Boolean {
        if (!accept) return false
        blits += Sheet.Frame(srcX, srcY, srcWidth, srcHeight)
        return true
    }
}

class SheetTest {

    @Test
    fun `the mapping reads names and rectangles, comments and all`() {
        val result = SheetMapping.parse(
            """
            # name = x, y, width, height
            KNIGHT = 16, 0, 16, 16

            boss   =  0, 64, 32, 36   # lowercase names are fine
            """.trimIndent(),
        )
        assertTrue(result.problems.isEmpty(), "clean mapping reported ${result.problems}")
        assertEquals(Sheet.Frame(16, 0, 16, 16), result.sheet.frame("KNIGHT"))
        assertEquals(Sheet.Frame(0, 64, 32, 36), result.sheet.frame("BOSS"), "names are normalised")
        assertNull(result.sheet.frame("MAGE"), "an unmapped name falls back")
    }

    @Test
    fun `a bad line costs its own sprite, not the sheet`() {
        val result = SheetMapping.parse(
            """
            KNIGHT = 16, 0, 16, 16
            RANGER = oops
            MAGE = 0, 0, 0, 16
            GOLEM = 1, 2, 3
            """.trimIndent(),
        )
        assertEquals(1, result.sheet.size, "only the good line should map")
        assertEquals(3, result.problems.size, "each bad line should be reported")
        assertTrue(result.isUsable)
        assertTrue(result.problems.all { it.startsWith("line ") }, "problems should point at a line")
    }

    @Test
    fun `an empty mapping is not usable, and says so quietly`() {
        val result = SheetMapping.parse("# nothing here\n\n")
        assertEquals(0, result.sheet.size)
        assertTrue(result.problems.isEmpty())
        assertTrue(!result.isUsable)
    }

    @Test
    fun `a mapped fighter is blitted, an unmapped one falls back to the built-in art`() {
        val sheet = SheetMapping.parse("KNIGHT = 16, 0, 16, 16").sheet
        val surface = BlitRecorder(accept = true)
        val p = PixelPainter(surface, unit = 1f, sheet = sheet)

        p.fighter(0f, 0f, 32f, SpriteKey.KNIGHT.name, Sprites.of(SpriteKey.KNIGHT))
        assertEquals(listOf(Sheet.Frame(16, 0, 16, 16)), surface.blits)
        assertEquals(0, surface.rects, "a blitted fighter must not also draw its built-in pixels")

        p.fighter(0f, 0f, 32f, SpriteKey.MAGE.name, Sprites.of(SpriteKey.MAGE))
        assertEquals(1, surface.blits.size, "the mage is not in the sheet")
        assertTrue(surface.rects > 0, "so it should have been drawn from the built-in art")
    }

    @Test
    fun `a surface that refuses the blit still gets a fighter`() {
        // A frame pointing off the sheet, or a device that has not loaded the image
        // yet, returns false — and the caller must not leave a hole in the room.
        val sheet = SheetMapping.parse("KNIGHT = 999, 999, 16, 16").sheet
        val surface = BlitRecorder(accept = false)
        PixelPainter(surface, unit = 1f, sheet = sheet)
            .fighter(0f, 0f, 32f, SpriteKey.KNIGHT.name, Sprites.of(SpriteKey.KNIGHT))
        assertTrue(surface.rects > 0, "the built-in knight should have been drawn instead")
    }

    @Test
    fun `sheet frames are fitted into the box and stand on its floor`() {
        // A tall 16x24 frame in a 32-cell box: scaled by the taller edge, centred
        // across, and bottom-aligned so it shares the floor with everyone else.
        val sheet = SheetMapping.parse("BOSS = 0, 0, 16, 24").sheet
        var bounds: FloatArray? = null
        val surface = object : Surface {
            override fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) = Unit
            override fun circle(cx: Float, cy: Float, radius: Float, color: Int) = Unit
            override fun image(
                srcX: Int,
                srcY: Int,
                srcWidth: Int,
                srcHeight: Int,
                left: Float,
                top: Float,
                right: Float,
                bottom: Float,
            ): Boolean {
                bounds = floatArrayOf(left, top, right, bottom)
                return true
            }
        }
        PixelPainter(surface, unit = 1f, sheet = sheet)
            .fighter(x = 0f, y = 0f, side = 48f, key = "BOSS", art = Sprites.of(SpriteKey.BOSS))

        val (left, top, right, bottom) = bounds!!.let { arrayOf(it[0], it[1], it[2], it[3]) }
        assertEquals(48f, bottom, "the frame must sit on the bottom of its box")
        assertEquals(32f, right - left, "16x24 fitted by height into 48 gives 32 wide")
        assertEquals(48f, bottom - top)
        assertEquals(8f, left, "and be centred across the box")
    }

    @Test
    fun `frames are scaled by whole pixels, even when that wastes room`() {
        // 16x28 in a 32-cell box would "fit" at 1.14x — which duplicates every
        // seventh row and nothing else. 1x, leaving four cells unused, is the only
        // scale that keeps the artwork intact.
        assertEquals(floatArrayOf(8f, 4f, 24f, 32f).toList(), blitOf("16, 0, 16, 28", side = 32f))

        // A frame taller than its box overhangs it rather than being shrunk below
        // its own resolution: that is how an ogre reads as an ogre.
        assertEquals(floatArrayOf(0f, -4f, 32f, 32f).toList(), blitOf("0, 0, 32, 36", side = 32f))

        // Room for two whole pixels per source pixel, so: two.
        assertEquals(floatArrayOf(0f, 8f, 64f, 64f).toList(), blitOf("0, 0, 32, 28", side = 64f))
    }

    /** The device-pixel rectangle a one-frame sheet is blitted into. */
    private fun blitOf(rect: String, side: Float): List<Float> {
        var bounds = floatArrayOf()
        val surface = object : Surface {
            override fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) = Unit
            override fun circle(cx: Float, cy: Float, radius: Float, color: Int) = Unit
            override fun image(
                srcX: Int,
                srcY: Int,
                srcWidth: Int,
                srcHeight: Int,
                left: Float,
                top: Float,
                right: Float,
                bottom: Float,
            ): Boolean {
                bounds = floatArrayOf(left, top, right, bottom)
                return true
            }
        }
        PixelPainter(surface, unit = 1f, sheet = SheetMapping.parse("BOSS = $rect").sheet)
            .fighter(x = 0f, y = 0f, side = side, key = "BOSS", art = Sprites.of(SpriteKey.BOSS))
        return bounds.toList()
    }

    @Test
    fun `every sprite the game asks for has a name a mapping can use`() {
        // The keys the layouts pass are enum names, so a player writing a mapping
        // has something stable to type.
        val hud = dev.taskbarhero.engine.Hud.of(GameState.newRun(0L))
        assertTrue(SpriteKey.entries.map { it.name }.contains(hud.enemySprite.name))
        for (key in SpriteKey.entries) {
            assertTrue(key.name.all { it.isUpperCase() || it == '_' }, "${key.name} is awkward to type")
        }
    }
}
