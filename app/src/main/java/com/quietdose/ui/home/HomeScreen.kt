package com.quietdose.ui.home

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quietdose.ui.theme.Done
import com.quietdose.ui.theme.GroupStyle
import com.quietdose.data.model.ItemType
import com.quietdose.ui.icons.ItemIcon
import com.quietdose.ui.theme.Ink
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
        item { Header(taken = state.totalTaken, due = state.totalDue, allDone = state.allDone) }

        state.focus?.let { focus ->
            item(key = "focus-${focus.group.id}") {
                GroupCard(
                    card = focus,
                    hero = true,
                    onToggle = { row -> vm.toggle(row.item, row.taken) },
                    onMarkAll = { vm.markGroup(focus.group.id) },
                    onRemind = { vm.sendReminder(focus.group.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (state.rest.isNotEmpty()) {
            item { SectionLabel("Also today") }
        }
        items(state.rest, key = { it.group.id }) { card ->
            GroupCard(
                card = card,
                hero = false,
                onToggle = { row -> vm.toggle(row.item, row.taken) },
                onMarkAll = { vm.markGroup(card.group.id) },
                onRemind = { vm.sendReminder(card.group.id) },
                modifier = Modifier.animateItem(),
            )
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun Header(taken: Int, due: Int, allDone: Boolean) {
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM")) }
    Column(Modifier.padding(top = 36.dp, bottom = 4.dp)) {
        Text("Today", style = MaterialTheme.typography.displaySmall, color = TextHigh)
        Text(
            when {
                allDone -> "All done — nothing left to take."
                due == 0 -> today
                else -> "$today · $taken of $due taken"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (allDone) Done else TextMid,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextLow,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun GroupCard(
    card: GroupCard,
    hero: Boolean,
    onToggle: (ItemRow) -> Unit,
    onMarkAll: () -> Unit,
    onRemind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = GroupStyle.tint(card.group)
    Surface(
        color = Surface1,
        shape = RoundedCornerShape(if (hero) 24.dp else 20.dp),
        modifier = modifier
            .fillMaxWidth()
            // The hero gets a whisper of its tint as a hairline so it reads as
            // "now" without breaking the calm.
            .then(
                if (hero) Modifier.border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                else Modifier,
            ),
    ) {
        Column(Modifier.padding(if (hero) 20.dp else 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupGlyph(tint = tint, hero = hero, group = card)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        card.group.name,
                        style = if (hero) MaterialTheme.typography.titleLarge
                        else MaterialTheme.typography.titleMedium,
                        color = TextHigh,
                    )
                    if (hero) {
                        val ctx = GroupStyle.whenLabel(card.group)
                        if (ctx.isNotBlank()) {
                            Text(ctx, style = MaterialTheme.typography.bodyMedium, color = TextMid)
                        }
                    }
                }
                Text(
                    "${card.takenCount}/${card.total}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (card.done) Done else tint,
                )
            }

            Spacer(Modifier.height(if (hero) 14.dp else 10.dp))
            card.items.forEach { row ->
                DoseRow(row = row, tint = tint, onToggle = { onToggle(row) })
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!card.done) {
                    TextAction("Mark all", tint, onMarkAll)
                    Spacer(Modifier.size(20.dp))
                }
                TextAction("Remind me", TextMid, onRemind)
            }
        }
    }
}

@Composable
private fun GroupGlyph(tint: Color, hero: Boolean, group: GroupCard) {
    val s = if (hero) 40.dp else 34.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(s)
            .background(tint.copy(alpha = 0.16f), CircleShape),
    ) {
        Icon(
            GroupStyle.icon(group.group),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(if (hero) 22.dp else 18.dp),
        )
    }
}

@Composable
private fun TextAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(vertical = 6.dp),
    )
}

/**
 * The hero interaction: a single, satisfying tap. The check springs in, the
 * row settles to a calm "done" state, and a crisp haptic confirms it — no
 * confetti, no streak counter. Now coloured by the group's own tint.
 */
@Composable
private fun DoseRow(row: ItemRow, tint: Color, onToggle: () -> Unit) {
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
        ItemLeading(type = row.item.type, tint = tint, checked = row.taken)
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

/**
 * The procedural pill icon doubles as the status: tap the row and a tinted
 * "done" badge springs onto the icon, which softens back. Recognition + state
 * in one calm element.
 */
@Composable
private fun ItemLeading(type: ItemType, tint: Color, checked: Boolean) {
    val iconAlpha by animateFloatAsState(
        targetValue = if (checked) 0.4f else 1f,
        label = "iconAlpha",
    )
    val badge by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "badge",
    )
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        ItemIcon(
            type = type,
            tint = tint,
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer { alpha = iconAlpha },
        )
        if (badge > 0f) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = badge; scaleY = badge; alpha = badge
                    }
                    .background(tint, CircleShape),
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Taken",
                    tint = Ink,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
