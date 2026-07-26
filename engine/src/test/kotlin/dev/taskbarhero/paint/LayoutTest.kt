package dev.taskbarhero.paint

import dev.taskbarhero.engine.Balance
import dev.taskbarhero.engine.GameState
import dev.taskbarhero.engine.IdleEngine
import dev.taskbarhero.engine.Ticker
import dev.taskbarhero.engine.Tone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Records every primitive the layouts emit and checks it lands inside the bar.
 * Overflowing text is the failure mode a widget cannot show you — the launcher
 * just clips it — so it is worth asserting instead of eyeballing.
 */
private class RecordingSurface : Surface {
    data class Op(val left: Float, val top: Float, val right: Float, val bottom: Float, val color: Int)

    val ops = mutableListOf<Op>()

    override fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        ops += Op(left, top, right, bottom, color)
    }

    override fun circle(cx: Float, cy: Float, radius: Float, color: Int) {
        ops += Op(cx - radius, cy - radius, cx + radius, cy + radius, color)
    }

    /** Skips the backdrop — the panel, brickwork and floor are meant to fill the bar. */
    fun content(): List<Op> = ops.filterNot { it.color in BACKDROP }

    private companion object {
        val BACKDROP = setOf(
            Palette.VOID, Palette.STONE, Palette.STONE_DARK, Palette.STONE_LIT,
            Palette.FLOOR, Palette.BEVEL_LIGHT, Palette.BEVEL_DARK,
        )
    }
}

class LayoutTest {

    private val b = Balance()
    private val t0 = 1_700_000_000_000L

    /**
     * Cell grids a real launcher hands us at 3x density: 4x1, a squeezed 3x1, a
     * stretched 5x1, then the taller variants where the stats strip appears.
     */
    private val barSizes = listOf(126 to 32, 93 to 32, 158 to 32, 126 to 67, 158 to 67, 126 to 46)

    private fun states(): Map<String, GameState> {
        val fresh = GameState.newRun(t0, b)
        return mapOf(
            "fresh" to fresh,
            "rich" to fresh.copy(gold = 9_999.0),
            "boss" to fresh.copy(act = 3, wave = 10, level = 24, gold = 4_200.0),
            "down" to fresh.copy(downUntilMs = t0 + 2_000L, act = 5, wave = 3),
            "auto" to fresh.copy(autoLevel = true, act = 2, wave = 6, level = 12),
            // Deep-run digits are the widest text the bar ever has to fit.
            "deep" to fresh.copy(
                act = 12, wave = 9, level = 240, runes = 48, gold = 1.4e12,
                kills = 986_400L, bossKills = 71L, deaths = 34L, deepestAct = 12, deepestWave = 10,
            ),
            "idled" to IdleEngine.advance(fresh.copy(autoLevel = true), t0 + 1_800_000L, b).state,
        )
    }

    @Test
    fun `bar layout stays inside its bounds at every size`() {
        for ((cols, rows) in barSizes) {
            for ((name, state) in states()) {
                val surface = RecordingSurface()
                // unit = 1 makes dot units and surface units the same, so the
                // assertions read directly in cells.
                BarLayout.draw(PixelPainter(surface, 1f), cols, rows, state, t0, recent = null, b = b)

                val content = surface.content()
                assertTrue(content.isNotEmpty(), "$name at ${cols}x$rows drew nothing")
                for (op in content) {
                    assertTrue(op.left >= -0.5f, "$name at ${cols}x$rows: left overflow ${op.left}")
                    assertTrue(op.top >= -0.5f, "$name at ${cols}x$rows: top overflow ${op.top}")
                    assertTrue(op.right <= cols + 0.5f, "$name at ${cols}x$rows: right overflow ${op.right} > $cols")
                    assertTrue(op.bottom <= rows + 0.5f, "$name at ${cols}x$rows: bottom overflow ${op.bottom} > $rows")
                }
            }
        }
    }

    @Test
    fun `a long event caption never escapes the arena`() {
        val caption = Ticker("BOSS 7 / CELESTIAL TOTEM AND THEN SOME", Tone.LOOT, grade = 7)
        for ((cols, rows) in barSizes) {
            val surface = RecordingSurface()
            BarLayout.draw(PixelPainter(surface, 1f), cols, rows, GameState.newRun(t0, b), t0, caption, b)
            val actionLeft = cols * (1f - BarLayout.ACTION_ZONE_FRACTION)
            val captionOps = surface.content().filter { it.top < 9f && it.color == Palette.grade(7) }
            assertTrue(captionOps.isNotEmpty(), "caption missing at ${cols}x$rows")
            // The caption must not reach under the button, or it would look like a label for it.
            assertTrue(
                captionOps.all { it.right <= actionLeft + 0.5f },
                "caption ran into the action zone at ${cols}x$rows",
            )
        }
    }

    @Test
    fun `the action button lights up exactly when the level is affordable`() {
        val cols = 107
        val poor = GameState.newRun(t0, b)
        val rich = poor.copy(gold = b.levelCost(poor.level))

        fun goldOpsInButton(state: GameState): Int {
            val surface = RecordingSurface()
            BarLayout.draw(PixelPainter(surface, 1f), cols, 30, state, t0, null, b)
            val actionLeft = cols * (1f - BarLayout.ACTION_ZONE_FRACTION)
            return surface.content().count { it.color == Palette.GOLD && it.left >= actionLeft }
        }

        assertEquals(0, goldOpsInButton(poor), "a broke hero must not get a lit button")
        assertTrue(goldOpsInButton(rich) > 0, "an affordable level should light the button up")
    }

    @Test
    fun `the tall bar draws more than the compact one`() {
        val state = states().getValue("idled")
        fun opCount(rows: Int): Int {
            val surface = RecordingSurface()
            BarLayout.draw(PixelPainter(surface, 1f), 107, rows, state, t0, null, b)
            return surface.content().size
        }
        assertTrue(opCount(BarLayout.ROWS_TALL) > opCount(BarLayout.ROWS_COMPACT))
    }

    @Test
    fun `a taller bar gains rows instead of bigger dots`() {
        val density = 3f
        val oneRow = BarLayout.ROW_HEIGHT_DP * density
        assertEquals(BarLayout.ROWS_COMPACT, BarLayout.rowsFor(oneRow, density))

        val pitch = BarLayout.unitPx(oneRow, density)
        val twoRows = 148f * density
        assertEquals(pitch, BarLayout.unitPx(twoRows, density), "the dot pitch must not stretch")
        assertTrue(BarLayout.rowsFor(twoRows, density) >= BarLayout.ROWS_TALL)

        // A bar shorter than one launcher row keeps the layout it was designed for.
        assertEquals(BarLayout.ROWS_COMPACT, BarLayout.rowsFor(40f * density, density))
    }

    @Test
    fun `a bar squeezed past readability degrades instead of overflowing`() {
        val surface = RecordingSurface()
        // 40 cells wide leaves no room for the arena; the layout should drop parts,
        // not spill them.
        BarLayout.draw(PixelPainter(surface, 1f), 40, 30, states().getValue("deep"), t0, null, b)
        assertTrue(surface.content().all { it.right <= 40.5f })
    }

    @Test
    fun `dungeon layout stays inside its bounds`() {
        val log = listOf(
            Ticker("BOSS 4 / LEGENDARY SHARD", Tone.LOOT, grade = 3),
            Ticker("LEVEL 61", Tone.GOOD),
            Ticker("RUNE 12 +24%", Tone.MAGIC),
            Ticker("WIPED AT 5-08", Tone.BAD),
            Ticker("NEW RUN", Tone.NEUTRAL),
        )
        for (rows in listOf(90, 120, 74)) {
            for ((name, state) in states()) {
                val surface = RecordingSurface()
                val cols = DungeonLayout.TARGET_COLS
                DungeonLayout.draw(PixelPainter(surface, 1f), cols, rows, state, log, t0, b)
                for (op in surface.content()) {
                    assertTrue(op.left >= -0.5f, "$name/$rows: left overflow ${op.left}")
                    assertTrue(op.right <= cols + 0.5f, "$name/$rows: right overflow ${op.right}")
                    assertTrue(op.top >= -0.5f, "$name/$rows: top overflow ${op.top}")
                    assertTrue(op.bottom <= rows + 0.5f, "$name/$rows: bottom overflow ${op.bottom}")
                }
            }
        }
    }

    @Test
    fun `a short dungeon drops the log rather than clipping it`() {
        val surface = RecordingSurface()
        DungeonLayout.draw(
            PixelPainter(surface, 1f),
            DungeonLayout.TARGET_COLS,
            72,
            GameState.newRun(t0, b),
            listOf(Ticker("LEVEL 2", Tone.GOOD), Ticker("LEVEL 3", Tone.GOOD)),
            t0,
            b,
        )
        assertTrue(surface.ops.all { it.bottom <= 72.5f })
    }

    @Test
    fun `bars stay honest at the extremes`() {
        val surface = RecordingSurface()
        val p = PixelPainter(surface, 1f)

        p.bar(0f, 0f, 20f, 4f, 0.001, Palette.HP, Palette.HP_SOCKET)
        val sliver = surface.ops.filter { it.color == Palette.HP }
        assertTrue(sliver.isNotEmpty(), "a sliver of health must still show")
        assertTrue(sliver.all { it.right - it.left <= 1f }, "0.1% must not round up to a wide fill")

        surface.ops.clear()
        p.bar(0f, 0f, 20f, 4f, 0.999, Palette.HP, Palette.HP_SOCKET)
        val nearlyFull = surface.ops.first { it.color == Palette.HP }
        assertTrue(nearlyFull.right - nearlyFull.left <= 19f, "not-quite-full must not read as full")

        surface.ops.clear()
        p.bar(0f, 0f, 20f, 4f, 1.0, Palette.HP, Palette.HP_SOCKET)
        val full = surface.ops.first { it.color == Palette.HP }
        assertEquals(20f, full.right - full.left)
    }
}
