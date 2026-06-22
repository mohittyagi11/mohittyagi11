package com.quietdose.di

import android.content.Context
import com.quietdose.brain.Brain
import com.quietdose.brain.BrainProvider
import com.quietdose.data.DoseRepository
import com.quietdose.data.db.DoseDatabase
import com.quietdose.data.settings.SettingsStore

/**
 * Lightweight manual DI. The dependency graph is small and the lifetimes are
 * simple (everything is application-scoped), so a hand-rolled locator beats a
 * framework here — and keeps the build free of extra annotation processors.
 */
object ServiceLocator {

    @Volatile private var repo: DoseRepository? = null
    @Volatile private var settings: SettingsStore? = null

    fun repository(context: Context): DoseRepository =
        repo ?: synchronized(this) {
            repo ?: run {
                val db = DoseDatabase.get(context)
                DoseRepository(db.groupDao(), db.itemDao(), db.intakeDao()).also { repo = it }
            }
        }

    fun settings(context: Context): SettingsStore =
        settings ?: synchronized(this) {
            settings ?: SettingsStore(context.applicationContext).also { settings = it }
        }

    /** The on-device brain (heuristic by default; on-device LLM if a model is present). */
    fun brain(context: Context): Brain = BrainProvider.get(context)
}
