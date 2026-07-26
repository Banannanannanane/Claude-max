package dev.dotmatrix.taskbarhero.engine

/**
 * Everything the widget prints, derived from state in one place: the Android
 * renderer only positions dots, and the strings themselves stay unit-tested.
 */
data class Hud(
    val stage: String,
    val level: String,
    val gold: String,
    val enemyName: String,
    /** Bar-sized caption: boss names are long, "BOSS 3" always fits. */
    val enemyShort: String,
    val enemySprite: SpriteKey,
    val heroHp: Double,
    val enemyHp: Double,
    val runeProgress: Double,
    val runes: String,
    val deepest: String,
    val buttonLabel: String,
    val buttonCost: String,
    val buttonEnabled: Boolean,
    val isDown: Boolean,
    val ticker: String,
) {
    companion object {
        fun of(
            state: GameState,
            b: Balance = Balance(),
            lastEvent: GameEvent? = null,
            autoNote: Boolean = true,
        ): Hud {
            val mob = Bestiary.mob(state.act, state.wave, state.enemyIdx, b)
            val ticker = when {
                state.isDown -> "HERO DOWN / REGROUPING"
                lastEvent != null -> lastEvent.caption
                state.autoLevel && autoNote -> "AUTO / IDLING"
                else -> "ACT ${Fmt.stage(state.act, state.wave)} / ${mob.name}"
            }
            return Hud(
                stage = Fmt.stage(state.act, state.wave),
                level = "LV ${state.level}",
                gold = Fmt.short(state.gold),
                enemyName = mob.name,
                enemyShort = if (b.isBossWave(state.wave)) "BOSS ${state.act}" else mob.name,
                enemySprite = mob.sprite,
                heroHp = state.heroHpFraction(b),
                enemyHp = state.enemyHpFraction(b),
                runeProgress = state.runeProgress(b),
                runes = "R${state.runes}",
                deepest = "BEST ${Fmt.stage(state.deepestAct, state.deepestWave)}",
                buttonLabel = if (state.autoLevel) "AUTO" else "LV UP",
                buttonCost = Fmt.short(b.levelCost(state.level)),
                buttonEnabled = state.canLevelUp(b),
                isDown = state.isDown,
                ticker = ticker,
            )
        }
    }
}
