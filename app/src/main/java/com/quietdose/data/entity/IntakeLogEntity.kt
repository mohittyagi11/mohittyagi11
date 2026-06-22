package com.quietdose.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.quietdose.data.model.IntakeSource

/**
 * A confirmation that something was taken. We log at item granularity but also
 * record the group, so "mark whole group taken" writes one row per item and the
 * home screen can show per-item or per-group done-state cheaply.
 *
 * [epochDay] is the local day the dose belongs to (so a 1 AM night dose still
 * counts for the right day per the app's day-rollover setting).
 */
@Entity(
    tableName = "intake_log",
    indices = [Index("epochDay"), Index("itemId"), Index("groupId")],
)
data class IntakeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val groupId: Long,
    val epochDay: Long,
    val takenAtEpochMs: Long,
    val source: IntakeSource = IntakeSource.APP,
)
