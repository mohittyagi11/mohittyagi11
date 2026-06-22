package com.quietdose.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.quietdose.data.entity.GroupEntity

/**
 * Maps a group to its calm visual identity — one tint + one glyph per routine.
 * A group's own [GroupEntity.accentArgb] override wins; otherwise we key off the
 * icon key (and fall back to a neutral tint for custom, user-made groups).
 */
object GroupStyle {

    fun tint(group: GroupEntity): Color =
        group.accentArgb?.let { Color(it) } ?: when (group.iconKey) {
            "sun" -> TintMorning
            "droplet" -> TintIron
            "home" -> TintEvening
            "moon" -> TintNight
            "calendar" -> TintMonthly
            else -> TintNeutral
        }

    fun icon(group: GroupEntity): ImageVector = when (group.iconKey) {
        "sun" -> Icons.Rounded.WbSunny
        "droplet" -> Icons.Rounded.WaterDrop
        "home" -> Icons.Rounded.Home
        "moon" -> Icons.Rounded.Bedtime
        "calendar" -> Icons.Rounded.CalendarMonth
        else -> Icons.Rounded.Medication
    }

    /** A short, human "when" hint for the hero card. */
    fun whenLabel(group: GroupEntity): String = when (group.iconKey) {
        "sun" -> "On waking · fasted"
        "droplet" -> "Mid-afternoon"
        "home" -> "With the evening meal"
        "moon" -> "Before sleep"
        "calendar" -> "Monthly"
        else -> ""
    }
}
