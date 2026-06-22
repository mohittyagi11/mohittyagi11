package com.quietdose.data.source

import android.content.Context
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.model.TriggerType
import com.quietdose.di.ServiceLocator
import com.quietdose.ui.theme.GroupStyle
import com.quietdose.ui.viz.DayEvent
import com.quietdose.ui.viz.EventSource
import com.quietdose.util.DateUtils
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Projects the user's supplement groups into [DayEvent]s — one event per group,
 * anchored to its trigger time, sized by how many items are due and filled when
 * all of them are taken.
 *
 * This mirrors the ad-hoc `buildEvents` / `anchorMinute` logic currently inlined
 * in `HomeViewModel` so it can REPLACE it: once `HomeViewModel` reads from the
 * [DaySourceRegistry] the two cannot drift apart.
 */
class SupplementSource(context: Context) : DaySource {

    private val appContext = context.applicationContext
    private val repo = ServiceLocator.repository(appContext)

    override suspend fun events(epochDay: Long): List<DayEvent> {
        // Snapshot the reactive sources once for this day.
        val groups = repo.observeGroups().first()
        val items = repo.observeAllItems().first()
        val taken = repo.observeTakenToday(epochDay).first().toSet()

        val byGroup = items.groupBy { it.groupId }

        return groups.mapNotNull { group ->
            val due = (byGroup[group.id] ?: emptyList())
                .filter { DateUtils.isDueOn(it, epochDay) }
            if (due.isEmpty()) return@mapNotNull null

            val allTaken = due.all { it.id in taken }
            DayEvent(
                minuteOfDay = anchorMinute(group),
                category = group.name,
                label = group.name,
                magnitude = due.size.toFloat(),
                done = allTaken,
                color = GroupStyle.tint(group),
                source = EventSource.SUPPLEMENT,
            )
        }
    }

    /**
     * The anchor minute for a group's trigger — a stand-in for the real trigger
     * engine, identical to `HomeViewModel.anchorMinute`. Exhaustive over
     * [TriggerType] so a new trigger kind forces a decision here.
     */
    private fun anchorMinute(g: GroupEntity): Int = when (g.trigger) {
        TriggerType.WAKE -> 7 * 60
        TriggerType.TIME_WINDOW, TriggerType.CADENCE_DAYS ->
            runCatching { JSONObject(g.triggerConfig).optInt("startMin", 14 * 60) }
                .getOrDefault(14 * 60)
        TriggerType.ARRIVE_HOME, TriggerType.ARRIVE_PLACE -> 18 * 60 + 30
        TriggerType.LEAVE -> 18 * 60
        TriggerType.BEFORE_SLEEP -> 22 * 60 + 30
        TriggerType.MONTHLY -> 23 * 60
        TriggerType.MANUAL -> 12 * 60
    }
}
