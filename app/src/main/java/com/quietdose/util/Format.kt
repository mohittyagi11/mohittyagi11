package com.quietdose.util

import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.DoseUnit

/** Human-friendly dose and label formatting, shared by notifications and UI. */
object Format {

    /** "2", "200 mcg", "1 mg", "5 sprays" … trimming trailing zeros. */
    fun dose(item: ItemEntity): String {
        val n = trimNumber(item.doseAmount)
        return when (item.doseUnit) {
            DoseUnit.UNIT -> when (item.type.name) {
                "SPRAY" -> "$n ${plural(item.doseAmount, "spray")}"
                else -> n
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

    private fun plural(amount: Double, word: String): String =
        if (amount == 1.0) word else "${word}s"

    private fun trimNumber(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}
