package com.quietdose.util

import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.ItemFlags

/** Human-friendly dose and label formatting, shared by notifications and UI. */
object Format {

    /** "2", "200 mcg", "1 mg", "5 sprays" … trimming trailing zeros. */
    fun dose(item: ItemEntity): String {
        val n = trimNumber(item.doseAmount)
        return when (item.doseUnit) {
            DoseUnit.UNIT -> when {
                item.type.name == "SPRAY" -> "$n ${plural(item.doseAmount, "spray")}"
                item.doseAmount > 1.0 -> "×$n"
                else -> "" // a single capsule/tablet needs no dose line
            }
            DoseUnit.MG -> "$n mg"
            DoseUnit.MCG -> "$n mcg"
            DoseUnit.G -> "$n g"
            DoseUnit.IU -> "$n IU"
            DoseUnit.ML -> "$n ml"
            DoseUnit.DROP -> "$n ${plural(item.doseAmount, "drop")}"
            DoseUnit.SCOOP -> "$n ${plural(item.doseAmount, "scoop")}"
        }
    }

    /** "NMN · 1" style line for a notification list. */
    fun line(item: ItemEntity): String {
        val d = dose(item)
        return if (d.isBlank() || d == "1") item.name else "${item.name} · $d"
    }

    /** Short behavioural tags from the item's flags — the on-screen intelligence. */
    fun behaviour(item: ItemEntity): List<String> {
        val f = item.flags
        val out = mutableListOf<String>()
        if (f and ItemFlags.FASTED != 0) out += "fasted"
        if (f and ItemFlags.EMPTY_STOMACH != 0) out += "empty stomach"
        if (f and ItemFlags.WITH_FOOD != 0) out += "with food"
        if (f and ItemFlags.AVOID_CAFFEINE != 0) out += "no tea/coffee"
        if (f and ItemFlags.AVOID_CALCIUM != 0) out += "no calcium"
        if (f and ItemFlags.FAT_SOLUBLE != 0) out += "fat-soluble"
        return out
    }

    private fun plural(amount: Double, word: String): String =
        if (amount == 1.0) word else "${word}s"

    private fun trimNumber(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}
