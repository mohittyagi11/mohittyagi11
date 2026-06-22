package com.quietdose.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.quietdose.data.model.TriggerType

/**
 * A group (a.k.a. routine / category) — the bucket of items that surface
 * together on one trigger. Groups are user-creatable and reorderable; the
 * "gamified", fun-to-customize config is built on top of these rows.
 *
 * [triggerConfig] is a small JSON blob whose shape depends on [trigger] — e.g.
 * a TIME_WINDOW group stores `{"startMin":840,"endMin":960}`, a CADENCE_DAYS
 * group stores `{"interval":2,"anchor":20300}` (epoch day), MONTHLY stores
 * `{"days":[1],"run":3}`. Keeping it as JSON lets new trigger kinds ship
 * without a schema migration.
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val trigger: TriggerType,
    val triggerConfig: String = "{}",
    /** Optional override accent (ARGB) for a splash of personality; null = app accent. */
    val accentArgb: Int? = null,
    /** Icon key resolved by the UI (e.g. "sun", "home", "moon", "pill"). */
    val iconKey: String = "pill",
    /** Quiet groups whisper (low-importance notifications); critical ones speak up. */
    val quiet: Boolean = false,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
)
