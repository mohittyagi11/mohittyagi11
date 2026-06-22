package com.quietdose.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("dose_settings")

/**
 * Reactive accessor for [Settings]. Reads map preference keys to the data class;
 * writes patch individual keys. Defaults come from [Settings] itself, so a fresh
 * install is fully configured.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val earliestWakeHour = intPreferencesKey("earliestWakeHour")
        val latestWakeFallbackHour = intPreferencesKey("latestWakeFallbackHour")
        val homeLat = doublePreferencesKey("homeLat")
        val homeLng = doublePreferencesKey("homeLng")
        val homeRadiusM = floatPreferencesKey("homeRadiusM")
        val dayRolloverHour = intPreferencesKey("dayRolloverHour")
        val weightHealthConnect = floatPreferencesKey("weightHealthConnect")
        val weightSleepApi = floatPreferencesKey("weightSleepApi")
        val weightActivityWalk = floatPreferencesKey("weightActivityWalk")
        val weightSustainedUsage = floatPreferencesKey("weightSustainedUsage")
        val wakeFireThreshold = floatPreferencesKey("wakeFireThreshold")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            earliestWakeHour = p[Keys.earliestWakeHour] ?: d.earliestWakeHour,
            latestWakeFallbackHour = p[Keys.latestWakeFallbackHour] ?: d.latestWakeFallbackHour,
            homeLat = p[Keys.homeLat] ?: d.homeLat,
            homeLng = p[Keys.homeLng] ?: d.homeLng,
            homeRadiusM = p[Keys.homeRadiusM] ?: d.homeRadiusM,
            dayRolloverHour = p[Keys.dayRolloverHour] ?: d.dayRolloverHour,
            weightHealthConnect = p[Keys.weightHealthConnect] ?: d.weightHealthConnect,
            weightSleepApi = p[Keys.weightSleepApi] ?: d.weightSleepApi,
            weightActivityWalk = p[Keys.weightActivityWalk] ?: d.weightActivityWalk,
            weightSustainedUsage = p[Keys.weightSustainedUsage] ?: d.weightSustainedUsage,
            wakeFireThreshold = p[Keys.wakeFireThreshold] ?: d.wakeFireThreshold,
            onboarded = p[Keys.onboarded] ?: d.onboarded,
        )
    }

    suspend fun setHome(lat: Double, lng: Double, radiusM: Float) {
        context.dataStore.edit {
            it[Keys.homeLat] = lat
            it[Keys.homeLng] = lng
            it[Keys.homeRadiusM] = radiusM
        }
    }

    suspend fun setWakeWindow(earliestHour: Int, latestFallbackHour: Int) {
        context.dataStore.edit {
            it[Keys.earliestWakeHour] = earliestHour
            it[Keys.latestWakeFallbackHour] = latestFallbackHour
        }
    }

    suspend fun setOnboarded(value: Boolean) {
        context.dataStore.edit { it[Keys.onboarded] = value }
    }
}
