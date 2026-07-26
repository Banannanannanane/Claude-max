package dev.taskbarhero.engine

/**
 * Everything the widget prints, derived from state in one place: the layouts only
 * position pixels, and the strings themselves stay unit-tested.
 */
data class Hud(
    val stage: String,
    val level: String,
    val gold: String,
    val enemyName: String,
    /** Bar-sized caption: boss names are long, "BOSS 3" always fits. */
    val enemyShort: String,
    val enemySprite: SpriteKey,
    val party: List<HeroHud>,
    val enemyHp: Double,
    val runeProgress: Double,
    val runes: String,
    val deepest: String,
    val buttonLabel: String,
    val buttonCost: String,
    val buttonEnabled: Boolean,
    val potionCost: String,
    val potionEnabled: Boolean,
    /** The grade the Cube would hand back, or null when nothing can fuse. */
    val cubeGrade: Int?,
    val cubeLabel: String,
    val gearBonus: String,
    val autoOn: Boolean,
    val isDown: Boolean,
    val ticker: String,
) {
    val cubeEnabled: Boolean get() = cubeGrade != null

    /** Health of the hero the monsters are actually hitting. */
    val frontHp: Double get() = party.firstOrNull { !it.down }?.hp ?: 0.0

    companion object {
        fun of(
            state: GameState,
            b: Balance = Balance(),
            lastEvent: GameEvent? = null,
            autoNote: Boolean = true,
        ): Hud {
            val mob = Bestiary.mob(state.act, state.wave, state.enemyIdx, b)
            val fusable = state.fusableGrade(b)
            val ticker = when {
                state.isDown -> "PARTY WIPED / REGROUPING"
                lastEvent != null -> lastEvent.caption
                state.autoLevel && autoNote -> "AUTO / IDLING"
                else -> "ACT ${Fmt.stage(state.act, state.wave)} / ${mob.name}"
            }
            return Hud(
                stage = Fmt.stage(state.act, state.wave),
                level = "LV ${state.partyLevel}",
                gold = Fmt.short(state.gold),
                enemyName = mob.name,
                enemyShort = if (b.isBossWave(state.wave)) "BOSS ${state.act}" else mob.name,
                enemySprite = mob.sprite,
                party = state.roster.map { HeroHud(it.cls, "LV ${it.level}", it.hpFraction(b), it.isDown) },
                enemyHp = state.enemyHpFraction(b),
                runeProgress = state.runeProgress(b),
                runes = "R${state.runes}",
                deepest = "BEST ${Fmt.stage(state.deepestAct, state.deepestWave)}",
                buttonLabel = if (state.autoLevel) "AUTO" else "LV UP",
                buttonCost = Fmt.short(state.levelCost(b)),
                buttonEnabled = state.canLevelUp(b),
                potionCost = Fmt.short(b.potionCost(state.partyLevel)),
                potionEnabled = state.canDrinkPotion(b),
                cubeGrade = fusable?.plus(1),
                // Shows the fullest pile even when it cannot fuse yet, so the
                // button explains what it is waiting for instead of just "-".
                cubeLabel = state.fullestGrade()
                    ?.let { "${state.stash[it]}/${b.cubeInput}" }
                    ?: "0/${b.cubeInput}",
                gearBonus = "+${Fmt.gain(state.gearPower(b))}",
                autoOn = state.autoLevel,
                isDown = state.isDown,
                ticker = ticker,
            )
        }
    }
}

/** One party member, as the screen sees it. */
data class HeroHud(val cls: HeroClass, val level: String, val hp: Double, val down: Boolean)
