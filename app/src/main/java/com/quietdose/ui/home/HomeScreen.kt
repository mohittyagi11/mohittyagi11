package com.quietdose.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quietdose.ui.theme.Accent
import com.quietdose.ui.theme.Done
import com.quietdose.ui.theme.Outline
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid
import com.quietdose.util.Format
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(modifier: Modifier = Modifier, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header(allDone = state.allDone) }

        items(state.cards, key = { it.group.id }) { card ->
            GroupCardView(
                card = card,
                onToggle = { row -> vm.toggle(row.item, row.taken) },
                onRemind = { vm.sendReminder(card.group.id) },
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Header(allDone: Boolean) {
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM")) }
    Column(Modifier.padding(top = 32.dp, bottom = 6.dp)) {
        Text("Today", style = MaterialTheme.typography.displaySmall, color = TextHigh)
        Text(
            if (allDone) "All done — nothing left to take." else today,
            style = MaterialTheme.typography.bodyLarge,
            color = if (allDone) Done else TextMid,
        )
    }
}

@Composable
private fun GroupCardView(
    card: GroupCard,
    onToggle: (ItemRow) -> Unit,
    onRemind: () -> Unit,
) {
    Surface(
        color = Surface1,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.group.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextHigh,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${card.takenCount}/${card.total}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (card.done) Done else TextLow,
                )
            }
            Spacer(Modifier.height(10.dp))
            card.items.forEach { row ->
                DoseRow(row = row, onToggle = { onToggle(row) })
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Remind me",
                style = MaterialTheme.typography.labelLarge,
                color = Accent,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onRemind() }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

/**
 * The hero interaction: a single, satisfying tap. The check springs in, the
 * row settles to a calm "done" state, and a crisp haptic confirms it — no
 * confetti, no streak counter.
 */
@Composable
private fun DoseRow(row: ItemRow, onToggle: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            }
            .padding(vertical = 10.dp),
    ) {
        CheckCircle(checked = row.taken)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (row.taken) TextLow else TextHigh,
                textDecoration = if (row.taken) TextDecoration.LineThrough else null,
            )
            val dose = Format.dose(row.item)
            if (dose.isNotBlank() && dose != "1") {
                Text(dose, style = MaterialTheme.typography.bodyMedium, color = TextMid)
            }
        }
    }
}

@Composable
private fun CheckCircle(checked: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "checkScale",
    )
    val fill by animateColorAsState(
        targetValue = if (checked) Accent else Surface1,
        label = "checkFill",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(26.dp)
            .border(
                width = 1.5.dp,
                color = if (checked) Accent else Outline,
                shape = CircleShape,
            )
            .background(fill, CircleShape),
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = if (checked) "Taken" else "Mark taken",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(16.dp)
                .scale(scale)
                .graphicsLayer { alpha = scale },
        )
    }
}
