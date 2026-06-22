package com.quietdose.ui.share

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietdose.brain.enrich.ProductEnricher
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.ItemType
import com.quietdose.di.ServiceLocator
import com.quietdose.ui.analysis.AnalysisScreen
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
import kotlinx.coroutines.launch

private sealed interface SharePhase {
    data object Reading : SharePhase
    data class Empty(val reason: String) : SharePhase
    data class Confirm(val result: ProductEnricher.EnrichResult) : SharePhase
}

/**
 * Reads a shared product URL on-device → confirm a pre-filled draft → run it
 * through the contextual analysis → save (with the restock link kept). Mirrors
 * the scan confirm screen's calm style.
 */
@Composable
fun ShareConfirmScreen(url: String?, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.repository(context) }
    val groups by remember { repo.observeGroups() }
        .collectAsStateWithLifecycle(initialValue = emptyList<GroupEntity>())
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var phase by remember { mutableStateOf<SharePhase>(SharePhase.Reading) }
    var pendingItem by remember { mutableStateOf<ItemEntity?>(null) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            phase = SharePhase.Empty("No link found in what you shared.")
            return@LaunchedEffect
        }
        val result = ProductEnricher.enrich(context, url)
        phase = if (result.draft != null) SharePhase.Confirm(result)
        else SharePhase.Empty(result.note ?: "Couldn't read that link.")
    }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        when (val p = phase) {
            SharePhase.Reading -> Center {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(16.dp))
                Text("Reading the link…", style = MaterialTheme.typography.titleMedium, color = TextHigh)
                Text("On-device. Pulling out the product.", style = MaterialTheme.typography.bodyMedium, color = TextMid)
            }
            is SharePhase.Empty -> Center {
                Text("Nothing to add", style = MaterialTheme.typography.titleLarge, color = TextHigh)
                Spacer(Modifier.height(8.dp))
                Text(p.reason, style = MaterialTheme.typography.bodyMedium, color = TextMid)
                Spacer(Modifier.height(20.dp))
                FilledButton(label = "Close", accent = Accent, onClick = onClose)
            }
            is SharePhase.Confirm -> ConfirmBody(
                result = p.result,
                groups = groups,
                onClose = onClose,
                onConfirm = { item -> pendingItem = item },
            )
        }
    }

    // Route the confirmed draft through the contextual analysis, then persist.
    pendingItem?.let { item ->
        AnalysisScreen(
            item = item,
            onAdd = {
                scope.launch { repo.upsertItem(item) }
                onSaved()
            },
            onDismiss = { pendingItem = null },
        )
    }
}

@Composable
private fun Center(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
        content = content,
    )
}

@Composable
private fun ConfirmBody(
    result: ProductEnricher.EnrichResult,
    groups: List<GroupEntity>,
    onClose: () -> Unit,
    onConfirm: (ItemEntity) -> Unit,
) {
    val draft = result.draft!!
    var name by remember { mutableStateOf(draft.name) }
    var brand by remember { mutableStateOf(draft.brand ?: "") }
    var category by remember { mutableStateOf(draft.category ?: "") }
    var type by remember { mutableStateOf(draft.type) }
    var doseAmount by remember { mutableStateOf(formatAmount(draft.doseAmount)) }
    var doseUnit by remember { mutableStateOf(draft.doseUnit) }
    var selectedGroupId by remember { mutableStateOf(groups.firstOrNull()?.id) }
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
            Text("From a link", style = MaterialTheme.typography.titleMedium, color = TextHigh)
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
        ) {
            // preview
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface2).padding(16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                ) {
                    ItemIcon(type = type, tint = accent, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name.ifBlank { "New item" }, style = MaterialTheme.typography.titleLarge, color = TextHigh)
                    Text("Read from the link — check the details", style = MaterialTheme.typography.bodyMedium, color = TextMid)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Link, contentDescription = null, tint = TextLow, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(result.url.take(60), style = MaterialTheme.typography.labelSmall, color = TextLow)
            }

            EditorSection("Name") { StackTextField(name, { name = it }, "Product name", accent = accent) }
            EditorSection("Brand & category") {
                StackTextField(brand, { brand = it }, "Brand (optional)", accent = accent)
                Spacer(Modifier.height(8.dp))
                StackTextField(category, { category = it }, "Category (optional)", accent = accent)
            }
            EditorSection("Form") {
                ChipGroup(options = ItemType.entries, selected = type, accent = accent, label = { it.label() }, onSelect = { type = it })
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
                ChipGroup(options = DoseUnit.entries, selected = doseUnit, accent = accent, label = { it.label() }, onSelect = { doseUnit = it })
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
                                ) {
                                    Icon(GroupStyle.icon(group), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(group.name, style = MaterialTheme.typography.titleMedium, color = if (selected) TextHigh else TextMid)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            FilledButton(
                label = "Analyze & add",
                accent = accent,
                enabled = name.isNotBlank() && selectedGroupId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val groupId = selectedGroupId ?: return@FilledButton
                onConfirm(
                    ItemEntity(
                        groupId = groupId,
                        name = name.trim(),
                        brand = brand.trim().ifBlank { null },
                        category = category.trim().ifBlank { null },
                        type = type,
                        doseAmount = doseAmount.toDoubleOrNull() ?: 1.0,
                        doseUnit = doseUnit,
                        purchaseUrl = result.url,
                        createdAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}

private fun formatAmount(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
