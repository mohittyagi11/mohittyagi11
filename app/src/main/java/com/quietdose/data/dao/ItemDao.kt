package com.quietdose.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.quietdose.data.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE enabled = 1 ORDER BY sortOrder, id")
    fun observeEnabled(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE groupId = :groupId AND enabled = 1 ORDER BY sortOrder, id")
    fun observeForGroup(groupId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE groupId = :groupId AND enabled = 1 ORDER BY sortOrder, id")
    suspend fun forGroup(groupId: Long): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun byId(id: Long): ItemEntity?

    /** Items whose stock has dropped to or below their low-stock threshold. */
    @Query("SELECT * FROM items WHERE stockCount IS NOT NULL AND stockCount <= lowStockThreshold AND enabled = 1")
    fun observeLowStock(): Flow<List<ItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Delete
    suspend fun delete(item: ItemEntity)

    @Query("UPDATE items SET stockCount = stockCount - :units WHERE id = :id AND stockCount IS NOT NULL")
    suspend fun decrementStock(id: Long, units: Double)
}
