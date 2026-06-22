package com.quietdose.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quietdose.brain.analysis.ItemKind
import com.quietdose.brain.analysis.KindDetector
import com.quietdose.brain.skills.DraftItem
import com.quietdose.brain.skills.UseTiming
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.FrequencyType
import com.quietdose.data.model.ItemType
import com.quietdose.ui.icons.ItemIcon
import com.quietdose.ui.stack.ChipGroup
import com.quietdose.ui.stack.EditorSection
import com.quietdose.ui.stack.FilledButton
import com.quietdose.ui.stack.StackTextField
import com.quietdose.ui.stack.androidxClickable
import com.quietdose.ui.stack.label
import com.quietdose.ui.theme.Accent
import com.quietdose.ui.theme.GroupStyle
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.Surface2
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid

/**
 * The ONE confirm screen every add-channel shares — typed, scanned or from a link.
 * It pre-fills from [draft], shows where the data came from ([provenance]), lets the
 * user fix anything, and hands back a configured [ItemEntity] for the analysis. The
 * action button is pinned (never lost behind a nav bar; this is hosted in its own
 * Activity).
 */
@Composable
fun AddConfirm(
    draft: DraftItem,
    provenance: AddProvenance,
    groups: List<GroupEntity>,
    purchaseUrl: String?,
    onClose: () -> Unit,
    onConfirm: (ItemEntity) -> Unit,
    initialGroupId: Long? = null,
) {
    var name by remember { mutableStateOf(draft.name) }
    var brand by remember { mutableStateOf(draft.brand ?: "") }
    var category by remember { mutableStateOf(draft.category ?: "") }
    var type by remember { mutableStateOf(draft.type) }
    var doseAmount by remember { mutableStateOf(trimAmount(draft.doseAmount)) }
    var doseUnit by remember { mutableStateOf(draft.doseUnit) }
    var timing by remember { mutableStateOf(draft.timing) }
    var frequency by remember { mutableStateOf(FrequencyType.DAILY) }
    var note by remember { mutableStateOf(draft.note ?: "") }
    var selectedGroupId by remember { mutableStateOf(initialGroupId ?: groups.firstOrNull()?.id) }

    // Dose vs. application: a supplement has an mg dose; a toner/device has a
    // per-use amount, a morning/evening rhythm and a frequency — different fields.
    val kind = KindDetector.detect(name, category.ifBlank { null } ?: brand.ifBlank { null })
    val ingested = kind.isIngested
    LaunchedEffect(groups) { if (selectedGroupId == null) selectedGroupId = groups.firstOrNull()?.id }

    val selectedGroup = groups.firstOrNull { it.id == selectedGroupId }
    val accent = selectedGroup?.let { GroupStyle.tint(it) } ?: Accent

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(CircleShape).androidxClickable(onClose),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextMid, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text("Add item", style = MaterialTheme.typography.titleMedium, color = TextHigh)
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
        ) {
            // Preview
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface2).padding(16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                ) { ItemIcon(type = type, tint = accent, modifier = Modifier.size(40.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name.ifBlank { "New item" }, style = MaterialTheme.typography.titleLarge, color = TextHigh)
                    Text(provenanceSubtitle(provenance), style = MaterialTheme.typography.bodyMedium, color = TextMid)
                }
            }

            ProvenanceCard(provenance)

            EditorSection("Name") { StackTextField(name, { name = it }, "Product name", accent = accent) }
            EditorSection("Brand & category") {
                StackTextField(brand, { brand = it }, "Brand (optional)", accent = accent)
                Spacer(Modifier.height(8.dp))
                StackTextField(category, { category = it }, "Category (optional)", accent = accent)
            }
            EditorSection("Form") {
                ChipGroup(options = ItemType.entries, selected = type, accent = accent, label = { it.label() }, onSelect = { type = it })
            }
            // Supplements are dosed (mg/mcg/IU); applied items (skincare, haircare,
            // devices) aren't — they have a per-use amount, a time of day and a rhythm.
            EditorSection(if (ingested) "Dose" else "Each use") {
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
                    options = if (ingested) DoseUnit.entries else APPLIED_UNITS,
                    selected = doseUnit,
                    accent = accent,
                    label = { it.label() },
                    onSelect = { doseUnit = it },
                )
            }
            if (!ingested) {
                EditorSection("When to use") {
                    ChipGroup(
                        options = UseTiming.entries,
                        selected = timing,
                        accent = accent,
                        label = { it.label },
                        onSelect = { timing = it },
                    )
                }
                EditorSection("How often") {
                    ChipGroup(
                        options = FrequencyType.entries,
                        selected = frequency,
                        accent = accent,
                        label = { it.label() },
                        onSelect = { frequency = it },
                    )
                }
            }
            EditorSection("Add to") {
                if (groups.isEmpty()) {
                    Text("No groups yet — create one in the app first.", style = MaterialTheme.typography.bodyMedium, color = TextMid)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.forEach { group ->
                            val selected = group.id == selectedGroupId
                            val tint = GroupStyle.tint(group)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) tint.copy(alpha = 0.18f) else Surface2)
                                    .androidxClickable { selectedGroupId = group.id }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
                                ) { Icon(GroupStyle.icon(group), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
                                Spacer(Modifier.width(12.dp))
                                Text(group.name, style = MaterialTheme.typography.titleMedium, color = if (selected) TextHigh else TextMid)
                            }
                        }
                    }
                }
            }
            EditorSection("Note") {
                StackTextField(note, { note = it }, "Anything worth remembering (optional)", accent = accent, singleLine = false)
            }
        }

        // Pinned — always reachable.
        Column(Modifier.fillMaxWidth().background(Surface1).imePadding().navigationBarsPadding().padding(16.dp)) {
            FilledButton(
                label = "Analyze & add",
                accent = accent,
                enabled = name.isNotBlank() && selectedGroupId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val groupId = selectedGroupId ?: return@FilledButton
                // Applied items carry their "when to use" in the note (the entity has no
                // timing column) and set the chosen frequency directly; supplements are
                // unchanged. Timing is folded in plain words so it reads naturally.
                val finalNote = if (ingested || timing == UseTiming.ANYTIME) {
                    note.trim().ifBlank { null }
                } else {
                    listOf("Use: ${timing.label.lowercase()}", note.trim())
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { null }
                }
                onConfirm(
                    ItemEntity(
                        groupId = groupId,
                        name = name.trim(),
                        brand = brand.trim().ifBlank { null },
                        category = category.trim().ifBlank { null },
                        type = type,
                        doseAmount = doseAmount.toDoubleOrNull() ?: 1.0,
                        doseUnit = doseUnit,
                        frequency = if (ingested) FrequencyType.DAILY else frequency,
                        flags = draft.flags,
                        note = finalNote,
                        purchaseUrl = purchaseUrl,
                        createdAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}

@Composable
private fun ProvenanceCard(provenance: AddProvenance) {
    when (provenance) {
        is AddProvenance.Typed -> Unit // nothing read; user is typing it
        is AddProvenance.Linked -> {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Link, contentDescription = null, tint = TextLow, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(provenance.url.take(60), style = MaterialTheme.typography.labelSmall, color = TextLow)
            }
        }
        is AddProvenance.Scanned -> {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface1).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = TextLow, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "WHAT WE READ · ${provenance.photoCount} photo${if (provenance.photoCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextLow,
                    )
                }
                if (provenance.ocrText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(provenance.ocrText.take(600), style = MaterialTheme.typography.bodySmall, color = TextMid)
                }
            }
        }
    }
}

private fun provenanceSubtitle(p: AddProvenance): String = when (p) {
    is AddProvenance.Typed -> "Typed in — check the details"
    is AddProvenance.Linked -> "Read from the link — check the details"
    is AddProvenance.Scanned -> "Read from the label — fix anything off"
}

private fun trimAmount(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/**
 * Per-use units that make sense for applied items (a toner is "2 drops" or "1 ml",
 * a device is "1 use") — the mg/mcg/IU dosing scale is hidden for them.
 */
private val APPLIED_UNITS: List<DoseUnit> = listOf(DoseUnit.UNIT, DoseUnit.DROP, DoseUnit.ML, DoseUnit.G)
