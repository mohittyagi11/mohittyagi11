package com.azadishashn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.azadishashn.app.ui.theme.Gold

/** Round/turn progress dots: filled = done, ringed = current, faint = upcoming. */
@Composable
fun TurnProgress(total: Int, current: Int, modifier: Modifier = Modifier) {
    if (total <= 0) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { i ->
            val done = i < current - 1
            val isCurrent = i == current - 1
            when {
                isCurrent -> Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .border(2.dp, Gold, CircleShape),
                )
                done -> Box(Modifier.size(8.dp).clip(CircleShape).background(Gold))
                else -> Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                )
            }
        }
    }
}
