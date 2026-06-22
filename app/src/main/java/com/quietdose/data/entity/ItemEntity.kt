package com.quietdose.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.FrequencyType
import com.quietdose.data.model.ItemType

/**
 * A single supplement. Everything about it is data: which group it surfaces in,
 * its form and dose, how often it's due, behavioural flags, a pairing hint, a
 * restock link, and live stock tracking.
 */
@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,

    val name: String,
    val brand: String? = null,
    /** Free-text category/tag for the customizer's filtering ("Longevity", "Sleep"…). */
    val category: String? = null,
    val type: ItemType = ItemType.CAPSULE,

    // Dose
    val doseAmount: Double = 1.0,
    val doseUnit: DoseUnit = DoseUnit.UNIT,

    // Frequency — *which days*; the group's trigger decides *when in the day*.
    val frequency: FrequencyType = FrequencyType.DAILY,
    val frequencyInterval: Int = 1,    // N for EVERY_N_DAYS
    val frequencyAnchorEpochDay: Long = 0, // phase for EVERY_N_DAYS
    val frequencyDaysMask: Int = 0,    // WEEKLY bitmask (bit0 = Monday)
    val frequencyDaysOfMonth: String = "", // MONTHLY_DAYS, comma-separated

    // Behaviour
    val flags: Int = 0,                // ItemFlags bitmask
    val note: String? = null,
    val pairWithItemId: Long? = null,  // co-locate / take together hint

    // Restock
    val purchaseUrl: String? = null,
    val stockCount: Double? = null,    // remaining units; null = not tracked
    val unitsPerDose: Double = 1.0,
    val lowStockThreshold: Double = 7.0, // warn at ~a week left

    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val createdAtEpochMs: Long = 0,
)
