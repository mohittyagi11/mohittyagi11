package com.quietdose.data.source

import android.content.Context
import com.quietdose.ui.viz.DayEvent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/** The wirable day-data domains. Each maps to one [DaySource]. */
enum class DaySourceKind { SUPPLEMENT, HEALTH_CONNECT, SKINCARE, DEVICE_USAGE }

/**
 * Aggregates the enabled [DaySource]s into one ordered stream of [DayEvent]s for
 * the day graph and timeline. Sources are individually toggleable and each is
 * isolated: a failing or empty source contributes nothing but never sinks the
 * rest (every source already returns an empty list rather than throwing, and we
 * defensively swallow anything that slips through).
 *
 * Sources run concurrently — a Health Connect read shouldn't serialize behind a
 * DataStore read.
 */
class DaySourceRegistry(
    context: Context,
    enabled: Set<DaySourceKind> = DEFAULT_ENABLED,
) : DaySource {

    private val appContext = context.applicationContext

    /** Live enable flags; flip to toggle a source on/off at runtime. */
    private val enabled: MutableSet<DaySourceKind> = enabled.toMutableSet()

    // Lazily built so we don't touch Health Connect / Usage APIs unless used.
    private val sources: Map<DaySourceKind, DaySource> by lazy {
        mapOf(
            DaySourceKind.SUPPLEMENT to SupplementSource(appContext),
            DaySourceKind.HEALTH_CONNECT to HealthConnectSource(appContext),
            DaySourceKind.SKINCARE to SkincareSource(appContext),
            DaySourceKind.DEVICE_USAGE to DeviceUsageSource(appContext),
        )
    }

    fun isEnabled(kind: DaySourceKind): Boolean = kind in enabled

    fun setEnabled(kind: DaySourceKind, value: Boolean) {
        if (value) enabled.add(kind) else enabled.remove(kind)
    }

    /** All enabled sources' events for [epochDay], sorted by time of day. */
    override suspend fun events(epochDay: Long): List<DayEvent> = coroutineScope {
        val active = sources.filterKeys { it in enabled }.values
        active
            .map { source -> async { runCatching { source.events(epochDay) }.getOrDefault(emptyList()) } }
            .awaitAll()
            .flatten()
            .sortedBy { it.minuteOfDay }
    }

    companion object {
        /**
         * On by default: supplements, Health Connect and skincare. Device usage is
         * opt-in (it needs the user to grant Usage Access in system settings).
         */
        val DEFAULT_ENABLED: Set<DaySourceKind> = setOf(
            DaySourceKind.SUPPLEMENT,
            DaySourceKind.HEALTH_CONNECT,
            DaySourceKind.SKINCARE,
        )
    }
}
