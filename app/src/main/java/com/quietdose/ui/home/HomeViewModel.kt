package com.quietdose.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.IntakeSource
import com.quietdose.di.ServiceLocator
import com.quietdose.notify.Notifier
import com.quietdose.util.DateUtils
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

data class HomeUiState(
    val cards: List<GroupCard> = emptyList(),
    val epochDay: Long = 0,
    val loading: Boolean = true,
) {
    val allDone: Boolean get() = cards.isNotEmpty() && cards.all { it.done }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.repository(app)
    private val settingsStore = ServiceLocator.settings(app)

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
                HomeUiState(cards, epochDay, loading = false)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

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
