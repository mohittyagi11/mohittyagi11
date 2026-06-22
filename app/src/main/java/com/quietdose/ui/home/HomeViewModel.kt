package com.quietdose.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.IntakeSource
import com.quietdose.data.model.TriggerType
import com.quietdose.di.ServiceLocator
import com.quietdose.notify.Notifier
import com.quietdose.ui.theme.GroupStyle
import com.quietdose.ui.viz.DayEvent
import com.quietdose.ui.viz.EventSource
import com.quietdose.util.DateUtils
import org.json.JSONObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ItemRow(val item: ItemEntity, val taken: Boolean)

data class GroupCard(val group: GroupEntity, val items: List<ItemRow>) {
    val takenCount: Int get() = items.count { it.taken }
    val total: Int get() = items.size
    val done: Boolean get() = total > 0 && takenCount == total
}

/** Where a timeline moment sits relative to now. */
enum class NodeStatus { DONE, NOW, DUE, UPCOMING }

/** One moment on the day's timeline — a routine anchored to its trigger. */
data class TimelineNode(
    val card: GroupCard,
    val status: NodeStatus,
    val anchorLabel: String,
    val summary: String,
)

data class HomeUiState(
    val cards: List<GroupCard> = emptyList(),
    val timeline: List<TimelineNode> = emptyList(),
    val dayEvents: List<DayEvent> = emptyList(),
    val focusGroupId: Long? = null,
    val epochDay: Long = 0,
    val loading: Boolean = true,
) {
    val allDone: Boolean get() = cards.isNotEmpty() && cards.all { it.done }
    val totalDue: Int get() = cards.sumOf { it.total }
    val totalTaken: Int get() = cards.sumOf { it.takenCount }

    val focus: GroupCard? get() = cards.firstOrNull { it.group.id == focusGroupId }
    val rest: List<GroupCard> get() = cards.filter { it.group.id != focusGroupId }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.repository(app)
    private val settingsStore = ServiceLocator.settings(app)
    private val brain = ServiceLocator.brain(app)

    val state: StateFlow<HomeUiState> =
        settingsStore.settings.flatMapLatest { s ->
            val epochDay = DateUtils.today(s.dayRolloverHour)
            combine(
                repo.observeGroups(),
                repo.observeAllItems(),
                repo.observeTakenToday(epochDay),
            ) { groups, items, takenIds ->
                val taken = takenIds.toSet()
                val byGroup = items.groupBy { it.groupId }
                val cards = groups.map { g ->
                    val rows = (byGroup[g.id] ?: emptyList())
                        .filter { DateUtils.isDueOn(it, epochDay) }
                        .sortedBy { it.sortOrder }
                        .map { ItemRow(it, it.id in taken) }
                    GroupCard(g, rows)
                }.filter { it.items.isNotEmpty() }
                val focusId = pickFocus(cards)
                HomeUiState(
                    cards = cards,
                    timeline = buildTimeline(cards, focusId),
                    dayEvents = buildEvents(cards),
                    focusGroupId = focusId,
                    epochDay = epochDay,
                    loading = false,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Order the day's routines into a timeline and tag each with its status. */
    private fun buildTimeline(cards: List<GroupCard>, focusId: Long?): List<TimelineNode> {
        val nowMin = java.time.LocalTime.now().toSecondOfDay() / 60
        return cards
            .map { it to anchorMinute(it.group) }
            .sortedBy { it.second }
            .map { (c, min) ->
                val status = when {
                    c.done -> NodeStatus.DONE
                    c.group.id == focusId -> NodeStatus.NOW
                    min <= nowMin -> NodeStatus.DUE
                    else -> NodeStatus.UPCOMING
                }
                TimelineNode(c, status, anchorShort(c.group), summaryFor(c, status))
            }
    }

    /** Project the day's routines into source-agnostic events for the day graph. */
    private fun buildEvents(cards: List<GroupCard>): List<DayEvent> = cards.map { c ->
        DayEvent(
            minuteOfDay = anchorMinute(c.group),
            category = c.group.name,
            label = c.group.name,
            magnitude = c.total.toFloat(),
            done = c.done,
            color = GroupStyle.tint(c.group),
            source = EventSource.SUPPLEMENT,
        )
    }

    private fun anchorMinute(g: GroupEntity): Int = when (g.trigger) {
        TriggerType.WAKE -> 7 * 60
        TriggerType.TIME_WINDOW, TriggerType.CADENCE_DAYS ->
            runCatching { JSONObject(g.triggerConfig).optInt("startMin", 14 * 60) }.getOrDefault(14 * 60)
        TriggerType.ARRIVE_HOME, TriggerType.ARRIVE_PLACE -> 18 * 60 + 30
        TriggerType.LEAVE -> 18 * 60
        TriggerType.BEFORE_SLEEP -> 22 * 60 + 30
        TriggerType.MONTHLY -> 23 * 60
        TriggerType.MANUAL -> 12 * 60
    }

    private fun anchorShort(g: GroupEntity): String = when (g.iconKey) {
        "sun" -> "Waking"
        "droplet" -> "Afternoon"
        "home" -> "Evening"
        "moon" -> "Night"
        "calendar" -> "Monthly"
        else -> "Anytime"
    }

    private fun summaryFor(c: GroupCard, status: NodeStatus): String =
        brain.narrate(
            group = c.group,
            items = c.items.map { it.item },
            takenCount = c.takenCount,
            status = status.name,
            hour = java.time.LocalTime.now().hour,
        )

    /**
     * Choose the group that's most relevant *right now* by time of day, falling
     * back to the first one with anything left. A lightweight stand-in until the
     * real trigger engine lands — but already makes the home feel present.
     */
    private fun pickFocus(cards: List<GroupCard>): Long? {
        if (cards.isEmpty()) return null
        val nowTrigger = when (java.time.LocalTime.now().hour) {
            in 5..10 -> TriggerType.WAKE
            in 11..16 -> TriggerType.TIME_WINDOW
            in 17..20 -> TriggerType.ARRIVE_HOME
            else -> TriggerType.BEFORE_SLEEP
        }
        return (cards.firstOrNull { it.group.trigger == nowTrigger && !it.done }
            ?: cards.firstOrNull { !it.done }
            ?: cards.first()).group.id
    }

    /** The buttery check-off: confirm or undo a single item for today. */
    fun toggle(item: ItemEntity, currentlyTaken: Boolean) = viewModelScope.launch {
        val day = DateUtils.today(settingsStore.settings.first().dayRolloverHour)
        if (currentlyTaken) repo.undo(item.id, day)
        else repo.markTaken(item, day, IntakeSource.APP)
    }

    fun markGroup(groupId: Long) = viewModelScope.launch {
        val day = DateUtils.today(settingsStore.settings.first().dayRolloverHour)
        repo.markGroupTaken(groupId, day, IntakeSource.APP)
    }

    /** Preview the real reminder for a group (used to drive/test notifications). */
    fun sendReminder(groupId: Long) = viewModelScope.launch {
        Notifier.fireGroup(getApplication(), groupId)
    }
}
