package com.quietdose.data.source

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.ui.graphics.Color
import com.quietdose.ui.theme.TintMorning
import com.quietdose.ui.theme.TintNight
import com.quietdose.ui.viz.DayEvent
import com.quietdose.ui.viz.EventSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** When in the day a skincare step lands. */
enum class SkinSlot(val anchorMinute: Int) {
    AM(7 * 60 + 15),   // just after waking, alongside the morning dose
    PM(22 * 60),       // wind-down
}

/**
 * One step in the routine. Self-contained — deliberately NOT a Room entity, so
 * the supplement database stays untouched. Identified by [id] so completion can
 * be tracked per day in the package's own DataStore.
 */
data class SkincareStep(
    val id: String,
    val name: String,
    val slot: SkinSlot,
    val tint: Color,
)

/**
 * A lightweight, on-device skincare routine. Ships with sensible seeded defaults
 * (AM SPF, PM retinoid + moisturizer) and projects each step into a SKINCARE
 * [DayEvent] at its slot's anchor. Per-day completion is stored in a tiny
 * DataStore that lives entirely inside this package — no Room, no schema change.
 */
class SkincareSource(context: Context) : DaySource {

    private val appContext = context.applicationContext

    /** The seeded routine. In-memory + stable ids; editing UI can come later. */
    val routine: List<SkincareStep> = DEFAULTS

    override suspend fun events(epochDay: Long): List<DayEvent> {
        val doneIds = doneFor(epochDay)
        return routine.map { step ->
            DayEvent(
                minuteOfDay = step.slot.anchorMinute,
                category = "Skincare",
                label = step.name,
                magnitude = 1f,
                done = step.id in doneIds,
                color = step.tint,
                source = EventSource.SKINCARE,
            )
        }
    }

    /** Mark/unmark a step done for a given day — for a future check-off UI. */
    suspend fun setDone(epochDay: Long, stepId: String, done: Boolean) {
        val key = dayKey(epochDay)
        appContext.skinStore.edit { prefs ->
            val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
            if (done) current.add(stepId) else current.remove(stepId)
            prefs[key] = current
        }
    }

    private suspend fun doneFor(epochDay: Long): Set<String> =
        appContext.skinStore.data
            .map { it[dayKey(epochDay)] ?: emptySet() }
            .first()

    private fun dayKey(epochDay: Long): Preferences.Key<Set<String>> =
        stringSetPreferencesKey("skin_done_$epochDay")

    companion object {
        private val DEFAULTS: List<SkincareStep> = listOf(
            SkincareStep("am_spf", "SPF", SkinSlot.AM, TintMorning),
            SkincareStep("pm_retinoid", "Retinoid", SkinSlot.PM, TintNight),
            SkincareStep("pm_moisturizer", "Moisturizer", SkinSlot.PM, TintNight),
        )
    }
}

/** Package-private DataStore, distinct from the app's "dose_settings" store. */
private val Context.skinStore: DataStore<Preferences> by preferencesDataStore("dose_skincare")
