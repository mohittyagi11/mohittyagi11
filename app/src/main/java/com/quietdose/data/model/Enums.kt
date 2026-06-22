package com.quietdose.data.model

/**
 * The core vocabulary of Dose. Everything the user can customize is expressed
 * through these enums plus the entities that reference them, so adding or
 * editing the stack never requires code changes.
 */

/**
 * What real-world event surfaces a group. This is the heart of the app — Dose
 * anchors to life events, not clock times.
 */
enum class TriggerType {
    /** Inferred wake (Health Connect sleep / Sleep API / activity / usage fusion). */
    WAKE,

    /** Geofence ENTER on the home location. */
    ARRIVE_HOME,

    /** Geofence ENTER on a saved place (office, gym…), configured in triggerConfig. */
    ARRIVE_PLACE,

    /** Leaving — geofence EXIT and/or a held ride-hailing "driver arriving" notification. */
    LEAVE,

    /** Inferred wind-down / before sleep. */
    BEFORE_SLEEP,

    /** A loose time window (e.g. mid-afternoon) — the only stable slot for some doses. */
    TIME_WINDOW,

    /** Every N days, phased from an anchor date (e.g. alternate-day iron). */
    CADENCE_DAYS,

    /** Specific days of the month, optionally a short run (e.g. a monthly pulse). */
    MONTHLY,

    /** No automatic trigger — the user opens it themselves. */
    MANUAL,
}

/** Physical form. Drives the icon and the "dose unit" hints; freely extensible. */
enum class ItemType {
    CAPSULE,
    TABLET,
    SOFTGEL,
    SPRAY,
    POWDER,
    LIQUID,
    GUMMY,
    SUBLINGUAL,
    OTHER,
}

/** How a dose is measured. */
enum class DoseUnit {
    UNIT,      // "2" (capsules/tablets/sprays)
    MG,
    MCG,
    G,
    IU,
    ML,
    DROP,
    SCOOP,
}

/** How often an item is due. The trigger says *when in the day*; this says *which days*. */
enum class FrequencyType {
    DAILY,
    EVERY_N_DAYS,   // uses interval + anchor
    WEEKLY,         // uses daysMask (bit 0 = Monday)
    MONTHLY_DAYS,   // uses daysOfMonth list
    AS_NEEDED,
}

/** Where an intake confirmation came from — useful for the brain/insights later. */
enum class IntakeSource {
    NOTIFICATION,   // one-tap from the notification, app never opened
    APP,
    AUTO,
}

/**
 * Behavioural flags encoded as data so the stack's rules hold without special
 * casing (e.g. fat-soluble items live with a meal; iron avoids tea/calcium).
 */
object ItemFlags {
    const val FAT_SOLUBLE = 1 shl 0
    const val EMPTY_STOMACH = 1 shl 1
    const val AVOID_CAFFEINE = 1 shl 2
    const val AVOID_CALCIUM = 1 shl 3
    const val WITH_FOOD = 1 shl 4
    const val FASTED = 1 shl 5
}
