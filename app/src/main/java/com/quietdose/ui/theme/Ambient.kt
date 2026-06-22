package com.quietdose.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A time-aware backdrop: the near-black canvas picks up the faintest wash of the
 * hour — warm at dawn, cool after dark — so the app feels alive without ever
 * getting loud. Returns a vertical brush meant to sit behind everything.
 */
object Ambient {

    private fun hourTint(hour: Int): Color = when (hour) {
        in 5..10 -> TintMorning   // dawn warmth
        in 11..16 -> TintIron     // daytime, faint clay
        in 17..20 -> TintEvening  // evening periwinkle
        else -> TintNight         // night indigo
    }

    fun backdrop(hour: Int): Brush = Brush.verticalGradient(
        colors = listOf(
            hourTint(hour).copy(alpha = 0.10f),
            Ink,
            Ink,
        ),
    )
}
