package com.azadishashn.app.data

import android.content.Context
import com.azadishashn.app.game.GameState
import kotlinx.serialization.json.Json
import java.util.UUID

/** A one-line summary of a saved game, for the library list. */
data class GameSummary(
    val id: String,
    val title: String,
    val playerNames: List<String>,
    val round: Int,
    val updatedAt: Long,
    val totalCards: Int,
    val leader: String,
    val finished: Boolean,
)

/**
 * Persists games to disk so they survive process death and app updates. This is
 * a small *library*: each game is stored under its own id (`game_<id>`), with an
 * ordered [index] of ids and an [activeId] pointer. The current question,
 * scores, and history are restored on launch; only newly generated questions
 * reflect any code changes.
 */
class GameStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("azadi_game", Context.MODE_PRIVATE)

    // Lenient so older saved games still load after the state shape evolves.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Human-readable variant for the export/import (copy-paste) flow.
    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** Serialize a game to shareable, human-readable JSON (for copy / export). */
    fun export(state: GameState): String =
        prettyJson.encodeToString(GameState.serializer(), state)

    /** Parse a pasted/exported game back into a [GameState]; null if it isn't valid. */
    fun import(raw: String): GameState? =
        raw.trim().takeIf { it.isNotEmpty() }?.let {
            runCatching { json.decodeFromString(GameState.serializer(), it) }.getOrNull()
        }

    // -- Library -------------------------------------------------------------

    fun newId(): String = UUID.randomUUID().toString()

    private fun index(): List<String> =
        prefs.getString(KEY_INDEX, null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    private fun writeIndex(ids: List<String>) {
        prefs.edit().putString(KEY_INDEX, ids.joinToString(",")).apply()
    }

    fun activeId(): String? = prefs.getString(KEY_ACTIVE, null)?.takeIf { it.isNotBlank() }

    fun setActive(id: String) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    /**
     * Persist a game blob (stamping [GameState.updatedAt]) and ensure it's in the
     * index. Does NOT change the active pointer — the caller owns that via
     * [setActive], so renaming/saving a non-active game can't steal focus.
     */
    fun saveGame(state: GameState) {
        if (state.id.isBlank()) return
        runCatching {
            val stamped = state.copy(updatedAt = System.currentTimeMillis())
            prefs.edit()
                .putString(gameKey(state.id), json.encodeToString(GameState.serializer(), stamped))
                .putString(KEY_INDEX, (index().filterNot { it == state.id } + state.id).joinToString(","))
                .apply()
        }
    }

    fun loadGame(id: String): GameState? =
        prefs.getString(gameKey(id), null)?.let { raw ->
            runCatching { json.decodeFromString(GameState.serializer(), raw) }.getOrNull()
        }

    fun deleteGame(id: String) {
        val edit = prefs.edit().remove(gameKey(id))
        if (activeId() == id) edit.remove(KEY_ACTIVE)
        edit.apply()
        writeIndex(index().filterNot { it == id })
    }

    /** Summaries for the library list, most-recently-updated first. */
    fun listSummaries(): List<GameSummary> =
        index().mapNotNull { id -> loadGame(id)?.let(::summaryOf) }
            .sortedByDescending { it.updatedAt }

    private fun summaryOf(s: GameState): GameSummary {
        val leader = s.players.maxByOrNull { it.total }?.takeIf { it.total > 0 }?.name ?: ""
        return GameSummary(
            id = s.id,
            title = s.title.ifBlank { s.players.joinToString(" · ") { it.name }.ifBlank { "Untitled game" } },
            playerNames = s.players.map { it.name },
            round = s.round,
            updatedAt = s.updatedAt,
            totalCards = s.players.sumOf { it.total },
            leader = leader,
            finished = s.finished,
        )
    }

    /**
     * One-time migration: if a pre-library single-slot game exists and the
     * library is empty, fold it into a library entry so the user's paused game
     * survives the upgrade. Returns the migrated id, or null if nothing to do.
     */
    fun migrateLegacyIfNeeded(): String? {
        if (index().isNotEmpty()) return null
        val legacy = prefs.getString(KEY_STATE, null)?.let { raw ->
            runCatching { json.decodeFromString(GameState.serializer(), raw) }.getOrNull()
        } ?: return null
        if (legacy.players.isEmpty()) return null
        val now = System.currentTimeMillis()
        val id = legacy.id.ifBlank { newId() }
        val migrated = legacy.copy(
            id = id,
            title = legacy.title.ifBlank { legacy.players.joinToString(" · ") { it.name } },
            createdAt = if (legacy.createdAt == 0L) now else legacy.createdAt,
            updatedAt = now,
        )
        prefs.edit()
            .putString(gameKey(id), json.encodeToString(GameState.serializer(), migrated))
            .putString(KEY_INDEX, id)
            .putString(KEY_ACTIVE, id)
            .remove(KEY_STATE)
            .apply()
        return id
    }

    companion object {
        private const val KEY_STATE = "state"      // legacy single slot (pre-library)
        private const val KEY_INDEX = "game_index"
        private const val KEY_ACTIVE = "active_id"
        private fun gameKey(id: String) = "game_$id"
    }
}
