package com.quietdose

import android.app.Application
import android.util.Log
import com.quietdose.data.Seed
import com.quietdose.data.db.DoseDatabase
import com.quietdose.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

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
        installCrashCatcher()

        // Seed the default stack on first run, off the main thread.
        appScope.launch {
            val db = DoseDatabase.get(this@DoseApp)
            Seed.run(db.groupDao(), db.itemDao())
            // Re-arm all event triggers (idempotent; degrades gracefully without
            // permissions — at minimum the windowed alarms + wake fallback arm).
            com.quietdose.trigger.TriggerEngine.reArmAll(this@DoseApp)
        }
    }

    /**
     * Under memory pressure, release the resident multi-GB model so it doesn't
     * starve the rest of the app (a major cause of native OOM crashes). It reloads
     * lazily next time it's needed, memory permitting.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            runCatching { com.quietdose.brain.BrainProvider.reset() }
        }
    }

    /**
     * Persist the stack trace of any fatal JVM exception so we can see exactly what
     * crashed (the Settings screen surfaces it, and it's also logged under "DoseCrash").
     * Native crashes (e.g. an OOM inside the model) won't reach here — their absence
     * is itself a signal that the fault was native.
     */
    private fun installCrashCatcher() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                Log.e("DoseCrash", "Uncaught on ${thread.name}", error)
                File(filesDir, "last_crash.txt").writeText(
                    "thread=${thread.name}\n" + Log.getStackTraceString(error),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        lateinit var instance: DoseApp
            private set

        /** The last fatal JVM crash, if any — shown in Settings to make bugs reportable. */
        fun lastCrash(app: Application): String? =
            runCatching { File(app.filesDir, "last_crash.txt").takeIf { it.exists() }?.readText() }.getOrNull()
    }
}
