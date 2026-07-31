package dev.taskbarhero.paint

import dev.taskbarhero.assets.Art
import dev.taskbarhero.game.Balance
import dev.taskbarhero.game.GameState
import dev.taskbarhero.game.Hud
import dev.taskbarhero.game.Item
import dev.taskbarhero.game.Loot
import dev.taskbarhero.game.Rune
import dev.taskbarhero.game.Slot
import dev.taskbarhero.game.Ticker
import kotlin.math.roundToInt

/** What the full screen is showing. The bar only ever shows the field. */
enum class Screen(val title: String, val icon: String) {
    FIELD("FIGHT", Art.SWORD),
    HERO("HERO", Art.CHEST),
    RUNES("RUNES", Art.GEM),
    PORTAL("PORTAL", Art.DOOR),
}

/**
 * The full screen: a titled panel, whatever that screen shows, and a row of round
 * buttons along the bottom to move between them.
 *
 * The nav row is the same shape on every screen and is drawn by this file alone,
 * so a screen can never disagree with it about where a tap lands.
 */
object ScreenLayout {

    /** Height of the nav row, in dp. A touch target does not scale. */
    const val NAV_DP = 64f

    fun navHeight(density: Float): Float = NAV_DP * density

    /** Which screen a tap at [x] on the nav row selects. */
    fun screenAt(x: Float, widthPx: Float): Screen {
        val share = widthPx / Screen.entries.size
        return Screen.entries[(x / share).toInt().coerceIn(0, Screen.entries.lastIndex)]
    }

    fun draw(
        p: Painter,
        screen: Screen,
        widthPx: Float,
        heightPx: Float,
        density: Float,
        state: GameState,
        nowMs: Long,
        recent: Ticker?,
        b: Balance = Balance(),
    ) {
        val nav = navHeight(density)
        val text = BarLayout.textSize(density)

        p.panel(Art.PANEL, 0f, 0f, widthPx, heightPx, corner = 14f * density)

        val bodyBottom = heightPx - nav
        when (screen) {
            Screen.FIELD -> BarLayout.draw(p, widthPx, bodyBottom, density, state, nowMs, recent, b)
            Screen.HERO -> drawHero(p, state, widthPx, bodyBottom, density, text, b)
            Screen.RUNES -> drawRunes(p, state, widthPx, bodyBottom, density, text)
            Screen.PORTAL -> notYet(p, "PORTAL", widthPx, bodyBottom, text)
        }
        drawNav(p, screen, widthPx, heightPx - nav, heightPx, density, text)
    }

    /**
     * The party sheet: one row per hero, their five slots beside them, and the pile
     * underneath.
     *
     * Gear is worn automatically, so this screen is not a place to fiddle — it is
     * the answer to "why did that get stronger", which an idle game owes the player
     * precisely because they were not watching when it happened.
     */
    private fun drawHero(
        p: Painter,
        state: GameState,
        widthPx: Float,
        bottom: Float,
        density: Float,
        text: Float,
        b: Balance,
    ) {
        val hud = Hud.of(state, b)
        val margin = 16f * density
        val line = p.surface.lineHeight(text)
        val cell = 26f * density
        var y = margin

        p.textCentre(widthPx / 2f, y, "PARTY", text, Palette.PARCHMENT)
        y += line + 6f * density

        for ((index, hero) in hud.party.withIndex()) {
            if (y + cell * 2f > bottom) break
            val portrait = p.scaleToFit(hero.cls.sprite, cell * 1.4f)
            p.sprite(hero.cls.sprite, margin + cell / 2f, y + cell * 1.4f, portrait)

            val nameX = margin + cell * 1.4f
            p.text(nameX, y, hero.cls.label, text, if (hero.down) Palette.HP else Palette.PARCHMENT)
            p.text(nameX, y + line, hero.level, text * 0.75f, Palette.PARCHMENT_DIM)

            // The five slots, in the order they are worn, with the empty ones shown
            // as empty: a hole is information, and hiding it hides the next upgrade.
            var x = widthPx - margin - Slot.entries.size * cell
            for (slot in Slot.entries) {
                val item = state.loadout.worn.getOrNull(index)?.get(slot)
                itemCell(p, x, y, cell, item, density)
                x += cell
            }
            y += cell * 1.7f
        }

        y += 6f * density
        p.text(margin, y, "GEAR ${hud.gearBonus}", text * 0.75f, Palette.MAGIC)
        y += line

        val pile = state.loadout.stash
        p.text(margin, y, "STASH ${pile.size}", text * 0.75f, Palette.PARCHMENT_DIM)
        y += line + 4f * density

        // The pile, newest last, in as many rows as there is room for.
        val perRow = ((widthPx - margin * 2f) / cell).toInt().coerceAtLeast(1)
        for ((i, item) in pile.withIndex()) {
            val row = i / perRow
            val top = y + row * cell
            if (top + cell > bottom) break
            itemCell(p, margin + (i % perRow) * cell, top, cell, item, density)
        }
    }

    /**
     * One inventory cell: a sunken socket, and the grade's colour as its border.
     *
     * Border-is-rarity is the convention every ARPG leans on, and it is why a
     * glance at a full pile tells you whether it is worth anything.
     */
    private fun itemCell(p: Painter, x: Float, y: Float, size: Float, item: Item?, density: Float) {
        val pad = 2f * density
        p.panel(Art.PANEL_INSET, x + pad, y + pad, size - pad * 2f, size - pad * 2f, corner = 6f * density)
        if (item == null) return

        val colour = Palette.grade(item.grade)
        p.fill(x + pad, y + pad, size - pad * 2f, 2f, colour)
        p.fill(x + pad, y + size - pad - 2f, size - pad * 2f, 2f, colour)
        p.fill(x + pad, y + pad, 2f, size - pad * 2f, colour)
        p.fill(x + size - pad - 2f, y + pad, 2f, size - pad * 2f, colour)

        val scale = p.scaleToFit(item.slot.sprite, size - pad * 4f)
        p.sprite(item.slot.sprite, x + size / 2f, y + size - pad * 2f, scale)
    }

    /**
     * The rune tree: one row per rune, its rank as pips, and what the next one
     * costs.
     *
     * A rune is bought with bosses, never with gold, so this screen is the only
     * place in the game where the currency is progress itself. The row is dimmed
     * when it cannot be afforded — a tree that lets you tap a node you cannot buy
     * teaches nothing.
     */
    private fun drawRunes(
        p: Painter,
        state: GameState,
        widthPx: Float,
        bottom: Float,
        density: Float,
        text: Float,
    ) {
        val margin = 16f * density
        val line = p.surface.lineHeight(text)
        val icon = 40f * density
        var y = margin

        p.textCentre(widthPx / 2f, y, "RUNES", text, Palette.PARCHMENT)
        y += line + 2f * density
        p.textCentre(
            widthPx / 2f, y,
            "${state.runes.available} TO SPEND — ONE PER BOSS", text * 0.75f, Palette.MAGIC,
        )
        y += line + 8f * density

        for (rune in Rune.entries) {
            if (y + icon > bottom) break
            val rank = state.runes.rank(rune)
            val maxed = rank >= rune.ranks
            val affordable = state.runes.canBuy(rune)

            p.button(margin, y, widthPx - margin * 2f, icon, affordable, corner = 10f * density)
            p.icon(rune.icon, margin + 6f * density, y + 4f * density, icon - 8f * density)

            val textX = margin + icon + 6f * density
            val colour = if (affordable || maxed) Palette.PARCHMENT else Palette.PARCHMENT_DIM
            p.text(textX, y + 4f * density, rune.label, text, colour)
            p.text(textX, y + 4f * density + line * 0.85f, rune.note, text * 0.7f, Palette.PARCHMENT_DIM)

            // Rank as pips rather than "3/6": you can count five dots at a glance
            // and you cannot read a fraction at a glance.
            val pip = 8f * density
            var px = widthPx - margin - 8f * density - rune.ranks * pip
            for (i in 0 until rune.ranks) {
                p.fill(px + 1f, y + icon / 2f - pip / 2f, pip - 2f, pip - 2f,
                    if (i < rank) Palette.GOLD else Palette.EMPTY)
                px += pip
            }
            p.textRight(
                widthPx - margin - 8f * density, y + icon - line * 0.8f,
                if (maxed) "MAX" else "${rune.costOf(rank)}",
                text * 0.7f,
                if (maxed) Palette.GOLD else colour,
            )
            y += icon + 6f * density
        }
    }

    /** Which rune a tap at [y] on the rune screen lands on, if any. */
    fun runeAt(y: Float, density: Float): Rune? {
        val margin = 16f * density
        val line = 16f * density * 1.8f
        val icon = 40f * density
        val first = margin + line * 2f + 10f * density
        if (y < first) return null
        val index = ((y - first) / (icon + 6f * density)).toInt()
        return Rune.entries.getOrNull(index)
    }

    /** A screen that exists in the nav but not yet in the game. Says so plainly. */
    private fun notYet(p: Painter, name: String, widthPx: Float, bottom: Float, text: Float) {
        p.textCentre(widthPx / 2f, bottom / 2f - text, name, text, Palette.PARCHMENT)
        p.textCentre(widthPx / 2f, bottom / 2f + text, "NOT BUILT YET", text * 0.75f, Palette.PARCHMENT_DIM)
    }

    /** The row of round buttons. Lit is where you are. */
    private fun drawNav(
        p: Painter,
        current: Screen,
        widthPx: Float,
        top: Float,
        bottom: Float,
        density: Float,
        text: Float,
    ) {
        val share = widthPx / Screen.entries.size
        val h = bottom - top
        for ((i, screen) in Screen.entries.withIndex()) {
            val x = i * share
            val here = screen == current
            p.button(x + 3f, top + 2f, share - 6f, h - 4f, here, corner = 10f * density)
            val icon = p.scaleToFit(screen.icon, h * 0.42f)
            p.sprite(screen.icon, x + share / 2f, top + h * 0.58f, icon)
            p.textCentre(
                x + share / 2f, bottom - p.surface.lineHeight(text * 0.6f) - 2f * density,
                screen.title, text * 0.6f,
                if (here) Palette.PARCHMENT else Palette.PARCHMENT_DIM,
            )
        }
    }

    @Suppress("unused")
    private val gradeNames = Loot.GRADES

    @Suppress("unused")
    private fun unusedRound(v: Float) = v.roundToInt()
}
