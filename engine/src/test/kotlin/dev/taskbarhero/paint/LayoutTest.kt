package dev.taskbarhero.paint

import dev.taskbarhero.engine.Balance
import dev.taskbarhero.engine.GameState
import dev.taskbarhero.engine.IdleEngine
import dev.taskbarhero.engine.Loot
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

    /** Sets every hero to the same level, health topped up for the new pool. */
    private fun GameState.at(level: Int): GameState =
        copy(party = party.map { it.copy(level = level, hp = b.heroMaxHp(it.cls, level)) })

    private fun states(): Map<String, GameState> {
        val fresh = GameState.newRun(t0, b)
        return mapOf(
            "fresh" to fresh,
            "rich" to fresh.copy(gold = 9_999.0),
            "boss" to fresh.copy(act = 3, wave = 10, gold = 4_200.0, unlocked = 2).at(24),
            "down" to fresh.copy(downUntilMs = t0 + 2_000L, act = 5, wave = 3, unlocked = 3),
            "auto" to fresh.copy(autoLevel = true, act = 2, wave = 6, unlocked = 2).at(12),
            // Deep-run digits are the widest text the bar ever has to fit.
            "deep" to fresh.copy(
                act = 12, wave = 9, runes = 48, gold = 1.4e12, unlocked = 3,
                kills = 986_400L, bossKills = 71L, deaths = 34L, deepestAct = 12, deepestWave = 10,
                stash = List(Loot.GRADES.size) { 9 },
            ).at(240),
            "idled" to IdleEngine.advance(fresh.copy(autoLevel = true), t0 + 1_800_000L, b).state,
        )
    }

    @Test
    fun `bar layout stays inside its bounds at every size`() {
        for ((cols, rows) in barSizes) {
            for ((name, state) in states()) {
                val surface = RecordingSurface()
                // unit = 1 makes art cells and surface units the same, so the
                // assertions read directly in pixels.
                val deck = if (rows >= BarLayout.ROWS_TALL) 22 else 0
                BarLayout.draw(PixelPainter(surface, 1f), cols, rows, state, t0, null, b, deck)

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
            val deck = if (rows >= BarLayout.ROWS_TALL) 22 else 0
            BarLayout.draw(PixelPainter(surface, 1f), cols, rows, GameState.newRun(t0, b), t0, caption, b, deck)
            val captionOps = surface.content().filter { it.top < 9f && it.color == Palette.grade(7) }
            assertTrue(captionOps.isNotEmpty(), "caption missing at ${cols}x$rows")
            // On a one-row bar the side button shares that row, and a caption running
            // under it would read as its label. A tall bar has the width to itself.
            val limit = if (deck > 0) cols.toFloat() else cols * (1f - BarLayout.ACTION_ZONE_FRACTION)
            assertTrue(
                captionOps.all { it.right <= limit + 0.5f },
                "caption ran past its room at ${cols}x$rows",
            )
        }
    }

    @Test
    fun `the action button lights up exactly when the level is affordable`() {
        val cols = 107
        val poor = GameState.newRun(t0, b)
        val rich = poor.copy(gold = poor.levelCost(b))

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
    fun `each biome is distinct and stays darker than the ink on it`() {
        val walls = Biome.ALL.map { it.stone }
        assertEquals(walls.size, walls.toSet().size, "two biomes share a wall colour")

        for (biome in Biome.ALL) {
            // Text and sprites are drawn light on these; a pale wall would erase them.
            assertTrue(luma(biome.stone) < luma(Palette.PARCHMENT) * 0.45f, "${biome.label} wall is too bright")
            assertTrue(luma(biome.stoneDark) < luma(biome.stone), "${biome.label} mortar must be darker than its stone")
            assertTrue(luma(biome.stoneLit) > luma(biome.stone), "${biome.label} lit lip must be lighter than its stone")
        }
    }

    @Test
    fun `biomes cycle with the acts and never run out`() {
        assertEquals(Biome.DUNGEON, Biome.of(1), "act 1 opens in the dungeon")
        assertEquals(Biome.of(1), Biome.of(2), "a biome lasts more than one act")
        assertTrue(Biome.of(1) != Biome.of(4), "act 4 should look different from act 1")
        // Deep runs must not fall off the end of the list.
        for (act in 1..200) assertTrue(Biome.of(act) in Biome.ALL)
    }

    private fun luma(color: Int): Float {
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    @Test
    fun `the deck split matches the layout weights`() {
        assertEquals(4, BarLayout.DECK_SPLIT.size)
        assertEquals(1f, BarLayout.DECK_SPLIT.sum(), 1e-6f)
        assertTrue(BarLayout.DECK_SPLIT.all { it >= 0.2f }, "every button needs a thumb-sized share")
    }

    @Test
    fun `the deck keeps its dp height whatever the widget height`() {
        val density = 3f
        val unit = BarLayout.unitPx(BarLayout.ROW_HEIGHT_DP * density, density)
        val rows = BarLayout.deckRows(unit, density)
        assertEquals(rows, BarLayout.deckRows(BarLayout.unitPx(400f * density, density), density))
        // 48dp at a 70/32 dp pitch: about 22 art rows, and always a real touch target.
        assertTrue(rows in 18..26, "deck is $rows rows")
    }

    @Test
    fun `a tall bar draws its three buttons inside the deck`() {
        val cols = 126
        val rows = 67
        val deck = 22
        val surface = RecordingSurface()
        val state = states().getValue("rich")
        BarLayout.draw(PixelPainter(surface, 1f), cols, rows, state, t0, null, b, deck)

        val deckTop = rows - deck
        // Each third of the deck must carry ink of its own: three real buttons.
        var x = 2f
        for (share in BarLayout.DECK_SPLIT) {
            val width = (cols - 4f) * share
            val painted = surface.content().count {
                it.top >= deckTop - 1f && it.left >= x - 1f && it.right <= x + width + 1f
            }
            assertTrue(painted > 0, "no button drawn in the deck slice starting at $x")
            x += width
        }
    }

    @Test
    fun `the deck only appears once the bar is tall enough`() {
        fun deckOps(rows: Int, deck: Int): Int {
            val surface = RecordingSurface()
            BarLayout.draw(PixelPainter(surface, 1f), 126, rows, states().getValue("rich"), t0, null, b, deck)
            return surface.content().count { it.top >= rows - deck }
        }
        assertTrue(deckOps(67, 22) > 0)
        // A one-row bar has no deck rows to give, and must not pretend otherwise.
        val compact = RecordingSurface()
        BarLayout.draw(PixelPainter(compact, 1f), 126, 32, states().getValue("rich"), t0, null, b, 22)
        assertTrue(compact.content().all { it.bottom <= 32.5f })
    }

    @Test
    fun `the tall bar draws more than the compact one`() {
        val state = states().getValue("idled")
        fun opCount(rows: Int, deck: Int): Int {
            val surface = RecordingSurface()
            BarLayout.draw(PixelPainter(surface, 1f), 107, rows, state, t0, null, b, deck)
            return surface.content().size
        }
        assertTrue(opCount(67, 22) > opCount(BarLayout.ROWS_COMPACT, 0))
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
