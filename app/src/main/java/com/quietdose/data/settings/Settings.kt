package com.quietdose.data.settings

/**
 * Scalar app configuration — the few global knobs that aren't per-item. Stored
 * in DataStore (reactive, simple) rather than Room.
 *
 * Wake-signal weights live here too so the inference engine can be tuned
 * without a release.
 */
data class Settings(
    val earliestWakeHour: Int = 5,      // before this, a phone glance is "still night"
    val latestWakeFallbackHour: Int = 11, // guarantee the morning dose by here

    // Home / places (geofencing). 0.0 = unset.
    val homeLat: Double = 0.0,
    val homeLng: Double = 0.0,
    val homeRadiusM: Float = 120f,

    // Day rollover: doses before this hour count for the previous day.
    val dayRolloverHour: Int = 3,

    // Wake-signal fusion weights (0..1 each); fire when the sum crosses threshold.
    val weightHealthConnect: Float = 0.6f,
    val weightSleepApi: Float = 0.4f,
    val weightActivityWalk: Float = 0.4f,
    val weightSustainedUsage: Float = 0.4f,
    val wakeFireThreshold: Float = 0.8f,

    val onboarded: Boolean = false,
)
