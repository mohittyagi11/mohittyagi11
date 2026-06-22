package com.quietdose

import android.app.Application
import com.quietdose.data.Seed
import com.quietdose.data.db.DoseDatabase
import com.quietdose.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point and the manual DI root.
 *
 * Dose deliberately uses lightweight manual dependency injection (see
 * [ServiceLocator]) rather than a framework: fewer annotation processors means
 * a more robust CI build, and the graph is small enough to assemble by hand.
 */
class DoseApp : Application() {

    /** App-scoped scope for fire-and-forget startup work (seeding, re-arming). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Seed the default stack on first run, off the main thread.
        appScope.launch {
            val db = DoseDatabase.get(this@DoseApp)
            Seed.run(db.groupDao(), db.itemDao())
            // Re-arm all event triggers (idempotent; degrades gracefully without
            // permissions — at minimum the windowed alarms + wake fallback arm).
            com.quietdose.trigger.TriggerEngine.reArmAll(this@DoseApp)
        }
    }

    companion object {
        lateinit var instance: DoseApp
            private set
    }
}
