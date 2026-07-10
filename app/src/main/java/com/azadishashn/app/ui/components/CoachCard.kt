package com.azadishashn.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.data.Lessons

/** Coach cards shown per screen visit before the rest wait for their next occurrence. */
private const val COACH_LIMIT = 2

/**
 * A one-time, first-encounter explainer for a house rule, rendered inline next
 * to the mechanic it teaches. The game teaches itself: each card appears the
 * FIRST time its rule fires, lists every branch with real numbers, and "Got it"
 * dismisses it forever (device-wide). [budget] caps how many cards a single
 * screen visit can show (the rest surface on their next natural occurrence),
 * except [force]d moments where the choice is NOW (whip envelope, a breaking
 * crisis, a scandal). [highlight] tints the branch that actually happened.
 */
@Composable
fun CoachMark(
    lessonId: String,
    hasSeen: (String) -> Boolean,
    markSeen: (String) -> Unit,
    budget: MutableIntState,
    modifier: Modifier = Modifier,
    highlight: Int = -1,
    force: Boolean = false,
) {
    val lesson = Lessons.byId(lessonId) ?: return
    // Claim a slot once per screen visit; a claimed card keeps it so it doesn't
    // vanish when a sibling is dismissed.
    val claimed = remember(lessonId) {
        when {
            hasSeen(lessonId) -> false
            force -> true
            budget.intValue >= COACH_LIMIT -> false
            else -> {
                budget.intValue++
                true
            }
        }
    }
    if (!claimed || hasSeen(lessonId)) return

    val tint = MaterialTheme.colorScheme.tertiary
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        SectionCard(accent = tint) {
            Text(
                "💡 NEW RULE · ${lesson.title.uppercase()}",
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
            Spacer(Modifier.height(4.dp))
            Text(lesson.scenario, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            lesson.branches.forEachIndexed { i, b ->
                val hot = i == highlight
                Column(Modifier.padding(vertical = 3.dp)) {
                    Text(
                        if (hot) "${b.label}  ← this time" else b.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hot) tint else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        b.outcome,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(
                onClick = { markSeen(lessonId) },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Got it") }
        }
    }
}
