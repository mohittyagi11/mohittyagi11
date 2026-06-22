package com.quietdose.ui.stack

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A group plus the items that live inside it, sorted for display. */
data class StackGroup(
    val group: GroupEntity,
    val items: List<ItemEntity>,
)

data class StackUiState(
    val groups: List<StackGroup> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = !loading && groups.isEmpty()
}

/**
 * Read/write surface for the Stack ("config") tab. Mirrors [com.quietdose.ui.home.HomeViewModel]
 * in shape — observes the repo into a single [StateFlow] and exposes small, intent-named
 * mutators that fire-and-forget into [viewModelScope]. All the customizer's writes route
 * through [com.quietdose.data.DoseRepository], so business rules stay in one place.
 */
class StackViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.repository(app)

    val state: StateFlow<StackUiState> =
        combine(repo.observeGroups(), repo.observeAllItems()) { groups, items ->
            val byGroup = items.groupBy { it.groupId }
            val rows = groups
                .sortedBy { it.sortOrder }
                .map { g ->
                    StackGroup(
                        group = g,
                        items = (byGroup[g.id] ?: emptyList()).sortedBy { it.sortOrder },
                    )
                }
            StackUiState(groups = rows, loading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StackUiState())

    /* ----------------------------- Groups ----------------------------- */

    /** Insert a brand-new group, parking it after every existing one. */
    fun addGroup(group: GroupEntity) = viewModelScope.launch {
        val nextSort = (state.value.groups.maxOfOrNull { it.group.sortOrder } ?: -1) + 1
        repo.upsertGroup(group.copy(sortOrder = nextSort))
    }

    /** Persist edits to an existing group (id preserved). */
    fun saveGroup(group: GroupEntity) = viewModelScope.launch {
        repo.upsertGroup(group)
    }

    fun deleteGroup(group: GroupEntity) = viewModelScope.launch {
        repo.deleteGroup(group)
    }

    /** Move a group up or down, swapping sortOrder with its neighbour. */
    fun moveGroup(groupId: Long, up: Boolean) = viewModelScope.launch {
        val ordered = state.value.groups.map { it.group }
        val index = ordered.indexOfFirst { it.id == groupId }
        if (index < 0) return@launch
        val target = if (up) index - 1 else index + 1
        if (target !in ordered.indices) return@launch
        val a = ordered[index]
        val b = ordered[target]
        repo.upsertGroup(a.copy(sortOrder = b.sortOrder))
        repo.upsertGroup(b.copy(sortOrder = a.sortOrder))
    }

    /* ------------------------------ Items ------------------------------ */

    /** Insert a new item into [groupId], appending it to that group's list. */
    fun addItem(item: ItemEntity) = viewModelScope.launch {
        val siblings = state.value.groups.firstOrNull { it.group.id == item.groupId }?.items
        val nextSort = (siblings?.maxOfOrNull { it.sortOrder } ?: -1) + 1
        repo.upsertItem(
            item.copy(
                sortOrder = nextSort,
                createdAtEpochMs = if (item.createdAtEpochMs == 0L) System.currentTimeMillis() else item.createdAtEpochMs,
            ),
        )
    }

    fun saveItem(item: ItemEntity) = viewModelScope.launch {
        repo.upsertItem(item)
    }

    fun deleteItem(item: ItemEntity) = viewModelScope.launch {
        repo.deleteItem(item)
    }

    /** Reorder an item within its group by swapping sortOrder with a neighbour. */
    fun moveItem(item: ItemEntity, up: Boolean) = viewModelScope.launch {
        val siblings = state.value.groups
            .firstOrNull { it.group.id == item.groupId }?.items ?: return@launch
        val index = siblings.indexOfFirst { it.id == item.id }
        if (index < 0) return@launch
        val target = if (up) index - 1 else index + 1
        if (target !in siblings.indices) return@launch
        val a = siblings[index]
        val b = siblings[target]
        repo.upsertItem(a.copy(sortOrder = b.sortOrder))
        repo.upsertItem(b.copy(sortOrder = a.sortOrder))
    }
}
