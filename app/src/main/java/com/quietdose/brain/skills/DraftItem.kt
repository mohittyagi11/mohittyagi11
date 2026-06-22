package com.quietdose.brain.skills

import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.ItemType

/**
 * A model-produced draft of a supplement — what [IdentifyProductSkill] returns
 * from a name or a scanned label. Maps cleanly onto the fields the item editor
 * pre-fills; the user always confirms before it's saved.
 */
data class DraftItem(
    val name: String,
    val brand: String? = null,
    val category: String? = null,
    val type: ItemType = ItemType.CAPSULE,
    val doseAmount: Double = 1.0,
    val doseUnit: DoseUnit = DoseUnit.UNIT,
    val flags: Int = 0,
    val note: String? = null,
    /**
     * When to use it, in plain words — for applied items (skincare/haircare/device)
     * the question is "morning / evening", not an mg dose. Optional and defaulted so
     * the supplement flow and every existing caller are untouched. The confirm screen
     * pre-fills this and the user can change it.
     */
    val timing: UseTiming = UseTiming.ANYTIME,
)

/** When an applied item is used across the day. Plain language, no clock times. */
enum class UseTiming(val label: String) {
    MORNING("Morning"),
    EVENING("Evening"),
    BOTH("Morning & evening"),
    ANYTIME("Anytime"),
}
