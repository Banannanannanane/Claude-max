package dev.dotmatrix.taskbarhero.paint

import dev.dotmatrix.taskbarhero.engine.Balance
import dev.dotmatrix.taskbarhero.engine.GameState
import dev.dotmatrix.taskbarhero.engine.IdleEngine
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

    override fun circle(cx: Float, cy: Float, radius: Float, color: Int) {
        ops += Op(cx - radius, cy - radius, cx + radius, cy + radius, color)
    }

    override fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        ops += Op(left, top, right, bottom, color)
    }

    override fun roundRectFill(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
        ops += Op(left, top, right, bottom, color)
    }

    override fun roundRectStroke(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        strokeWidth: Float,
        color: Int,
    ) {
        val h = strokeWidth / 2f
        ops += Op(left - h, top - h, right + h, bottom + h, color)
    }

    /** Ignores the background panel and grid, which are meant to fill the bar. */
    fun content(): List<Op> = ops.drop(1).filter { it.color != Palette.GRID }
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
                BarLayout.draw(DotPainter(surface, 1f), cols, rows, state, t0, recentEvent = null, b = b)

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
        val caption = "GLYPH EATER DOWN / IMMORTAL TASKBAR TOTEM"
        for ((cols, rows) in barSizes) {
            val surface = RecordingSurface()
            BarLayout.draw(DotPainter(surface, 1f), cols, rows, GameState.newRun(t0, b), t0, caption, b)
            val actionLeft = cols * (1f - BarLayout.ACTION_ZONE_FRACTION)
            val captionOps = surface.content().filter { it.top < 8f && it.color == Palette.RED }
            assertTrue(captionOps.isNotEmpty(), "caption missing at ${cols}x$rows")
            // The caption must not reach under the button, or it would look like a label for it.
            assertTrue(
                captionOps.all { it.right <= actionLeft + 0.5f },
                "caption ran into the action zone at ${cols}x$rows",
            )
        }
    }

    @Test
    fun `the action button turns red exactly when the level is affordable`() {
        val cols = 107
        val poor = GameState.newRun(t0, b)
        val rich = poor.copy(gold = b.levelCost(poor.level))

        fun redOpsInButton(state: GameState): Int {
            val surface = RecordingSurface()
            BarLayout.draw(DotPainter(surface, 1f), cols, 30, state, t0, null, b)
            val actionLeft = cols * (1f - BarLayout.ACTION_ZONE_FRACTION)
            return surface.content().count { it.color == Palette.RED && it.left >= actionLeft }
        }

        assertEquals(0, redOpsInButton(poor), "a broke hero should not get a red button")
        assertTrue(redOpsInButton(rich) > 0, "an affordable level should light the button up")
    }

    @Test
    fun `the tall bar draws more than the compact one`() {
        val state = states().getValue("idled")
        fun opCount(rows: Int): Int {
            val surface = RecordingSurface()
            BarLayout.draw(DotPainter(surface, 1f), 107, rows, state, t0, null, b)
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
        BarLayout.draw(DotPainter(surface, 1f), 40, 30, states().getValue("deep"), t0, null, b)
        assertTrue(surface.content().all { it.right <= 40.5f })
    }

    @Test
    fun `dungeon layout stays inside its bounds`() {
        val log = listOf(
            "BOSS 4 DOWN / GLYPH CUBE SHARD", "LEVEL 61", "RUNE 12 +24%", "WIPED AT 5-08", "NEW RUN",
        )
        for (rows in listOf(90, 120, 74)) {
            for ((name, state) in states()) {
                val surface = RecordingSurface()
                val cols = DungeonLayout.TARGET_COLS
                DungeonLayout.draw(DotPainter(surface, 1f), cols, rows, state, log, t0, b)
                for (op in surface.ops.filter { it.color != Palette.GRID }) {
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
            DotPainter(surface, 1f),
            DungeonLayout.TARGET_COLS,
            72,
            GameState.newRun(t0, b),
            listOf("LEVEL 2", "LEVEL 3"),
            t0,
            b,
        )
        assertTrue(surface.ops.all { it.bottom <= 72.5f })
    }

    @Test
    fun `meters keep one dot honest at the extremes`() {
        val surface = RecordingSurface()
        val p = DotPainter(surface, 1f)
        p.meter(0f, 0f, cells = 10, fraction = 0.001, lit = Palette.RED)
        assertEquals(1, surface.ops.count { it.color == Palette.RED }, "a sliver of health must still show")

        surface.ops.clear()
        p.meter(0f, 0f, cells = 10, fraction = 0.999, lit = Palette.RED)
        assertEquals(9, surface.ops.count { it.color == Palette.RED }, "not-quite-full must not read as full")

        surface.ops.clear()
        p.meter(0f, 0f, cells = 10, fraction = 1.0, lit = Palette.RED)
        assertEquals(10, surface.ops.count { it.color == Palette.RED })
    }
}
