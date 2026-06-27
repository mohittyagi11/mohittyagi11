package com.azadishashn.app.data

import android.content.Context
import com.azadishashn.app.game.GameState
import kotlinx.serialization.json.Json

/**
 * Persists the in-progress game to disk so it survives process death and app
 * updates. The current question, scores, and history are restored on launch;
 * only newly generated questions reflect any code changes.
 */
class GameStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("azadi_game", Context.MODE_PRIVATE)

    // Lenient so older saved games still load after the state shape evolves.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(state: GameState) {
        runCatching {
            prefs.edit()
                .putString(KEY_STATE, json.encodeToString(GameState.serializer(), state))
                .apply()
        }
    }

    fun load(): GameState? =
        prefs.getString(KEY_STATE, null)?.let { raw ->
            runCatching { json.decodeFromString(GameState.serializer(), raw) }.getOrNull()
        }

    fun clear() {
        prefs.edit().remove(KEY_STATE).apply()
    }

    companion object {
        private const val KEY_STATE = "state"
    }
}
