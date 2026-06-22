package com.quietdose.ui.stack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.model.TriggerType
import com.quietdose.ui.theme.Accent
import com.quietdose.ui.theme.GroupStyle
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.Surface2
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid
import java.time.LocalDate

/**
 * Group editor as a modal sheet. Edits a draft locally and only commits on "Save",
 * emitting the correct [GroupEntity.triggerConfig] JSON for the chosen trigger.
 * Pass a null [existing] to create a fresh group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditorSheet(
    existing: GroupEntity?,
    onDismiss: () -> Unit,
    onSave: (GroupEntity) -> Unit,
    onDelete: ((GroupEntity) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Draft state, seeded from the existing group or sensible defaults.
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var trigger by remember { mutableStateOf(existing?.trigger ?: TriggerType.WAKE) }
    var iconKey by remember { mutableStateOf(existing?.iconKey ?: "sun") }
    var tintArgb by remember {
        mutableIntStateOf(existing?.accentArgb ?: seededTint(existing?.name ?: ""))
    }
    var quiet by remember { mutableStateOf(existing?.quiet ?: false) }

    // Trigger config draft.
    val initialCfg = remember { TriggerConfig.parse(existing?.triggerConfig ?: "{}") }
    var startMin by remember { mutableIntStateOf(initialCfg.startMin) }
    var endMin by remember { mutableIntStateOf(initialCfg.endMin) }
    var interval by remember { mutableIntStateOf(initialCfg.interval) }
    var anchorDay by remember { mutableLongStateOf(initialCfg.anchorEpochDay) }
    var monthDays by remember { mutableStateOf(initialCfg.monthDays) }

    val accent = Color(tintArgb)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface1,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            EditorHeader(
                title = if (existing == null) "New group" else "Edit group",
                accent = accent,
                glyph = GroupStyle.icon(GroupEntity(name = name, trigger = trigger, iconKey = iconKey)),
            )

            EditorSection("Name") {
                StackTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Morning, Evening, Monthly pulse…",
                    accent = accent,
                )
            }

            EditorSection("Trigger") {
                ChipGroup(
                    options = TriggerType.entries,
                    selected = trigger,
                    accent = accent,
                    label = { it.label() },
                    onSelect = { trigger = it },
                )
                TriggerConfigEditor(
                    trigger = trigger,
                    accent = accent,
                    startMin = startMin, onStartMin = { startMin = it },
                    endMin = endMin, onEndMin = { endMin = it },
                    interval = interval, onInterval = { interval = it },
                    anchorDay = anchorDay, onAnchorToday = { anchorDay = LocalDate.now().toEpochDay() },
                    monthDays = monthDays, onMonthDays = { monthDays = it },
                )
            }

            EditorSection("Icon") {
                IconKeyPicker(selectedKey = iconKey, accent = accent, onSelect = { iconKey = it })
            }

            EditorSection("Accent") {
                TintSwatches(selectedArgb = tintArgb, onSelect = { tintArgb = it })
            }

            EditorSection("Notifications") {
                ToggleRow(
                    title = "Quiet",
                    subtitle = "Whisper instead of speaking up",
                    checked = quiet,
                    accent = accent,
                    onChange = { quiet = it },
                )
            }

            Spacer(Modifier.height(24.dp))

            FilledButton(
                label = if (existing == null) "Add group" else "Save",
                accent = accent,
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val cfg = TriggerConfig(
                    startMin = startMin,
                    endMin = endMin,
                    interval = interval,
                    anchorEpochDay = anchorDay,
                    monthDays = monthDays,
                )
                val base = existing ?: GroupEntity(name = "", trigger = trigger)
                onSave(
                    base.copy(
                        name = name.trim(),
                        trigger = trigger,
                        triggerConfig = cfg.toJson(trigger),
                        accentArgb = tintArgb,
                        iconKey = iconKey,
                        quiet = quiet,
                    ),
                )
            }

            if (existing != null && onDelete != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = TextLow,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    TextButtonGhost("Delete group", color = TextLow) { onDelete(existing) }
                }
            }
        }
    }
}

@Composable
private fun TriggerConfigEditor(
    trigger: TriggerType,
    accent: Color,
    startMin: Int, onStartMin: (Int) -> Unit,
    endMin: Int, onEndMin: (Int) -> Unit,
    interval: Int, onInterval: (Int) -> Unit,
    anchorDay: Long, onAnchorToday: () -> Unit,
    monthDays: List<Int>, onMonthDays: (List<Int>) -> Unit,
) {
    val step = 15
    when (trigger) {
        TriggerType.TIME_WINDOW -> {
            Spacer(Modifier.height(14.dp))
            ConfigRow("From") {
                Stepper(
                    value = formatMinutes(startMin),
                    accent = accent,
                    onDec = { onStartMin((startMin - step).coerceAtLeast(0)) },
                    onInc = { onStartMin((startMin + step).coerceAtMost(endMin - step)) },
                )
            }
            Spacer(Modifier.height(10.dp))
            ConfigRow("To") {
                Stepper(
                    value = formatMinutes(endMin),
                    accent = accent,
                    onDec = { onEndMin((endMin - step).coerceAtLeast(startMin + step)) },
                    onInc = { onEndMin((endMin + step).coerceAtMost(24 * 60 - step)) },
                )
            }
        }
        TriggerType.CADENCE_DAYS -> {
            Spacer(Modifier.height(14.dp))
            ConfigRow("Every") {
                Stepper(
                    value = if (interval == 1) "1 day" else "$interval days",
                    accent = accent,
                    onDec = { onInterval((interval - 1).coerceAtLeast(1)) },
                    onInc = { onInterval((interval + 1).coerceAtMost(60)) },
                )
            }
            Spacer(Modifier.height(10.dp))
            ConfigRow("Phase") {
                TextButtonGhost("Start today", color = accent, onClick = onAnchorToday)
            }
            val ofDay = LocalDate.ofEpochDay(anchorDay)
            Text(
                "Anchored to $ofDay",
                style = MaterialTheme.typography.bodyMedium,
                color = TextLow,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        TriggerType.MONTHLY -> {
            Spacer(Modifier.height(14.dp))
            FieldLabel("Days of the month")
            MonthDayGrid(selected = monthDays, accent = accent, onToggle = { d ->
                onMonthDays(if (d in monthDays) monthDays - d else (monthDays + d).sorted())
            })
        }
        TriggerType.WAKE,
        TriggerType.ARRIVE_HOME,
        TriggerType.ARRIVE_PLACE,
        TriggerType.LEAVE,
        TriggerType.BEFORE_SLEEP,
        TriggerType.MANUAL -> {
            // Event-anchored triggers carry no extra config here.
            Spacer(Modifier.height(8.dp))
            Text(
                triggerHint(trigger),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid,
            )
        }
    }
}

private fun triggerHint(trigger: TriggerType): String = when (trigger) {
    TriggerType.WAKE -> "Surfaces shortly after you wake."
    TriggerType.ARRIVE_HOME -> "Surfaces when you get home."
    TriggerType.ARRIVE_PLACE -> "Surfaces when you reach a saved place."
    TriggerType.LEAVE -> "Surfaces as you head out."
    TriggerType.BEFORE_SLEEP -> "Surfaces during wind-down."
    TriggerType.MANUAL -> "No automatic reminder — you open it yourself."
    else -> ""
}

@Composable
private fun ConfigRow(label: String, control: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = TextMid, modifier = Modifier.width(64.dp))
        Spacer(Modifier.width(8.dp))
        control()
    }
}

@Composable
private fun MonthDayGrid(selected: List<Int>, accent: Color, onToggle: (Int) -> Unit) {
    // 7 columns of 1..31.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..31).chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day ->
                    val on = day in selected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) accent.copy(alpha = 0.24f) else Surface2)
                            .androidxClickable { onToggle(day) },
                    ) {
                        Text(
                            "$day",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (on) accent else TextMid,
                        )
                    }
                }
            }
        }
    }
}

/* ----------------------------- Shared sheet chrome ----------------------------- */

@Composable
fun EditorHeader(title: String, accent: Color, glyph: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
        ) {
            Icon(glyph, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = TextHigh)
    }
}

@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(Surface2))
    }
}
