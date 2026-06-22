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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.ItemType
import com.quietdose.ui.analysis.StoredProductGlyph
import com.quietdose.ui.analysis.hasStoredLook
import com.quietdose.ui.components.DayRing
import com.quietdose.ui.icons.ItemIcon
import com.quietdose.ui.theme.AccentSoft
import com.quietdose.ui.theme.Done
import com.quietdose.ui.theme.GroupStyle
import com.quietdose.ui.theme.Ink
import com.quietdose.ui.theme.Outline
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.Surface2
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid
import com.quietdose.ui.theme.TintNeutral
import com.quietdose.util.Format
import java.time.LocalTime

private enum class Mode { Timeline, Checklist }

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    vm: HomeViewModel = viewModel(),
    onOpenSettings: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focusTint = state.focus?.let { GroupStyle.tint(it.group) } ?: TintNeutral
    var mode by remember { mutableStateOf(Mode.Timeline) }
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SlimHeader(
                taken = state.totalTaken,
                due = state.totalDue,
                tint = focusTint,
                allDone = state.allDone,
                statusLine = statusLine(state),
                onOpenSettings = onOpenSettings,
            )
        }
        item {
            ModeToggle(mode = mode, onChange = { mode = it }, modifier = Modifier.padding(vertical = 6.dp))
        }

        when (mode) {
            Mode.Timeline -> itemsIndexed(state.timeline, key = { _, n -> n.card.group.id }) { i, node ->
                val id = node.card.group.id
                val open = expanded[id] ?: (node.status == NodeStatus.NOW)
                TimelineNodeView(
                    node = node,
                    isFirst = i == 0,
                    isLast = i == state.timeline.lastIndex,
                    expanded = open,
                    onToggleExpand = { expanded[id] = !open },
                    onToggleItem = { row -> vm.toggle(row.item, row.taken) },
                    onMarkAll = { vm.markGroup(id) },
                    onRemind = { vm.sendReminder(id) },
                )
            }

            Mode.Checklist -> items(state.cards, key = { it.group.id }) { card ->
                CompactCard(
                    card = card,
                    onToggle = { row -> vm.toggle(row.item, row.taken) },
                    onMarkAll = { vm.markGroup(card.group.id) },
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun statusLine(state: HomeUiState): String {
    if (state.totalDue == 0) return ""
    if (state.allDone) return "You're all set for today."
    val left = state.totalDue - state.totalTaken
    val now = state.focus?.group?.name
    return if (now != null) "$left left · $now now" else "$left left today"
}

@Composable
private fun SlimHeader(
    taken: Int,
    due: Int,
    tint: Color,
    allDone: Boolean,
    statusLine: String,
    onOpenSettings: () -> Unit,
) {
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
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 2.dp),
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
        Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
            DayRing(taken = taken, due = due, tint = tint, modifier = Modifier.fillMaxSize())
            if (allDone) {
                Icon(Icons.Rounded.Check, contentDescription = "All done", tint = tint, modifier = Modifier.size(24.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(due - taken).coerceAtLeast(0)}", style = MaterialTheme.typography.titleLarge, color = TextHigh)
                    Text("left", style = MaterialTheme.typography.labelSmall, color = TextLow)
                }
            }
        }
        Spacer(Modifier.size(10.dp))
        Icon(
            Icons.Rounded.Settings,
            contentDescription = "Settings",
            tint = TextMid,
            modifier = Modifier
                .size(24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onOpenSettings() },
        )
    }
}

@Composable
private fun ModeToggle(mode: Mode, onChange: (Mode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Seg("Timeline", mode == Mode.Timeline, Modifier.weight(1f)) { onChange(Mode.Timeline) }
        Seg("Checklist", mode == Mode.Checklist, Modifier.weight(1f)) { onChange(Mode.Checklist) }
    }
}

@Composable
private fun Seg(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) AccentSoft else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(vertical = 9.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) TextHigh else TextMid,
        )
    }
}

/* ----------------------------- Timeline ----------------------------- */

@Composable
private fun TimelineNodeView(
    node: TimelineNode,
    isFirst: Boolean,
    isLast: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleItem: (ItemRow) -> Unit,
    onMarkAll: () -> Unit,
    onRemind: () -> Unit,
) {
    val card = node.card
    val tint = GroupStyle.tint(card.group)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Rail(status = node.status, tint = tint, isFirst = isFirst, isLast = isLast)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(node.anchorLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextLow)
            Spacer(Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onToggleExpand() },
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        card.group.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (node.status == NodeStatus.DONE) TextMid else TextHigh,
                    )
                    Text(node.summary, style = MaterialTheme.typography.bodyMedium, color = TextMid)
                }
                Text(
                    "${card.takenCount}/${card.total}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (card.done) Done else tint,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                card.items.forEach { row ->
                    DoseRow(row = row, tint = tint, showChips = true, onToggle = { onToggleItem(row) })
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!card.done) {
                        PrimaryAction("Take all", tint, Modifier.weight(1f), onMarkAll)
                        Spacer(Modifier.width(12.dp))
                    }
                    GhostAction("Remind", onRemind)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun Rail(status: NodeStatus, tint: Color, isFirst: Boolean, isLast: Boolean) {
    Column(
        modifier = Modifier.width(28.dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(2.dp)
                .height(10.dp)
                .background(if (isFirst) Color.Transparent else Outline),
        )
        Dot(status, tint)
        Box(
            Modifier
                .width(2.dp)
                .weight(1f)
                .background(if (isLast) Color.Transparent else Outline),
        )
    }
}

@Composable
private fun Dot(status: NodeStatus, tint: Color) {
    val halo = status == NodeStatus.NOW
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(22.dp)) {
        if (halo) {
            Box(Modifier.size(22.dp).background(tint.copy(alpha = 0.18f), CircleShape))
        }
        when (status) {
            NodeStatus.DONE -> Box(Modifier.size(14.dp).background(Done, CircleShape))
            NodeStatus.NOW -> Box(Modifier.size(14.dp).background(tint, CircleShape))
            NodeStatus.DUE -> Box(Modifier.size(14.dp).border(2.dp, tint, CircleShape))
            NodeStatus.UPCOMING -> Box(Modifier.size(13.dp).border(1.5.dp, Outline, CircleShape))
        }
    }
}

/* ----------------------------- Checklist ----------------------------- */

@Composable
private fun CompactCard(
    card: GroupCard,
    onToggle: (ItemRow) -> Unit,
    onMarkAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = GroupStyle.tint(card.group)
    Surface(color = Surface1, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth()) {
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
            card.items.forEach { row -> DoseRow(row = row, tint = tint, showChips = false, onToggle = { onToggle(row) }) }
            if (!card.done) {
                Spacer(Modifier.height(4.dp))
                GhostAction("Mark all", onMarkAll, color = tint)
            }
        }
    }
}

/* ----------------------------- Shared ----------------------------- */

@Composable
private fun GroupGlyph(tint: Color, group: com.quietdose.data.entity.GroupEntity, size: androidx.compose.ui.unit.Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).background(tint.copy(alpha = 0.16f), CircleShape),
    ) {
        Icon(GroupStyle.icon(group), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
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
        ItemLeading(item = row.item, tint = tint, checked = row.taken)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        Text(text, style = MaterialTheme.typography.labelSmall, color = if (solid) tint else TextMid)
    }
}

@Composable
private fun ItemLeading(item: ItemEntity, tint: Color, checked: Boolean) {
    val iconAlpha by animateFloatAsState(if (checked) 0.4f else 1f, label = "iconAlpha")
    val badge by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "badge",
    )
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        if (item.hasStoredLook()) {
            StoredProductGlyph(item, modifier = Modifier.size(34.dp).graphicsLayer { alpha = iconAlpha })
        } else {
            ItemIcon(type = item.type, tint = tint, modifier = Modifier.size(34.dp).graphicsLayer { alpha = iconAlpha })
        }
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
