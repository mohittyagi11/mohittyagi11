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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quietdose.data.model.ItemType
import com.quietdose.ui.components.DayRing
import com.quietdose.ui.icons.ItemIcon
import com.quietdose.ui.theme.Done
import com.quietdose.ui.theme.GroupStyle
import com.quietdose.ui.theme.Ink
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.Surface2
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid
import com.quietdose.ui.theme.TintNeutral
import com.quietdose.util.Format
import java.time.LocalTime

@Composable
fun HomeScreen(modifier: Modifier = Modifier, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focusTint = state.focus?.let { GroupStyle.tint(it.group) } ?: TintNeutral

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DayHero(
                taken = state.totalTaken,
                due = state.totalDue,
                tint = focusTint,
                allDone = state.allDone,
                statusLine = statusLine(state),
            )
        }

        state.focus?.let { focus ->
            item(key = "focus-${focus.group.id}") {
                FeaturedCard(
                    card = focus,
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
            CompactCard(
                card = card,
                onToggle = { row -> vm.toggle(row.item, row.taken) },
                onMarkAll = { vm.markGroup(card.group.id) },
                modifier = Modifier.animateItem(),
            )
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

private fun statusLine(state: HomeUiState): String {
    if (state.due0()) return ""
    if (state.allDone) return "You're all set for today."
    val left = state.totalDue - state.totalTaken
    val now = state.focus?.group?.name
    return if (now != null) "$left left · $now now" else "$left left today"
}

private fun HomeUiState.due0(): Boolean = totalDue == 0

@Composable
private fun DayHero(taken: Int, due: Int, tint: Color, allDone: Boolean, statusLine: String) {
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Tonight"
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.titleMedium, color = TextMid)
            Text("Today", style = MaterialTheme.typography.displaySmall, color = TextHigh)
            if (statusLine.isNotBlank()) {
                Text(
                    statusLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (allDone) Done else TextMid,
                )
            }
        }
        Box(Modifier.size(86.dp), contentAlignment = Alignment.Center) {
            DayRing(taken = taken, due = due, tint = tint, modifier = Modifier.fillMaxSize())
            if (allDone) {
                Icon(Icons.Rounded.Check, contentDescription = "All done", tint = tint, modifier = Modifier.size(30.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(due - taken).coerceAtLeast(0)}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextHigh,
                    )
                    Text("left", style = MaterialTheme.typography.labelSmall, color = TextLow)
                }
            }
        }
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

/** The centrepiece: the routine that's relevant now, rendered with weight. */
@Composable
private fun FeaturedCard(
    card: GroupCard,
    onToggle: (ItemRow) -> Unit,
    onMarkAll: () -> Unit,
    onRemind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = GroupStyle.tint(card.group)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(listOf(tint.copy(alpha = 0.16f), Surface1, Surface1)),
            )
            .border(1.dp, tint.copy(alpha = 0.4f), RoundedCornerShape(26.dp))
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupGlyph(tint = tint, group = card.group, size = 44.dp)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.group.name, style = MaterialTheme.typography.titleLarge, color = TextHigh)
                    val ctx = GroupStyle.whenLabel(card.group)
                    if (ctx.isNotBlank()) {
                        Text(ctx, style = MaterialTheme.typography.bodyMedium, color = TextMid)
                    }
                }
                Text(
                    "${card.takenCount}/${card.total}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (card.done) Done else tint,
                )
            }

            Spacer(Modifier.height(16.dp))
            card.items.forEach { row ->
                DoseRow(row = row, tint = tint, showChips = true, onToggle = { onToggle(row) })
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!card.done) {
                    PrimaryAction("Take all", tint, Modifier.weight(1f), onMarkAll)
                    Spacer(Modifier.width(12.dp))
                }
                GhostAction("Remind", onRemind)
            }
        }
    }
}

@Composable
private fun CompactCard(
    card: GroupCard,
    onToggle: (ItemRow) -> Unit,
    onMarkAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = GroupStyle.tint(card.group)
    Surface(
        color = Surface1,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupGlyph(tint = tint, group = card.group, size = 34.dp)
                Spacer(Modifier.size(12.dp))
                Text(
                    card.group.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHigh,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${card.takenCount}/${card.total}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (card.done) Done else tint,
                )
            }
            Spacer(Modifier.height(10.dp))
            card.items.forEach { row ->
                DoseRow(row = row, tint = tint, showChips = false, onToggle = { onToggle(row) })
            }
            if (!card.done) {
                Spacer(Modifier.height(4.dp))
                GhostAction("Mark all", onMarkAll, color = tint)
            }
        }
    }
}

@Composable
private fun GroupGlyph(tint: Color, group: com.quietdose.data.entity.GroupEntity, size: androidx.compose.ui.unit.Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).background(tint.copy(alpha = 0.16f), CircleShape),
    ) {
        Icon(
            GroupStyle.icon(group),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

@Composable
private fun PrimaryAction(label: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.22f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

@Composable
private fun GhostAction(label: String, onClick: () -> Unit, color: Color = TextMid) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/**
 * The hero interaction: a single, satisfying tap. The procedural pill icon
 * doubles as status — a tinted "done" badge springs on, the row settles, a
 * crisp haptic confirms it. No confetti, no streak counter.
 */
@Composable
private fun DoseRow(row: ItemRow, tint: Color, showChips: Boolean, onToggle: () -> Unit) {
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
            .padding(vertical = 9.dp),
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
            val chips = if (showChips) Format.behaviour(row.item) else emptyList()
            if (dose.isNotBlank() || chips.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (dose.isNotBlank()) Chip(dose, tint, solid = true)
                    chips.take(2).forEach { Chip(it, tint, solid = false) }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, tint: Color, solid: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (solid) tint.copy(alpha = 0.16f) else Surface2)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (solid) tint else TextMid,
        )
    }
}

@Composable
private fun ItemLeading(type: ItemType, tint: Color, checked: Boolean) {
    val iconAlpha by animateFloatAsState(if (checked) 0.4f else 1f, label = "iconAlpha")
    val badge by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "badge",
    )
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        ItemIcon(
            type = type,
            tint = tint,
            modifier = Modifier.size(34.dp).graphicsLayer { alpha = iconAlpha },
        )
        if (badge > 0f) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .graphicsLayer { scaleX = badge; scaleY = badge; alpha = badge }
                    .background(tint, CircleShape),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = "Taken", tint = Ink, modifier = Modifier.size(12.dp))
            }
        }
    }
}
