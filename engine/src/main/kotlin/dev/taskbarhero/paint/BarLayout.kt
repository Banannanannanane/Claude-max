package dev.taskbarhero.paint

import dev.taskbarhero.assets.Art
import dev.taskbarhero.game.GameState
import dev.taskbarhero.game.Hud
import dev.taskbarhero.game.Ticker
import dev.taskbarhero.game.Tone
import kotlin.math.roundToInt

/**
 * The widget: a dungeon room with the numbers you watch on the left and the
 * buttons you press underneath.
 *
 * Laid out in device pixels rather than an abstract grid, because the deck's
 * painted buttons have to land exactly on the touch zones the RemoteViews layout
 * declares — and those are in dp. Everything else is derived from the room that
 * is left over.
 */
object BarLayout {

    /** Height of the button deck, in dp. Fixed: a touch target does not scale. */
    const val DECK_DP = 52f

    /** Below this the widget is one row and shows a single action. */
    const val TALL_DP = 110f

    /** How the deck's width splits between LV UP / HEAL / CUBE / AUTO. */
    val DECK_SPLIT = floatArrayOf(0.28f, 0.24f, 0.24f, 0.24f)

    /** Width of the single action on a one-row bar, as a fraction. */
    const val ACTION_FRACTION = 0.28f

    /** How long an event holds the room's caption instead of the monster's name. */
    const val EVENT_MS = 3_500L

    private const val PAD = 4f

    /** The panel's frame, in pixels. Content starts inside it. */
    private fun corner(density: Float): Float = 12f * density

    fun deckHeight(density: Float): Float = DECK_DP * density

    fun hasDeck(heightPx: Float, density: Float): Boolean = heightPx >= TALL_DP * density

    fun draw(
        p: Painter,
        widthPx: Float,
        heightPx: Float,
        density: Float,
        state: GameState,
        nowMs: Long,
        recent: Ticker? = null,
        b: dev.taskbarhero.game.Balance = dev.taskbarhero.game.Balance(),
    ) {
        val hud = Hud.of(state, b, nowMs)
        val text = textSize(density)

        p.fill(0f, 0f, widthPx, heightPx, Palette.VOID)
        p.panel(Art.PANEL, 0f, 0f, widthPx, heightPx, corner = corner(density))

        val deck = if (hasDeck(heightPx, density)) deckHeight(density) else 0f
        // Inside the panel's own frame, not on top of it: the nine-slice corner is
        // the widest the border ever gets, and text under it is text half eaten.
        val inset = corner(density) + PAD * density
        val bottom = heightPx - (if (deck > 0f) deck else inset)

        val statsRight = statusWidth(p, hud, text) + inset
        drawStatus(p, hud, inset, inset, statsRight, bottom, text)

        val roomLeft = statsRight + 4f * density
        val roomRight = if (deck > 0f) widthPx - inset else widthPx * (1f - ACTION_FRACTION) - 2f * density
        drawRoom(p, hud, roomLeft, inset, roomRight, bottom, nowMs, recent, text, density)

        if (deck > 0f) {
            drawDeck(p, hud, inset, heightPx - deck, widthPx - inset, heightPx - inset, text, density)
        } else {
            drawAction(p, hud, roomRight + 2f * density, inset, widthPx - inset, bottom, text, density)
        }
    }

    /** Text is drawn at whole multiples of the font's design size, or it stops being pixel art. */
    fun textSize(density: Float): Float = (16f * maxOf(1, (density / 1.6f).roundToInt()))

    private fun statusWidth(p: Painter, hud: Hud, text: Float): Float = maxOf(
        p.surface.measure(hud.stage, text),
        p.surface.measure(hud.level, text),
        p.surface.measure(hud.gold, text) + text,
    ) + text / 2f

    /** Left column: where we are, how strong we are, what we can spend. */
    private fun drawStatus(p: Painter, hud: Hud, x: Float, y: Float, right: Float, bottom: Float, text: Float) {
        val line = p.surface.lineHeight(text)
        p.text(x, y, hud.stage, text, Palette.PARCHMENT)
        p.text(x, y + line, hud.level, text, Palette.PARCHMENT_DIM)

        // The coin is sized to the line it sits on, not to a guess: an icon that
        // overruns its row eats the row above it.
        val coinY = y + line * 2f
        val coinScale = p.scaleToFit(Art.COIN, line * 0.8f)
        val coinW = p.spriteWidth(Art.COIN, coinScale)
        p.sprite(Art.COIN, x + coinW / 2f, coinY + line * 0.9f, coinScale)
        p.text(x + coinW + 4f, coinY, hud.gold, text, Palette.GOLD)

        p.fill(right - 2f, y, 2f, bottom - y, Palette.INK)
    }

    /** The room: wall, floor, torches, the duel, and a meter under each side. */
    private fun drawRoom(
        p: Painter,
        hud: Hud,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        nowMs: Long,
        recent: Ticker?,
        text: Float,
        density: Float,
    ) {
        val w = right - left
        val h = bottom - top
        if (w < 60f || h < 40f) return

        val meterH = maxOf(6f, 5f * density)
        val meterY = bottom - meterH
        val floorY = meterY - 4f * density
        val captionH = p.surface.lineHeight(text)

        // Room first, everything else on top of it.
        val tileScale = maxOf(1, (h / 96f).roundToInt())
        p.tile(Art.WALL, left, top, w, floorY - top, tileScale)
        p.tile(Art.FLOOR, left, floorY, w, bottom - floorY, tileScale)

        // Fighters get whatever height is left between the caption and the floor.
        val band = floorY - (top + captionH + 2f * density)
        val scale = p.scaleFor(band)

        // Torches on the side walls: never above a head, and only where there is wall.
        val torchH = p.spriteHeight(Art.TORCH, scale)
        if (w > 150f && band > torchH * 2.2f) {
            val torchY = top + captionH + torchH
            p.sprite(Art.TORCH, left + w * 0.13f, torchY, scale)
            p.sprite(Art.TORCH, left + w * 0.87f, torchY, scale)
        }

        val members = hud.party.size.coerceAtLeast(1)
        val slot = hud.party.maxOfOrNull { p.spriteWidth(it.cls.sprite, scale) } ?: (16f * scale)
        val enemyW = p.spriteWidth(hud.enemySprite, scale)
        val scene = Formation.scene(members, slot, enemyW, left + 4f * density, right - 4f * density)

        if (!hud.isDown) {
            p.shadow(scene.enemyCentre, floorY, enemyW * 0.8f)
            p.sprite(hud.enemySprite, scene.enemyCentre, floorY, scale)
        }
        for (i in Formation.drawOrder(members)) {
            val hero = hud.party[i]
            val centre = Formation.centreOf(i, members, slot, scene.partyLeft)
            // The front hero leans into the fight on the attack beat. A lunge is
            // motion, not a second drawing — which is all this pack could give.
            val lunge = if (i == 0 && !hud.isDown && (nowMs / 500L) % 2L == 0L) scale * 2f else 0f
            p.shadow(centre, floorY, slot * 0.8f)
            p.sprite(
                if (hero.down || hud.isDown) Art.GRAVE else hero.cls.sprite,
                centre + lunge,
                floorY,
                scale,
            )
        }

        // Caption last of all, on a plate, so it is never a sprite's hat.
        val caption = recent?.text ?: if (hud.isDown) "WIPED" else hud.enemyName
        val colour = recent?.let(::toneColour) ?: if (hud.isDown) Palette.HP else Palette.PARCHMENT
        val captionW = p.surface.measure(caption, text)
        p.fill(left + w / 2f - captionW / 2f - 4f, top, captionW + 8f, captionH, Palette.VOID)
        p.textCentre(left + w / 2f, top, caption, text, colour)

        val meterW = (w - 12f * density) / 2f
        p.bar(left + 2f * density, meterY, meterW, meterH, hud.frontFraction, Art.Bar.RED)
        if (!hud.isDown) {
            p.bar(right - 2f * density - meterW, meterY, meterW, meterH, hud.enemyFraction, Art.Bar.GOLD)
        }
    }

    /** The four buttons, painted where the RemoteViews touch zones are declared. */
    private fun drawDeck(
        p: Painter,
        hud: Hud,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        text: Float,
        density: Float,
    ) {
        val w = right - left
        val h = bottom - top
        val labels = listOf("LV UP", "HEAL", "CUBE", "AUTO")
        val values = listOf(hud.levelCost, hud.potionCost, hud.cubeLabel, if (hud.autoOn) "ON" else "OFF")
        val enabled = listOf(hud.canLevelUp, hud.canDrinkPotion, hud.canCube, hud.autoOn)
        val accents = listOf(Palette.GOLD, Palette.HP, Palette.grade(hud.cubeGrade), Palette.MAGIC)
        val small = text * 0.75f

        var x = left
        for (i in labels.indices) {
            val bw = w * DECK_SPLIT[i]
            p.button(x + 2f, top, bw - 4f, h, enabled[i], corner = 12f * density)
            val centre = x + bw / 2f
            val labelY = top + h / 2f - p.surface.lineHeight(small)
            p.textCentre(centre, labelY, labels[i], small, if (enabled[i]) Palette.PARCHMENT else Palette.PARCHMENT_DIM)
            p.textCentre(centre, labelY + p.surface.lineHeight(small), values[i], small, if (enabled[i]) accents[i] else Palette.PARCHMENT_DIM)
            x += bw
        }
    }

    /** A one-row bar has room for one action, and it is the one that always applies. */
    private fun drawAction(
        p: Painter,
        hud: Hud,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        text: Float,
        density: Float,
    ) {
        p.button(left, top, right - left, bottom - top, hud.canLevelUp, corner = 12f * density)
        val centre = (left + right) / 2f
        val y = (top + bottom) / 2f - p.surface.lineHeight(text)
        p.textCentre(centre, y, "LV UP", text, if (hud.canLevelUp) Palette.PARCHMENT else Palette.PARCHMENT_DIM)
        p.textCentre(centre, y + p.surface.lineHeight(text), hud.levelCost, text, if (hud.canLevelUp) Palette.GOLD else Palette.PARCHMENT_DIM)
    }

    /** Tone to colour — the one place a message's meaning becomes a pixel. */
    fun toneColour(ticker: Ticker): Int = when (ticker.tone) {
        Tone.NEUTRAL -> Palette.PARCHMENT_DIM
        Tone.GOOD -> Palette.GOLD
        Tone.BAD -> Palette.HP
        Tone.MAGIC -> Palette.MAGIC
        Tone.LOOT -> Palette.grade(ticker.grade)
    }
}
