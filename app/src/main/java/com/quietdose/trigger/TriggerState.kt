package com.quietdose.trigger

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Tiny private DataStore for trigger-engine bookkeeping that doesn't belong in
 * user-facing [com.quietdose.data.settings.Settings] — chiefly "did the morning
 * WAKE group already fire today?" so the inference engine and the latest-wake
 * fallback never double-fire and so wake inference locks once per day.
 *
 * Kept separate from `dose_settings` to avoid touching the shared SettingsStore.
 */
class TriggerState(private val context: Context) {

    private object Keys {
        /** Epoch day (rollover-adjusted) on which the WAKE group last fired. */
        val lastMorningFiredDay = longPreferencesKey("lastMorningFiredDay")
    }

    suspend fun lastMorningFiredDay(): Long =
        context.dataStore.data.map { it[Keys.lastMorningFiredDay] ?: Long.MIN_VALUE }.first()

    /** True if the WAKE group has already fired for [epochDay]. */
    suspend fun morningAlreadyFired(epochDay: Long): Boolean =
        lastMorningFiredDay() >= epochDay

    /** Lock the morning for [epochDay]; idempotent. */
    suspend fun markMorningFired(epochDay: Long) {
        context.dataStore.edit { it[Keys.lastMorningFiredDay] = epochDay }
    }

    companion object {
        private val Context.dataStore: DataStore<Preferences> by
            preferencesDataStore("dose_trigger_state")
    }
}
