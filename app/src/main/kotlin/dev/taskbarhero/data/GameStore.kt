package dev.taskbarhero.data

import android.content.Context
import android.content.SharedPreferences
import dev.taskbarhero.game.Balance
import dev.taskbarhero.game.GameEvent
import dev.taskbarhero.game.GameState
import dev.taskbarhero.game.Hero
import dev.taskbarhero.game.HeroClass
import dev.taskbarhero.game.IdleEngine
import dev.taskbarhero.game.Loot
import dev.taskbarhero.game.Ticker
import dev.taskbarhero.game.Tone
import dev.taskbarhero.game.toTicker

/**
 * The one writer of the save.
 *
 * The widget and the open app both advance the same run, so if either kept its
 * own copy they would disagree within seconds. Everything goes through here, and
 * every call advances the simulation to now first — which is also what makes the
 * game keep playing while the phone is in a pocket.
 */
object GameStore {

    val balance = Balance()

    private const val FILE = "run"
    private const val SEPARATOR = ";"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Advances to now, saves, and hands back what happened. */
    @Synchronized
    fun tick(ctx: Context, nowMs: Long = System.currentTimeMillis()): IdleEngine.Result {
        val result = IdleEngine.advance(read(ctx, nowMs), nowMs, balance)
        write(ctx, result.state)
        result.events.lastOrNull()?.let { rememberTicker(ctx, it.toTicker()) }
        return result
    }

    /** The state as of now, without writing. For a redraw that must not change anything. */
    @Synchronized
    fun peek(ctx: Context, nowMs: Long = System.currentTimeMillis()): GameState =
        IdleEngine.advance(read(ctx, nowMs), nowMs, balance).state

    @Synchronized
    fun levelUp(ctx: Context): GameEvent? {
        val state = tick(ctx).state
        val after = IdleEngine.levelUp(state, balance) ?: return null
        write(ctx, after)
        val event = GameEvent.LevelUp(after.partyLevel)
        rememberTicker(ctx, event.toTicker())
        return event
    }

    @Synchronized
    fun drinkPotion(ctx: Context): GameEvent? {
        val state = tick(ctx).state
        val after = IdleEngine.drinkPotion(state, balance) ?: return null
        write(ctx, after)
        rememberTicker(ctx, GameEvent.Potion.toTicker())
        return GameEvent.Potion
    }

    @Synchronized
    fun cube(ctx: Context): GameEvent? {
        val state = tick(ctx).state
        val (after, event) = IdleEngine.cube(state, balance) ?: return null
        write(ctx, after)
        rememberTicker(ctx, event.toTicker())
        return event
    }

    @Synchronized
    fun toggleAuto(ctx: Context): Boolean {
        val state = tick(ctx).state
        val on = !state.autoLevel
        write(ctx, state.copy(autoLevel = on))
        rememberTicker(ctx, Ticker(if (on) "AUTO ON" else "AUTO OFF", Tone.MAGIC))
        return on
    }

    @Synchronized
    fun reset(ctx: Context) {
        write(ctx, GameState.newRun(System.currentTimeMillis(), balance))
        rememberTicker(ctx, Ticker("NEW RUN", Tone.GOOD))
    }

    // --- the last message ----------------------------------------------------

    /**
     * The widget keeps one line of event text, and the tone travels with it.
     *
     * A broadcast later the GameEvent no longer exists, so a caption stored as
     * text alone would come back in the wrong colour — which in this game means
     * the wrong meaning, since colour is what says "loot" rather than "death".
     */
    private fun rememberTicker(ctx: Context, ticker: Ticker) {
        prefs(ctx).edit()
            .putString("ticker", ticker.text)
            .putInt("tickerTone", ticker.tone.ordinal)
            .putInt("tickerGrade", ticker.grade)
            .putLong("tickerAt", System.currentTimeMillis())
            .apply()
    }

    /** The stored caption while it is still fresh, otherwise null. */
    fun recentTicker(ctx: Context, withinMs: Long, nowMs: Long = System.currentTimeMillis()): Ticker? {
        val p = prefs(ctx)
        val text = p.getString("ticker", null) ?: return null
        if (nowMs - p.getLong("tickerAt", 0L) > withinMs) return null
        return Ticker(text, Tone.entries[p.getInt("tickerTone", 0)], p.getInt("tickerGrade", -1))
    }

    // --- persistence ---------------------------------------------------------

    private fun read(ctx: Context, nowMs: Long): GameState {
        val p = prefs(ctx)
        if (!p.contains("act")) return GameState.newRun(nowMs, balance)

        val levels = p.getString("levels", "")!!.split(SEPARATOR).mapNotNull { it.toIntOrNull() }
        val healths = p.getString("healths", "")!!.split(SEPARATOR).mapNotNull { it.toDoubleOrNull() }
        val party = HeroClass.entries.mapIndexed { i, cls ->
            val level = levels.getOrElse(i) { 1 }
            Hero(cls, level, healths.getOrElse(i) { balance.heroMaxHp(cls, level) })
        }
        val stash = p.getString("stash", "")!!.split(SEPARATOR).mapNotNull { it.toIntOrNull() }

        return GameState(
            party = party,
            unlocked = p.getInt("unlocked", 1),
            act = p.getInt("act", 1),
            wave = p.getInt("wave", 1),
            enemyHp = p.getFloat("enemyHp", 0f).toDouble(),
            enemyIndex = p.getInt("enemyIndex", 0),
            gold = Double.fromBits(p.getLong("gold", 0L)),
            stash = if (stash.size == Loot.GRADES.size) stash else List(Loot.GRADES.size) { 0 },
            kills = p.getLong("kills", 0L),
            bossKills = p.getLong("bossKills", 0L),
            wipes = p.getLong("wipes", 0L),
            deepestAct = p.getInt("deepestAct", 1),
            deepestWave = p.getInt("deepestWave", 1),
            autoLevel = p.getBoolean("autoLevel", false),
            lastTickMs = p.getLong("lastTickMs", nowMs),
            downUntilMs = p.getLong("downUntilMs", 0L),
        )
    }

    private fun write(ctx: Context, s: GameState) {
        prefs(ctx).edit()
            .putString("levels", s.party.joinToString(SEPARATOR) { it.level.toString() })
            .putString("healths", s.party.joinToString(SEPARATOR) { it.hp.toString() })
            .putString("stash", s.stash.joinToString(SEPARATOR))
            .putInt("unlocked", s.unlocked)
            .putInt("act", s.act)
            .putInt("wave", s.wave)
            .putFloat("enemyHp", s.enemyHp.toFloat())
            .putInt("enemyIndex", s.enemyIndex)
            // Gold outgrows a Float within an hour and a Long within a day, so it
            // is stored as the bits of the Double it actually is.
            .putLong("gold", s.gold.toRawBits())
            .putLong("kills", s.kills)
            .putLong("bossKills", s.bossKills)
            .putLong("wipes", s.wipes)
            .putInt("deepestAct", s.deepestAct)
            .putInt("deepestWave", s.deepestWave)
            .putBoolean("autoLevel", s.autoLevel)
            .putLong("lastTickMs", s.lastTickMs)
            .putLong("downUntilMs", s.downUntilMs)
            .apply()
    }
}
