package com.quietdose.ui.stack

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quietdose.brain.analysis.ProductLook
import com.quietdose.brain.analysis.ProductLookCodec
import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.FrequencyType
import com.quietdose.data.model.ItemType
import com.quietdose.ui.analysis.IconPickerRow
import com.quietdose.ui.analysis.StoredProductGlyph
import com.quietdose.ui.analysis.hasStoredLook
import com.quietdose.ui.icons.ItemIcon
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.Surface2
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid
import java.time.LocalDate

/**
 * Item editor as a modal sheet, edited as a local draft and committed on save.
 * The hero is a live [ItemIcon] preview that re-renders the moment type or tint
 * changes — a seeded "dynamic default" the user can re-roll with the vary control.
 * Pass a null [existing] to create a fresh item in [groupId].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditorSheet(
    groupId: Long,
    existing: ItemEntity?,
    groupTintArgb: Int,
    onDismiss: () -> Unit,
    onSave: (ItemEntity) -> Unit,
    onDelete: ((ItemEntity) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var brand by remember { mutableStateOf(existing?.brand ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: ItemType.CAPSULE) }

    // Live preview tint: seeded dynamic default from the name (or the group tint),
    // re-rollable via the "vary" control. Persisted onto category-free preview only;
    // it drives the on-screen icon, matching the app's per-group tinting at render time.
    var previewTint by remember {
        mutableIntStateOf(
            if ((existing?.name ?: "").isNotBlank()) seededTint(existing!!.name) else groupTintArgb,
        )
    }

    // The picked drawn "look". null → keep whatever's stored (auto). Editing a saved item
    // starts on its current look; picking a tile overrides it on save.
    var pickedLook by remember { mutableStateOf(ProductLookCodec.decode(existing?.look)) }

    var doseAmount by remember { mutableStateOf(formatAmount(existing?.doseAmount ?: 1.0)) }
    var doseUnit by remember { mutableStateOf(existing?.doseUnit ?: DoseUnit.UNIT) }

    var frequency by remember { mutableStateOf(existing?.frequency ?: FrequencyType.DAILY) }
    var interval by remember { mutableIntStateOf(existing?.frequencyInterval ?: 2) }
    var daysMask by remember { mutableIntStateOf(existing?.frequencyDaysMask ?: 0) }
    var monthDays by remember {
        mutableStateOf(
            existing?.frequencyDaysOfMonth
                ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { it in 1..31 } ?: emptyList(),
        )
    }

    var flags by remember { mutableIntStateOf(existing?.flags ?: 0) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var purchaseUrl by remember { mutableStateOf(existing?.purchaseUrl ?: "") }
    var trackStock by remember { mutableStateOf(existing?.stockCount != null) }
    var stockCount by remember { mutableStateOf(formatAmount(existing?.stockCount ?: 0.0)) }
    var unitsPerDose by remember { mutableStateOf(formatAmount(existing?.unitsPerDose ?: 1.0)) }

    val accent = Color(previewTint)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface1,
        dragHandle = { Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(Surface2))
        } },
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
            LivePreview(
                type = type,
                tint = accent,
                title = name.ifBlank { if (existing == null) "New item" else "Item" },
                subtitle = type.label(),
                onVary = { previewTint = nextTint(previewTint) },
                // Reflect the CURRENT pick + typed fields live, so the preview updates the
                // moment you choose an icon (not only after save).
                item = (existing ?: ItemEntity(groupId = groupId, name = name)).copy(
                    name = name.trim(),
                    brand = brand.trim().ifBlank { null },
                    category = category.trim().ifBlank { null },
                    type = type,
                    look = pickedLook?.let { ProductLookCodec.encode(it) } ?: existing?.look,
                ),
            )

            EditorSection("Name") {
                StackTextField(name, { name = it }, "NMN, Vitamin D, Magnesium…", accent = accent)
            }

            EditorSection("Brand & category") {
                StackTextField(brand, { brand = it }, "Brand (optional)", accent = accent)
                Spacer(Modifier.height(8.dp))
                StackTextField(category, { category = it }, "Category, e.g. Longevity (optional)", accent = accent)
            }

            EditorSection("Form") {
                ChipGroup(
                    options = ItemType.entries,
                    selected = type,
                    accent = accent,
                    label = { it.label() },
                    onSelect = { type = it },
                )
            }

            EditorSection("Icon") {
                // A live item built from the current fields so the tiles reflect what's typed.
                val iconItem = (existing ?: ItemEntity(groupId = groupId, name = "")).copy(
                    name = name.trim(),
                    brand = brand.trim().ifBlank { null },
                    category = category.trim().ifBlank { null },
                    type = type,
                )
                IconPickerRow(
                    item = iconItem,
                    selected = pickedLook,
                    onPick = { pickedLook = it },
                )
            }

            EditorSection("Dose") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StackTextField(
                        value = doseAmount,
                        onValueChange = { doseAmount = it.filter { c -> c.isDigit() || c == '.' } },
                        placeholder = "1",
                        keyboardType = KeyboardType.Decimal,
                        accent = accent,
                        modifier = Modifier.width(110.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(doseUnit.label(), style = MaterialTheme.typography.titleMedium, color = TextMid)
                }
                Spacer(Modifier.height(10.dp))
                ChipGroup(
                    options = DoseUnit.entries,
                    selected = doseUnit,
                    accent = accent,
                    label = { it.label() },
                    onSelect = { doseUnit = it },
                )
            }

            EditorSection("Frequency") {
                ChipGroup(
                    options = FrequencyType.entries,
                    selected = frequency,
                    accent = accent,
                    label = { it.label() },
                    onSelect = { frequency = it },
                )
                FrequencyDetail(
                    frequency = frequency,
                    accent = accent,
                    interval = interval, onInterval = { interval = it },
                    daysMask = daysMask, onDaysMask = { daysMask = it },
                    monthDays = monthDays, onMonthDays = { monthDays = it },
                )
            }

            EditorSection("Behaviour") {
                ToggleChipRow(
                    options = FlagChoices,
                    isOn = { bit -> flags and bit != 0 },
                    accent = accent,
                    onToggle = { bit -> flags = flags xor bit },
                )
            }

            EditorSection("Note") {
                StackTextField(note, { note = it }, "Anything worth remembering (optional)", accent = accent, singleLine = false)
            }

            EditorSection("Restock") {
                StackTextField(purchaseUrl, { purchaseUrl = it }, "Purchase link (optional)", keyboardType = KeyboardType.Uri, accent = accent)
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    title = "Track stock",
                    subtitle = "Count down units as you take them",
                    checked = trackStock,
                    accent = accent,
                    onChange = { trackStock = it },
                )
                if (trackStock) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            FieldLabel("Units left")
                            StackTextField(
                                stockCount,
                                { stockCount = it.filter { c -> c.isDigit() || c == '.' } },
                                "0", keyboardType = KeyboardType.Decimal, accent = accent,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            FieldLabel("Per dose")
                            StackTextField(
                                unitsPerDose,
                                { unitsPerDose = it.filter { c -> c.isDigit() || c == '.' } },
                                "1", keyboardType = KeyboardType.Decimal, accent = accent,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            FilledButton(
                label = if (existing == null) "Add item" else "Save",
                accent = accent,
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val base = existing ?: ItemEntity(groupId = groupId, name = "")
                onSave(
                    base.copy(
                        groupId = groupId,
                        name = name.trim(),
                        brand = brand.trim().ifBlank { null },
                        category = category.trim().ifBlank { null },
                        type = type,
                        doseAmount = doseAmount.toDoubleOrNull() ?: 1.0,
                        doseUnit = doseUnit,
                        frequency = frequency,
                        frequencyInterval = interval.coerceAtLeast(1),
                        frequencyAnchorEpochDay = if (frequency == FrequencyType.EVERY_N_DAYS && base.frequencyAnchorEpochDay == 0L)
                            LocalDate.now().toEpochDay() else base.frequencyAnchorEpochDay,
                        frequencyDaysMask = daysMask,
                        frequencyDaysOfMonth = monthDays.sorted().joinToString(","),
                        flags = flags,
                        note = note.trim().ifBlank { null },
                        purchaseUrl = purchaseUrl.trim().ifBlank { null },
                        stockCount = if (trackStock) (stockCount.toDoubleOrNull() ?: 0.0) else null,
                        unitsPerDose = unitsPerDose.toDoubleOrNull() ?: 1.0,
                        // Apply the picked icon; leaving it untouched preserves the existing look.
                        look = pickedLook?.let { ProductLookCodec.encode(it) } ?: base.look,
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
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = TextLow, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    TextButtonGhost("Delete item", color = TextLow) { onDelete(existing) }
                }
            }
        }
    }
}

@Composable
private fun LivePreview(
    type: ItemType,
    tint: Color,
    title: String,
    subtitle: String,
    onVary: () -> Unit,
    item: ItemEntity? = null,
) {
    val haptics = LocalHapticFeedback.current
    // A gentle pop whenever the form (or tint) changes, so picking feels satisfying:
    // snap down, then spring back to 1 each time the keyed effect re-runs.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(type, tint) {
        pop.snapTo(0.84f)
        pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Surface2)
            .padding(16.dp),
    ) {
        if (item?.hasStoredLook() == true) {
            // A saved product keeps the same lookalike glyph the stack shows — no old form icon.
            StoredProductGlyph(
                item,
                modifier = Modifier.size(64.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                showBacking = true,
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
            ) {
                ItemIcon(
                    type = type,
                    tint = tint,
                    modifier = Modifier.size(48.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = TextHigh)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.18f))
                .androidxClickable {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onVary()
                },
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = "Vary colour", tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FrequencyDetail(
    frequency: FrequencyType,
    accent: Color,
    interval: Int, onInterval: (Int) -> Unit,
    daysMask: Int, onDaysMask: (Int) -> Unit,
    monthDays: List<Int>, onMonthDays: (List<Int>) -> Unit,
) {
    when (frequency) {
        FrequencyType.EVERY_N_DAYS -> {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Every", style = MaterialTheme.typography.titleMedium, color = TextMid)
                Spacer(Modifier.width(12.dp))
                Stepper(
                    value = if (interval == 1) "1 day" else "$interval days",
                    accent = accent,
                    onDec = { onInterval((interval - 1).coerceAtLeast(1)) },
                    onInc = { onInterval((interval + 1).coerceAtMost(60)) },
                )
            }
        }
        FrequencyType.WEEKLY -> {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WeekdayLabels.forEachIndexed { i, lbl ->
                    val on = (daysMask shr i) and 1 == 1
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (on) accent.copy(alpha = 0.24f) else Surface2)
                            .androidxClickable { onDaysMask(daysMask xor (1 shl i)) },
                    ) {
                        Text(lbl, style = MaterialTheme.typography.labelLarge, color = if (on) accent else TextMid)
                    }
                }
            }
        }
        FrequencyType.MONTHLY_DAYS -> {
            Spacer(Modifier.height(14.dp))
            FieldLabel("Days of the month")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..31).chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        week.forEach { day ->
                            val on = day in monthDays
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (on) accent.copy(alpha = 0.24f) else Surface2)
                                    .androidxClickable {
                                        onMonthDays(if (on) monthDays - day else (monthDays + day).sorted())
                                    },
                            ) {
                                Text("$day", style = MaterialTheme.typography.bodyMedium, color = if (on) accent else TextMid)
                            }
                        }
                    }
                }
            }
        }
        FrequencyType.DAILY -> {
            Spacer(Modifier.height(8.dp))
            Text("Due every day.", style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
        FrequencyType.AS_NEEDED -> {
            Spacer(Modifier.height(8.dp))
            Text("Only when you reach for it.", style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
    }
}

private fun formatAmount(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
