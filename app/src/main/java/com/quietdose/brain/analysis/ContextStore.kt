package com.quietdose.brain.analysis

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** What kind of knowledge a [ContextEntry] is — the incremental reasoning trail. */
enum class ContextKind { FACT, DEDUCTION, DECISION, USER_INTENT, USER_DECISION }

/**
 * One accumulated piece of context about an item. The store grows over time so
 * the brain can *reuse* what it has already established (facts it validated,
 * deductions it made, decisions you took) instead of re-deriving from scratch.
 */
data class ContextEntry(
    val kind: ContextKind,
    val text: String,
    val source: String,
    val confidence: Float,
    val createdAtEpochMs: Long,
)

private val Context.analysisContextStore: DataStore<Preferences> by
    preferencesDataStore("dose_analysis_context")

/**
 * On-device, append-only context per item (keyed by a normalized item key, e.g.
 * the lowercase name, so it works for drafts that aren't saved yet). Stored as a
 * small JSON array in DataStore — no extra Room schema, survives app updates.
 */
object ContextStore {

    private fun keyOf(itemKey: String) = stringPreferencesKey("ctx_" + itemKey.lowercase().trim())

    fun observe(context: Context, itemKey: String): Flow<List<ContextEntry>> =
        context.applicationContext.analysisContextStore.data.map { prefs ->
            parse(prefs[keyOf(itemKey)])
        }

    suspend fun snapshot(context: Context, itemKey: String): List<ContextEntry> =
        observe(context, itemKey).first()

    /** Append [entries] to the item's context, de-duplicating by (kind,text). */
    suspend fun add(context: Context, itemKey: String, entries: List<ContextEntry>) {
        if (entries.isEmpty()) return
        context.applicationContext.analysisContextStore.edit { prefs ->
            val existing = parse(prefs[keyOf(itemKey)])
            val seen = existing.map { it.kind to it.text }.toMutableSet()
            val merged = existing.toMutableList()
            entries.forEach { e ->
                if (seen.add(e.kind to e.text)) merged += e
            }
            prefs[keyOf(itemKey)] = encode(merged.takeLast(60)) // cap growth
        }
    }

    suspend fun recordIntent(context: Context, itemKey: String, intent: String) {
        if (intent.isBlank()) return
        add(context, itemKey, listOf(entry(ContextKind.USER_INTENT, intent, "user", 1f)))
    }

    suspend fun recordDecision(context: Context, itemKey: String, decision: String) {
        add(context, itemKey, listOf(entry(ContextKind.USER_DECISION, decision, "user", 1f)))
    }

    fun entry(kind: ContextKind, text: String, source: String, confidence: Float) =
        ContextEntry(kind, text, source, confidence, System.currentTimeMillis())

    // --- (de)serialization -------------------------------------------------

    private fun parse(raw: String?): List<ContextEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val kind = runCatching { ContextKind.valueOf(o.optString("k")) }.getOrNull() ?: continue
                add(
                    ContextEntry(
                        kind = kind,
                        text = o.optString("t"),
                        source = o.optString("s"),
                        confidence = o.optDouble("c", 1.0).toFloat(),
                        createdAtEpochMs = o.optLong("at"),
                    ),
                )
            }
        }
    }

    private fun encode(entries: List<ContextEntry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("k", e.kind.name)
                    .put("t", e.text)
                    .put("s", e.source)
                    .put("c", e.confidence.toDouble())
                    .put("at", e.createdAtEpochMs),
            )
        }
        return arr.toString()
    }
}
