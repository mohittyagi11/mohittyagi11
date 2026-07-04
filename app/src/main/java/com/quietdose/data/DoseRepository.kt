package com.quietdose.data

import com.quietdose.data.dao.GroupDao
import com.quietdose.data.dao.IntakeDao
import com.quietdose.data.dao.ItemDao
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.IntakeLogEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.IntakeSource
import com.quietdose.util.DateUtils
import kotlinx.coroutines.flow.Flow

/**
 * The single read/write surface over the data layer. ViewModels, the scheduler,
 * notification receivers and the brain all go through here so business rules
 * (stock decrement on confirm, day-rollover) live in one place.
 */
class DoseRepository(
    private val groupDao: GroupDao,
    private val itemDao: ItemDao,
    private val intakeDao: IntakeDao,
) {
    fun observeGroups(): Flow<List<GroupEntity>> = groupDao.observeEnabled()
    fun observeAllItems(): Flow<List<ItemEntity>> = itemDao.observeEnabled()
    fun observeItems(groupId: Long): Flow<List<ItemEntity>> = itemDao.observeForGroup(groupId)
    fun observeLowStock(): Flow<List<ItemEntity>> = itemDao.observeLowStock()
    fun observeTakenToday(epochDay: Long): Flow<List<Long>> = intakeDao.observeTakenItemIds(epochDay)

    suspend fun group(id: Long): GroupEntity? = groupDao.byId(id)
    suspend fun item(id: Long): ItemEntity? = itemDao.byId(id)
    suspend fun itemsFor(groupId: Long): List<ItemEntity> = itemDao.forGroup(groupId)

    /** Confirm a single item, honouring day-rollover and decrementing stock. */
    suspend fun markTaken(
        item: ItemEntity,
        epochDay: Long,
        source: IntakeSource,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (intakeDao.countFor(item.id, epochDay) > 0) return // idempotent
        intakeDao.insert(
            IntakeLogEntity(
                itemId = item.id,
                groupId = item.groupId,
                epochDay = epochDay,
                takenAtEpochMs = nowMs,
                source = source,
            ),
        )
        if (item.stockCount != null) itemDao.decrementStock(item.id, item.unitsPerDose)
    }

    /** Confirm a whole group at once — what the notification's one tap does. */
    suspend fun markGroupTaken(groupId: Long, epochDay: Long, source: IntakeSource) {
        itemsFor(groupId)
            .filter { DateUtils.isDueOn(it, epochDay) }
            .forEach { markTaken(it, epochDay, source) }
    }

    suspend fun undo(itemId: Long, epochDay: Long) = intakeDao.undo(itemId, epochDay)

    // Customizer writes
    suspend fun upsertGroup(group: GroupEntity): Long = groupDao.upsert(group)
    suspend fun upsertItem(item: ItemEntity): Long = itemDao.upsert(item)
    suspend fun deleteGroup(group: GroupEntity) = groupDao.delete(group)
    suspend fun deleteItem(item: ItemEntity) = itemDao.delete(item)
}
