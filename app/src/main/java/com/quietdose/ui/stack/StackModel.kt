package com.quietdose.ui.stack

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.FrequencyType
import com.quietdose.data.model.ItemFlags
import com.quietdose.data.model.ItemType
import com.quietdose.data.model.TriggerType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Pure mapping/serialization helpers shared by the Stack editors. Kept out of the
 * Composables so the JSON shapes for [GroupEntity.triggerConfig] live in one place
 * and exactly match what [com.quietdose.ui.home.HomeViewModel] reads back.
 */

/* ----------------------------- Icon catalogue ----------------------------- */

data class IconChoice(val key: String, val icon: ImageVector, val label: String)

/** The six glyphs the spec asks for, in the order [com.quietdose.ui.theme.GroupStyle] keys off. */
val IconChoices: List<IconChoice> = listOf(
    IconChoice("sun", Icons.Rounded.WbSunny, "Sun"),
    IconChoice("droplet", Icons.Rounded.WaterDrop, "Droplet"),
    IconChoice("home", Icons.Rounded.Home, "Home"),
    IconChoice("moon", Icons.Rounded.Bedtime, "Moon"),
    IconChoice("calendar", Icons.Rounded.CalendarMonth, "Calendar"),
    IconChoice("pill", Icons.Rounded.Medication, "Pill"),
)

/* ----------------------------- Human labels ----------------------------- */

fun TriggerType.label(): String = when (this) {
    TriggerType.WAKE -> "On waking"
    TriggerType.ARRIVE_HOME -> "Arriving home"
    TriggerType.ARRIVE_PLACE -> "Arriving at a place"
    TriggerType.LEAVE -> "Leaving"
    TriggerType.BEFORE_SLEEP -> "Before sleep"
    TriggerType.TIME_WINDOW -> "Time window"
    TriggerType.CADENCE_DAYS -> "Every N days"
    TriggerType.MONTHLY -> "Monthly"
    TriggerType.MANUAL -> "Manual"
}

fun ItemType.label(): String = when (this) {
    ItemType.CAPSULE -> "Capsule"
    ItemType.TABLET -> "Tablet"
    ItemType.SOFTGEL -> "Softgel"
    ItemType.SPRAY -> "Spray"
    ItemType.POWDER -> "Powder"
    ItemType.LIQUID -> "Liquid"
    ItemType.GUMMY -> "Gummy"
    ItemType.SUBLINGUAL -> "Sublingual"
    ItemType.OTHER -> "Other"
}

fun DoseUnit.label(): String = when (this) {
    DoseUnit.UNIT -> "units"
    DoseUnit.MG -> "mg"
    DoseUnit.MCG -> "mcg"
    DoseUnit.G -> "g"
    DoseUnit.IU -> "IU"
    DoseUnit.ML -> "ml"
    DoseUnit.DROP -> "drops"
    DoseUnit.SCOOP -> "scoops"
}

fun FrequencyType.label(): String = when (this) {
    FrequencyType.DAILY -> "Every day"
    FrequencyType.EVERY_N_DAYS -> "Every N days"
    FrequencyType.WEEKLY -> "Certain weekdays"
    FrequencyType.MONTHLY_DAYS -> "Days of month"
    FrequencyType.AS_NEEDED -> "As needed"
}

/** Flag bit + its human label, in the order [com.quietdose.util.Format.behaviour] lists them. */
val FlagChoices: List<Pair<Int, String>> = listOf(
    ItemFlags.FASTED to "fasted",
    ItemFlags.EMPTY_STOMACH to "empty stomach",
    ItemFlags.WITH_FOOD to "with food",
    ItemFlags.AVOID_CAFFEINE to "no tea/coffee",
    ItemFlags.AVOID_CALCIUM to "no calcium",
    ItemFlags.FAT_SOLUBLE to "fat-soluble",
)

val WeekdayLabels: List<String> = listOf("M", "T", "W", "T", "F", "S", "S")

/* ----------------------------- Trigger config JSON ----------------------------- */

/**
 * A parsed view over a group's [triggerConfig] blob. Only the fields relevant to the
 * current [TriggerType] are meaningful; the editor reads what it needs and emits the
 * right shape via [toJson].
 */
data class TriggerConfig(
    val startMin: Int = 14 * 60,   // TIME_WINDOW: window start (minutes since midnight)
    val endMin: Int = 16 * 60,     // TIME_WINDOW: window end
    val interval: Int = 2,         // CADENCE_DAYS: every N days
    val anchorEpochDay: Long = LocalDate.now().toEpochDay(), // CADENCE_DAYS: phase
    val monthDays: List<Int> = listOf(1), // MONTHLY: days of month
    val run: Int = 1,              // MONTHLY: how many consecutive days
) {
    fun toJson(trigger: TriggerType): String = when (trigger) {
        TriggerType.TIME_WINDOW ->
            JSONObject().put("startMin", startMin).put("endMin", endMin).toString()
        TriggerType.CADENCE_DAYS ->
            JSONObject().put("interval", interval.coerceAtLeast(1)).put("anchor", anchorEpochDay).toString()
        TriggerType.MONTHLY ->
            JSONObject()
                .put("days", JSONArray(monthDays.sorted()))
                .put("run", run.coerceAtLeast(1))
                .toString()
        else -> "{}"
    }

    companion object {
        fun parse(json: String): TriggerConfig {
            val o = runCatching { JSONObject(json) }.getOrElse { JSONObject() }
            val days = o.optJSONArray("days")?.let { arr ->
                (0 until arr.length()).map { arr.optInt(it) }.filter { it in 1..31 }
            }?.ifEmpty { null } ?: listOf(1)
            val def = TriggerConfig()
            return TriggerConfig(
                startMin = o.optInt("startMin", def.startMin),
                endMin = o.optInt("endMin", def.endMin),
                interval = o.optInt("interval", def.interval),
                anchorEpochDay = o.optLong("anchor", def.anchorEpochDay),
                monthDays = days,
                run = o.optInt("run", def.run),
            )
        }
    }
}

/** "14:00" from minutes-since-midnight, for the time-window readout. */
fun formatMinutes(min: Int): String {
    val m = ((min % (24 * 60)) + 24 * 60) % (24 * 60)
    return "%02d:%02d".format(m / 60, m % 60)
}

/* ----------------------------- Playful tint seeding ----------------------------- */

/**
 * A small, hand-picked palette of calm, desaturated tints for the "regenerate/vary"
 * control. Cycling stays inside the app's restrained world — no neon — so customizing
 * feels playful without breaking the dark, premium mood.
 */
val StackTints: List<Int> = listOf(
    0xFFE0B877.toInt(), // warm gold
    0xFFC58A78.toInt(), // clay
    0xFF8FA6F0.toInt(), // periwinkle
    0xFF9B8CE0.toInt(), // indigo
    0xFF8FB89A.toInt(), // sage
    0xFF7FB8C4.toInt(), // muted teal
    0xFFC79BCB.toInt(), // dusty mauve
    0xFFD0A0A8.toInt(), // rose
    0xFF9AA0B4.toInt(), // neutral
)

/** Deterministic "dynamic default" tint, seeded from a name so new items feel chosen. */
fun seededTint(seed: String): Int {
    if (seed.isEmpty()) return StackTints.first()
    val h = seed.fold(0) { acc, c -> acc * 31 + c.code }
    return StackTints[((h % StackTints.size) + StackTints.size) % StackTints.size]
}

/** The next tint after [current] in the catalogue — the "vary" tap. */
fun nextTint(current: Int): Int {
    val i = StackTints.indexOf(current)
    return StackTints[if (i < 0) 0 else (i + 1) % StackTints.size]
}
