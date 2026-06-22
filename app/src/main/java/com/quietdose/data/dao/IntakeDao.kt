package com.quietdose.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quietdose.data.entity.IntakeLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IntakeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: IntakeLogEntity): Long

    /** Item ids already confirmed for a given local day — powers done-state. */
    @Query("SELECT itemId FROM intake_log WHERE epochDay = :epochDay")
    fun observeTakenItemIds(epochDay: Long): Flow<List<Long>>

    @Query("SELECT itemId FROM intake_log WHERE epochDay = :epochDay")
    suspend fun takenItemIds(epochDay: Long): List<Long>

    @Query("SELECT COUNT(*) FROM intake_log WHERE itemId = :itemId AND epochDay = :epochDay")
    suspend fun countFor(itemId: Long, epochDay: Long): Int

    @Query("DELETE FROM intake_log WHERE itemId = :itemId AND epochDay = :epochDay")
    suspend fun undo(itemId: Long, epochDay: Long)

    @Query("SELECT * FROM intake_log WHERE epochDay BETWEEN :from AND :to ORDER BY takenAtEpochMs DESC")
    fun observeRange(from: Long, to: Long): Flow<List<IntakeLogEntity>>
}
