package com.quietdose

import android.app.Application

/**
 * Application entry point and (eventually) the manual DI container.
 *
 * Dose deliberately uses lightweight manual dependency injection rather than a
 * framework: fewer annotation processors means a more robust CI build, and the
 * graph is small enough to assemble by hand. The data layer, scheduler, wake
 * engine and "brain" services will be exposed from here as they land.
 */
class DoseApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Increment 2+ will: open the Room database, seed the default stack on
        // first run, register notification channels, and re-arm all triggers.
    }

    companion object {
        lateinit var instance: DoseApp
            private set
    }
}
