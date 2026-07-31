package dev.taskbarhero.paint

import dev.taskbarhero.assets.Art
import dev.taskbarhero.game.Balance
import dev.taskbarhero.game.GameState
import dev.taskbarhero.game.Hud
import dev.taskbarhero.game.Ticker
import dev.taskbarhero.game.Tone
import kotlin.math.roundToInt

/**
 * The bar, laid out where the original game puts things.
 *
 * TBH is a strip of open ground, not a framed room: the fighters stand on one
 * line with the dark beyond behind them, each carries a thin health line above
 * its head — green for the party, red for what it is fighting — the stage and its
 * progress float on a plate at the top centre, and the money sits top-left. That
 * arrangement is the point of this file; everything else here serves it.
 *
 * Measured in device pixels rather than an abstract grid, because the painted
 * buttons have to land exactly on the touch zones the RemoteViews layout declares,
 * and those are in dp.
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

    /** How long an event holds the plate instead of the stage number. */
    const val EVENT_MS = 3_500L

    private const val PAD = 4f

    /** The panel's frame, in pixels. Content starts inside it. */
    private fun corner(density: Float): Float = 12f * density

    fun deckHeight(density: Float): Float = DECK_DP * density

    fun hasDeck(heightPx: Float, density: Float): Boolean = heightPx >= TALL_DP * density

    /** Text is drawn at whole multiples of the font's design size, or it stops being pixel art. */
    fun textSize(density: Float): Float = (16f * maxOf(1, (density / 1.6f).roundToInt()))

    fun draw(
        p: Painter,
        widthPx: Float,
        heightPx: Float,
        density: Float,
        state: GameState,
        nowMs: Long,
        recent: Ticker? = null,
        b: Balance = Balance(),
    ) {
        val hud = Hud.of(state, b, nowMs)
        val text = textSize(density)
        val deck = if (hasDeck(heightPx, density)) deckHeight(density) else 0f
        val inset = corner(density) + PAD * density

        p.panel(Art.PANEL, 0f, 0f, widthPx, heightPx, corner = corner(density))

        val fieldBottom = heightPx - (if (deck > 0f) deck else inset)
        val fieldRight = if (deck > 0f) widthPx - inset else widthPx * (1f - ACTION_FRACTION)
        drawField(p, hud, state, inset, inset, fieldRight, fieldBottom, nowMs, recent, text, density, b)

        if (deck > 0f) {
            drawDeck(p, hud, inset, heightPx - deck, widthPx - inset, heightPx - inset, text, density)
        } else {
            drawAction(p, hud, fieldRight + 2f * density, inset, widthPx - inset, fieldBottom, text, density)
        }
    }

    /**
     * The battlefield: dark beyond, one ground line, and the fight standing on it.
     *
     * Drawn back to front — ground, fighters, the lines above their heads, then the
     * plate — because in this game everything readable sits on top of a sprite, and
     * the order is the only thing keeping them apart.
     */
    private fun drawField(
        p: Painter,
        hud: Hud,
        state: GameState,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        nowMs: Long,
        recent: Ticker?,
        text: Float,
        density: Float,
        b: Balance,
    ) {
        val w = right - left
        val h = bottom - top
        if (w < 60f || h < 40f) return

        p.fill(left, top, w, h, Palette.VOID)

        val plateH = p.surface.lineHeight(text) + 4f * density
        val lineH = maxOf(3f, 2f * density)
        // A thin strip of ground at the bottom, a thinner horizon just above it,
        // and the fighters get all the rest. Scenery given a third of the bar
        // leaves the party small under a wide empty sky, which is the opposite of
        // what this game is about.
        val groundH = (h * 0.14f).coerceIn(6f, 22f * density)
        val floorY = bottom - groundH
        // The horizon is the first thing to go. On a one-row bar every pixel of
        // height belongs to the fighters, and a distant treeline nobody can see
        // past a knight's head is not scenery, it is a stripe.
        val horizonH = groundH * 0.8f
        if (h > 150f) p.band(Art.horizon(state.act), left, floorY - horizonH, w, horizonH)
        p.band(Art.ground(state.act), left, floorY, w, groundH)

        val headroom = top + plateH + lineH * 4f
        val scale = p.scaleFor(floorY - headroom)

        val members = hud.party.size.coerceAtLeast(1)
        val slot = hud.party.maxOfOrNull { p.spriteWidth(it.cls.sprite, scale) } ?: (16f * scale)
        val enemyW = p.spriteWidth(hud.enemySprite, scale)
        val scene = Formation.scene(members, slot, enemyW, left + 4f * density, right - 4f * density)

        if (!hud.isDown) {
            p.shadow(scene.enemyCentre, floorY, enemyW * 0.8f)
            p.sprite(hud.enemySprite, scene.enemyCentre, floorY, scale)
            healthLine(
                p, scene.enemyCentre, floorY - p.spriteHeight(hud.enemySprite, scale) - lineH * 2f,
                enemyW, lineH, hud.enemyFraction, Palette.HP,
            )
        }

        for (i in Formation.drawOrder(members)) {
            val hero = hud.party[i]
            val centre = Formation.centreOf(i, members, slot, scene.partyLeft)
            // The front hero leans in on the attack beat. A lunge is motion, not a
            // second drawing — which is all a one-frame pack can give.
            val lunge = if (i == 0 && !hud.isDown && (nowMs / 500L) % 2L == 0L) scale * 2f else 0f
            val key = if (hero.down || hud.isDown) Art.GRAVE else hero.cls.sprite
            p.shadow(centre, floorY, slot * 0.8f)
            p.sprite(key, centre + lunge, floorY, scale)
            if (!hud.isDown) {
                healthLine(
                    p, centre, floorY - p.spriteHeight(key, scale) - lineH * 2f,
                    slot * 0.7f, lineH, hero.fraction, Palette.ALLY,
                )
            }
        }

        drawPlate(p, hud, state, left, top, right, plateH, recent, text, density, b)
        drawGold(p, hud, left + 2f * density, top + plateH + 4f * density, text)
    }

    /**
     * A fighter's health: a flat line over its head, no frame and no socket.
     *
     * Two colours and nothing else — green is ours, red is not — which is what lets
     * a glance at the bar say who is losing, without reading a number.
     */
    private fun healthLine(
        p: Painter,
        centreX: Float,
        y: Float,
        width: Float,
        height: Float,
        fraction: Double,
        colour: Int,
    ) {
        val x = centreX - width / 2f
        p.fill(x - 1f, y - 1f, width + 2f, height + 2f, Palette.INK)
        p.fill(x, y, width, height, Palette.EMPTY)
        val f = fraction.coerceIn(0.0, 1.0)
        if (f <= 0.0) return
        // A hair of colour survives at almost-dead, and a hair of dark at
        // almost-full: "nearly" must never look like "already".
        val filled = (width * f).toFloat().coerceIn(1f, if (f < 1.0) width - 1f else width)
        p.fill(x, y, filled, height, colour)
    }

    /**
     * The plate at the top centre: which stage, and how far into it.
     *
     * The dots are the act's waves, and they are what turns "3-07" from a label
     * into progress you can watch fill.
     */
    private fun drawPlate(
        p: Painter,
        hud: Hud,
        state: GameState,
        left: Float,
        top: Float,
        right: Float,
        height: Float,
        recent: Ticker?,
        text: Float,
        density: Float,
        b: Balance,
    ) {
        val label = recent?.text ?: if (hud.isDown) "WIPED" else "${hud.stage} ${hud.enemyName}"
        val colour = recent?.let(::toneColour) ?: if (hud.isDown) Palette.HP else Palette.PARCHMENT

        val dot = maxOf(3f, 2f * density)
        val gap = dot
        val dots = b.wavesPerAct
        val dotsW = dots * dot + (dots - 1) * gap
        val plateW = minOf(
            maxOf(p.surface.measure(label, text), dotsW) + 10f * density,
            right - left,
        )
        val centreX = (left + right) / 2f
        val x = centreX - plateW / 2f

        p.panel(Art.PANEL_INSET, x, top, plateW, height + dot + 6f * density, corner = 8f * density)
        p.textCentre(centreX, top + 2f * density, clip(p, label, plateW - 8f * density, text), text, colour)

        var dx = centreX - dotsW / 2f
        for (i in 1..dots) {
            p.fill(
                dx, top + height, dot, dot,
                when {
                    i == state.wave -> Palette.GOLD
                    i < state.wave -> Palette.PARCHMENT_DIM
                    else -> Palette.EMPTY
                },
            )
            dx += dot + gap
        }
    }

    /** Trims [s] to what fits, on a word boundary — a clipped word is a wrong word. */
    private fun clip(p: Painter, s: String, budget: Float, text: Float): String {
        if (p.surface.measure(s, text) <= budget) return s
        var out = s
        while (out.contains(' ') && p.surface.measure(out, text) > budget) {
            out = out.substringBeforeLast(' ')
        }
        while (out.isNotEmpty() && p.surface.measure(out, text) > budget) {
            out = out.dropLast(1)
        }
        return out
    }

    /** Money, top-left, with its coin — the one number always on screen. */
    private fun drawGold(p: Painter, hud: Hud, x: Float, y: Float, text: Float) {
        val scale = p.scaleToFit(Art.COIN, text * 0.9f)
        val w = p.spriteWidth(Art.COIN, scale)
        p.sprite(Art.COIN, x + w / 2f, y + text * 0.95f, scale)
        p.text(x + w + 4f, y, hud.gold, text, Palette.GOLD)
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
            p.textCentre(
                centre, labelY + p.surface.lineHeight(small), values[i], small,
                if (enabled[i]) accents[i] else Palette.PARCHMENT_DIM,
            )
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
        p.textCentre(
            centre, y + p.surface.lineHeight(text), hud.levelCost, text,
            if (hud.canLevelUp) Palette.GOLD else Palette.PARCHMENT_DIM,
        )
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
