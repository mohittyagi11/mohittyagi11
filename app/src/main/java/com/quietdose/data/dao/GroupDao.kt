package com.quietdose.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.model.TriggerType
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups WHERE enabled = 1 ORDER BY sortOrder, id")
    fun observeEnabled(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun byId(id: Long): GroupEntity?

    @Query("SELECT * FROM groups WHERE trigger = :trigger AND enabled = 1 ORDER BY sortOrder, id")
    suspend fun byTrigger(trigger: TriggerType): List<GroupEntity>

    @Query("SELECT COUNT(*) FROM groups")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Delete
    suspend fun delete(group: GroupEntity)
}
