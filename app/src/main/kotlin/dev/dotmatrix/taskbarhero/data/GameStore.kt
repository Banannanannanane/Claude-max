package dev.dotmatrix.taskbarhero.data

import android.content.Context
import android.content.SharedPreferences
import dev.dotmatrix.taskbarhero.engine.Balance
import dev.dotmatrix.taskbarhero.engine.GameEvent
import dev.dotmatrix.taskbarhero.engine.GameState
import dev.dotmatrix.taskbarhero.engine.IdleEngine
import dev.dotmatrix.taskbarhero.engine.TickResult

/**
 * The save file, in SharedPreferences. Flat keys instead of JSON: the state is
 * a handful of primitives, and both the widget broadcast and the activity touch
 * it from the same process, so plain synchronised access is enough.
 *
 * Every read advances the simulation first — that is what makes offline
 * progress work without a service running in the background.
 */
object GameStore {

    private const val PREFS = "tbh_state"
    private const val SCHEMA = 1

    private const val KEY_SCHEMA = "schema"
    private const val KEY_LAST_TICK = "last_tick"
    private const val KEY_LEVEL = "level"
    private const val KEY_RUNES = "runes"
    private const val KEY_GOLD = "gold"
    private const val KEY_XP = "xp"
    private const val KEY_ACT = "act"
    private const val KEY_WAVE = "wave"
    private const val KEY_ENEMY_IDX = "enemy_idx"
    private const val KEY_HERO_HP = "hero_hp"
    private const val KEY_ENEMY_HP = "enemy_hp"
    private const val KEY_DOWN_UNTIL = "down_until"
    private const val KEY_KILLS = "kills"
    private const val KEY_BOSS_KILLS = "boss_kills"
    private const val KEY_DEATHS = "deaths"
    private const val KEY_DEEPEST_ACT = "deepest_act"
    private const val KEY_DEEPEST_WAVE = "deepest_wave"
    private const val KEY_LIFETIME_GOLD = "lifetime_gold"
    private const val KEY_AUTO = "auto_level"
    private const val KEY_EVENT_TEXT = "event_text"
    private const val KEY_EVENT_AT = "event_at"

    val balance = Balance()

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Advances the run to [nowMs], persists it, and returns what happened. */
    @Synchronized
    fun tick(ctx: Context, nowMs: Long = System.currentTimeMillis()): TickResult {
        val p = prefs(ctx)
        val result = IdleEngine.advance(load(p, nowMs), nowMs, balance)
        save(p, result.state, result.events.lastOrNull(), nowMs)
        return result
    }

    @Synchronized
    fun levelUp(ctx: Context, nowMs: Long = System.currentTimeMillis()): TickResult? {
        val p = prefs(ctx)
        val ticked = IdleEngine.advance(load(p, nowMs), nowMs, balance)
        val levelled = IdleEngine.levelUp(ticked.state, balance) ?: run {
            save(p, ticked.state, ticked.events.lastOrNull(), nowMs)
            return null
        }
        val event = GameEvent.LevelUp(levelled.level)
        save(p, levelled, event, nowMs)
        return ticked.copy(state = levelled, events = ticked.events + event)
    }

    @Synchronized
    fun setAutoLevel(ctx: Context, enabled: Boolean, nowMs: Long = System.currentTimeMillis()): GameState {
        val p = prefs(ctx)
        val state = IdleEngine.advance(load(p, nowMs), nowMs, balance).state.copy(autoLevel = enabled)
        save(p, state, null, nowMs)
        return state
    }

    @Synchronized
    fun reset(ctx: Context, nowMs: Long = System.currentTimeMillis()): GameState {
        val fresh = GameState.newRun(nowMs, balance)
        val p = prefs(ctx)
        p.edit().clear().apply()
        save(p, fresh, null, nowMs)
        return fresh
    }

    /** Reads without simulating — for renderers that already ticked. */
    @Synchronized
    fun peek(ctx: Context, nowMs: Long = System.currentTimeMillis()): GameState = load(prefs(ctx), nowMs)

    /** Caption of the most recent event, or null once it has gone stale. */
    fun recentEvent(ctx: Context, nowMs: Long, windowMs: Long): String? {
        val p = prefs(ctx)
        val at = p.getLong(KEY_EVENT_AT, 0L)
        if (at == 0L || nowMs - at > windowMs || nowMs < at) return null
        return p.getString(KEY_EVENT_TEXT, null)
    }

    private fun load(p: SharedPreferences, nowMs: Long): GameState {
        if (p.getInt(KEY_SCHEMA, 0) != SCHEMA) return GameState.newRun(nowMs, balance)
        return GameState(
            lastTickMs = p.getLong(KEY_LAST_TICK, nowMs),
            level = p.getInt(KEY_LEVEL, 1).coerceAtLeast(1),
            runes = p.getInt(KEY_RUNES, 0).coerceAtLeast(0),
            gold = p.getFloat(KEY_GOLD, 0f).toDouble(),
            xp = p.getFloat(KEY_XP, 0f).toDouble(),
            act = p.getInt(KEY_ACT, 1).coerceAtLeast(1),
            wave = p.getInt(KEY_WAVE, 1).coerceAtLeast(1),
            enemyIdx = p.getInt(KEY_ENEMY_IDX, 0).coerceAtLeast(0),
            heroHp = p.getFloat(KEY_HERO_HP, 0f).toDouble(),
            enemyHp = p.getFloat(KEY_ENEMY_HP, 0f).toDouble(),
            downUntilMs = p.getLong(KEY_DOWN_UNTIL, 0L),
            kills = p.getLong(KEY_KILLS, 0L),
            bossKills = p.getLong(KEY_BOSS_KILLS, 0L),
            deaths = p.getLong(KEY_DEATHS, 0L),
            deepestAct = p.getInt(KEY_DEEPEST_ACT, 1).coerceAtLeast(1),
            deepestWave = p.getInt(KEY_DEEPEST_WAVE, 1).coerceAtLeast(1),
            lifetimeGold = p.getFloat(KEY_LIFETIME_GOLD, 0f).toDouble(),
            autoLevel = p.getBoolean(KEY_AUTO, false),
        )
    }

    private fun save(p: SharedPreferences, s: GameState, event: GameEvent?, nowMs: Long) {
        p.edit().apply {
            putInt(KEY_SCHEMA, SCHEMA)
            putLong(KEY_LAST_TICK, s.lastTickMs)
            putInt(KEY_LEVEL, s.level)
            putInt(KEY_RUNES, s.runes)
            // Floats lose precision on huge idle numbers, which is fine: nothing
            // in the HUD shows more than three significant digits.
            putFloat(KEY_GOLD, s.gold.toFloat())
            putFloat(KEY_XP, s.xp.toFloat())
            putInt(KEY_ACT, s.act)
            putInt(KEY_WAVE, s.wave)
            putInt(KEY_ENEMY_IDX, s.enemyIdx)
            putFloat(KEY_HERO_HP, s.heroHp.toFloat())
            putFloat(KEY_ENEMY_HP, s.enemyHp.toFloat())
            putLong(KEY_DOWN_UNTIL, s.downUntilMs)
            putLong(KEY_KILLS, s.kills)
            putLong(KEY_BOSS_KILLS, s.bossKills)
            putLong(KEY_DEATHS, s.deaths)
            putInt(KEY_DEEPEST_ACT, s.deepestAct)
            putInt(KEY_DEEPEST_WAVE, s.deepestWave)
            putFloat(KEY_LIFETIME_GOLD, s.lifetimeGold.toFloat())
            putBoolean(KEY_AUTO, s.autoLevel)
            if (event != null) {
                putString(KEY_EVENT_TEXT, event.caption)
                putLong(KEY_EVENT_AT, nowMs)
            }
        }.apply()
    }
}
