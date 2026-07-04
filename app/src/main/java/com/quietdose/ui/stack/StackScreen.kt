package com.quietdose.ui.stack

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quietdose.brain.analysis.ProductLookCodec
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import android.widget.Toast
import androidx.compose.ui.window.Dialog
import com.quietdose.ui.add.AddItemActivity
import com.quietdose.ui.analysis.StoredProductGlyph
import com.quietdose.ui.analysis.hasStoredLook
import com.quietdose.ui.analysis.benefitsFor
import com.quietdose.ui.analysis.lookVariants
import com.quietdose.ui.icons.ItemIcon
import com.quietdose.ui.theme.Accent
import com.quietdose.ui.theme.GroupStyle
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.Surface2
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid
import com.quietdose.ui.theme.TintNeutral
import com.quietdose.util.Format
import kotlinx.coroutines.launch

/** Which editor sheet, if any, is currently open. */
private sealed interface Editing {
    data object NewGroup : Editing
    data class EditGroup(val group: GroupEntity) : Editing
    data class NewItem(val groupId: Long, val tintArgb: Int) : Editing
    data class EditItem(val item: ItemEntity, val tintArgb: Int) : Editing
}

/** Stack tab views: routines by group, or the flat catalogue of every item. */
private enum class StackMode { Groups, Items }

@Composable
fun StackScreen(modifier: Modifier = Modifier, vm: StackViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var mode by remember { mutableStateOf(StackMode.Groups) }
    var query by remember { mutableStateOf("") }
    var showAddChooser by remember { mutableStateOf(false) }
    var showLinkInput by remember { mutableStateOf(false) }

    // Flat catalogue, computed in composable scope (not inside the LazyColumn lambda).
    val catalogRows = remember(state.groups) {
        state.groups.flatMap { sg -> sg.items.map { it to sg.group } }
    }
    val catalogFiltered = catalogRows.filter { (item, _) ->
        query.isBlank() ||
            item.name.contains(query, ignoreCase = true) ||
            (item.brand?.contains(query, ignoreCase = true) == true) ||
            (item.category?.contains(query, ignoreCase = true) == true)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { StackHeader(onScan = { context.startActivity(AddItemActivity.scan(context)) }) }
        item { ModeToggle(mode = mode, onMode = { mode = it }) }

        if (mode == StackMode.Groups) {
            items(state.groups, key = { it.group.id }) { sg ->
                GroupCardEditable(
                    stack = sg,
                    expanded = expanded[sg.group.id] ?: false,
                    onToggleExpand = { expanded[sg.group.id] = !(expanded[sg.group.id] ?: false) },
                    onEditGroup = { editing = Editing.EditGroup(sg.group) },
                    onMoveUp = { vm.moveGroup(sg.group.id, up = true) },
                    onMoveDown = { vm.moveGroup(sg.group.id, up = false) },
                    onAddItem = { context.startActivity(AddItemActivity.typed(context, sg.group.id)) },
                    onEditItem = { item -> editing = Editing.EditItem(item, GroupStyle.tint(sg.group).toArgb()) },
                )
            }

            if (state.isEmpty) {
                item { EmptyState() }
            }

            item {
                Spacer(Modifier.height(4.dp))
                AddGroupButton { editing = Editing.NewGroup }
                Spacer(Modifier.height(24.dp))
            }
        } else {
            // Flat catalogue: every tracked item, across groups, searchable.
            item { AddItemBar(onClick = { showAddChooser = true }) }
            item { CatalogSearch(query = query, onQuery = { query = it }, count = catalogFiltered.size) }
            if (catalogFiltered.isEmpty()) {
                item { CatalogEmpty(hasItems = catalogRows.isNotEmpty()) }
            } else {
                items(catalogFiltered, key = { it.first.id }) { (item, group) ->
                    CatalogRow(
                        item = item,
                        group = group,
                        onClick = { editing = Editing.EditItem(item, GroupStyle.tint(group).toArgb()) },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    when (val e = editing) {
        is Editing.NewGroup -> GroupEditorSheet(
            existing = null,
            onDismiss = { editing = null },
            onSave = { vm.addGroup(it); editing = null },
        )
        is Editing.EditGroup -> GroupEditorSheet(
            existing = e.group,
            onDismiss = { editing = null },
            onSave = { vm.saveGroup(it); editing = null },
            onDelete = { vm.deleteGroup(it); editing = null },
        )
        is Editing.NewItem -> ItemEditorSheet(
            groupId = e.groupId,
            existing = null,
            groupTintArgb = e.tintArgb,
            onDismiss = { editing = null },
            onSave = { vm.addItem(it); editing = null },
        )
        is Editing.EditItem -> ItemEditorSheet(
            groupId = e.item.groupId,
            existing = e.item,
            groupTintArgb = e.tintArgb,
            onDismiss = { editing = null },
            onSave = { vm.saveItem(it); editing = null },
            onDelete = { vm.deleteItem(it); editing = null },
        )
        null -> Unit
    }

    if (showAddChooser) {
        AddChooserDialog(
            hasGroups = state.groups.isNotEmpty(),
            onManual = {
                showAddChooser = false
                state.groups.firstOrNull()?.let { sg ->
                    context.startActivity(AddItemActivity.typed(context, sg.group.id))
                }
            },
            onScan = { showAddChooser = false; context.startActivity(AddItemActivity.scan(context)) },
            onLink = { showAddChooser = false; showLinkInput = true },
            onGenerateAll = {
                showAddChooser = false
                // Backfill every item that is missing EITHER its drawn icon OR its benefit
                // orbs — no model, no network. Items drawn in an earlier build already have a
                // look but no benefits, so orbs never appeared; catch those here too. Preserve
                // an existing look; only infer one when there is none.
                val todo = state.groups.flatMap { it.items }
                    .filter { it.look.isNullOrBlank() || it.benefits.isNullOrBlank() }
                scope.launch {
                    todo.forEach { item ->
                        // Keep the item's existing icon; only draw a fresh one when it has none.
                        val lookJson = item.look?.ifBlank { null }
                            ?: lookVariants(item).firstOrNull()?.let { ProductLookCodec.encode(it) }
                        // Derive the benefits (for the icon's orbs) when the item has none.
                        val benefits = item.benefits?.ifBlank { null }
                            ?: benefitsFor(item).joinToString("\n").ifBlank { null }
                        vm.saveItem(item.copy(look = lookJson, benefits = benefits))
                    }
                }
                Toast.makeText(
                    context,
                    if (todo.isEmpty()) "Every item already has an icon and orbs"
                    else "Updated ${todo.size} icon${if (todo.size == 1) "" else "s"}",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = { showAddChooser = false },
        )
    }
    if (showLinkInput) {
        LinkInputDialog(
            onSubmit = { url ->
                showLinkInput = false
                // Full-screen Activity — same unified flow as sharing a link to Dose.
                runCatching { context.startActivity(AddItemActivity.link(context, url)) }
            },
            onDismiss = { showLinkInput = false },
        )
    }
}

@Composable
private fun StackHeader(onScan: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 2.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Your stack", style = MaterialTheme.typography.titleMedium, color = TextMid)
            Text("Customize", style = MaterialTheme.typography.displaySmall, color = TextHigh)
            Text(
                "Shape your routines — drag the rhythm, pick the form, set the mood.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMid,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 20.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = 0.12f))
                .androidxClickable(onScan),
        ) {
            Icon(
                Icons.Rounded.PhotoCamera,
                contentDescription = "Scan a label",
                tint = Accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/* ----------------------------- Catalogue view ----------------------------- */

@Composable
private fun ModeToggle(mode: StackMode, onMode: (StackMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeSegment("Groups", mode == StackMode.Groups, Modifier.weight(1f)) { onMode(StackMode.Groups) }
        ModeSegment("All items", mode == StackMode.Items, Modifier.weight(1f)) { onMode(StackMode.Items) }
    }
}

@Composable
private fun ModeSegment(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Accent.copy(alpha = 0.20f) else Color.Transparent)
            .androidxClickable(onClick)
            .padding(vertical = 9.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Accent else TextMid,
        )
    }
}

@Composable
private fun CatalogSearch(query: String, onQuery: (String) -> Unit, count: Int) {
    Column(Modifier.fillMaxWidth()) {
        StackTextField(value = query, onValueChange = onQuery, placeholder = "Search items…")
        Spacer(Modifier.height(4.dp))
        Text(
            "$count item${if (count == 1) "" else "s"}",
            style = MaterialTheme.typography.labelSmall,
            color = TextLow,
        )
    }
}

@Composable
private fun CatalogRow(item: ItemEntity, group: GroupEntity, onClick: () -> Unit) {
    val tint = GroupStyle.tint(group)
    Surface(color = Surface1, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.androidxClickable(onClick).padding(14.dp),
        ) {
            if (item.hasStoredLook()) {
                // A saved product keeps the lookalike glyph it earned at analysis.
                StoredProductGlyph(item, modifier = Modifier.size(40.dp), showBacking = true)
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
                ) {
                    ItemIcon(type = item.type, tint = tint, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, color = TextHigh)
                val sub = listOfNotNull(item.brand?.ifBlank { null }, group.name).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = TextMid)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(catalogDose(item), style = MaterialTheme.typography.bodyMedium, color = TextLow)
        }
    }
}

private fun catalogDose(item: ItemEntity): String {
    val amt = if (item.doseAmount % 1.0 == 0.0) item.doseAmount.toLong().toString() else item.doseAmount.toString()
    return "$amt ${item.doseUnit.label()}"
}

@Composable
private fun CatalogEmpty(hasItems: Boolean) {
    Text(
        if (hasItems) "No items match." else "No items yet — tap Add item to type one in, scan a label, or paste a link.",
        style = MaterialTheme.typography.bodyLarge,
        color = TextLow,
        modifier = Modifier.padding(vertical = 20.dp),
    )
}

@Composable
private fun AddItemBar(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Accent.copy(alpha = 0.16f))
            .androidxClickable(onClick)
            .padding(vertical = 13.dp),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add item", style = MaterialTheme.typography.titleMedium, color = Accent)
    }
}

@Composable
private fun AddChooserDialog(
    hasGroups: Boolean,
    onManual: () -> Unit,
    onScan: () -> Unit,
    onLink: () -> Unit,
    onGenerateAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Surface1, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Add an item", style = MaterialTheme.typography.titleLarge, color = TextHigh)
                Text(
                    "Every route runs the same on-device analysis before it's added.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                )
                Spacer(Modifier.height(16.dp))
                ChooserRow(Icons.Rounded.Add, "Type it in", if (hasGroups) "Enter the name and details" else "Create a group first", enabled = hasGroups, onClick = onManual)
                Spacer(Modifier.height(8.dp))
                ChooserRow(Icons.Rounded.PhotoCamera, "Scan a label", "Photograph front + back", enabled = true, onClick = onScan)
                Spacer(Modifier.height(8.dp))
                ChooserRow(Icons.Rounded.Link, "From a link", "Paste an Amazon/Flipkart URL", enabled = true, onClick = onLink)
                Spacer(Modifier.height(8.dp))
                ChooserRow(Icons.Rounded.AutoAwesome, "Generate all icons", "Draw an icon for every item without one", enabled = true, onClick = onGenerateAll)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButtonGhost("Cancel", color = TextLow, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun ChooserRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) Accent else TextLow
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .androidxClickable { if (enabled) onClick() }
            .padding(14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) TextHigh else TextLow)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMid)
        }
    }
}

@Composable
private fun LinkInputDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Surface1, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Paste a product link", style = MaterialTheme.typography.titleLarge, color = TextHigh)
                Spacer(Modifier.height(12.dp))
                StackTextField(value = url, onValueChange = { url = it }, placeholder = "https://…")
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButtonGhost("Cancel", color = TextLow, onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    FilledButton(label = "Read link", accent = Accent, enabled = url.isNotBlank()) { onSubmit(url.trim()) }
                }
            }
        }
    }
}

@Composable
private fun GroupCardEditable(
    stack: StackGroup,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onEditGroup: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (ItemEntity) -> Unit,
) {
    val group = stack.group
    val tint = GroupStyle.tint(group)
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Surface(color = Surface1, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupGlyph(group = group, tint = tint)
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .androidxClickable { onToggleExpand() },
                ) {
                    Text(group.name, style = MaterialTheme.typography.titleLarge, color = TextHigh)
                    Text(subtitle(group, stack.items.size), style = MaterialTheme.typography.bodyMedium, color = TextMid)
                }
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextLow,
                    modifier = Modifier.size(24.dp).rotate(chevron).androidxClickable { onToggleExpand() },
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    stack.items.forEach { item ->
                        ItemRowEditable(item = item, tint = tint, onClick = { onEditItem(item) })
                    }
                    if (stack.items.isEmpty()) {
                        Text(
                            "No items yet — add the first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextLow,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InlineAction(Icons.Rounded.Add, "Add item", tint, Modifier.weight(1f), onAddItem)
                        Spacer(Modifier.width(8.dp))
                        InlineAction(Icons.Rounded.Tune, "Edit group", TextMid, onClick = onEditGroup)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ReorderButton(Icons.Rounded.KeyboardArrowUp, "Move up", onMoveUp)
                        Spacer(Modifier.width(6.dp))
                        ReorderButton(Icons.Rounded.KeyboardArrowDown, "Move down", onMoveDown)
                    }
                }
            }
        }
    }
}

private fun subtitle(group: GroupEntity, itemCount: Int): String {
    val count = if (itemCount == 1) "1 item" else "$itemCount items"
    return "${group.trigger.label()} · $count"
}

@Composable
private fun GroupGlyph(group: GroupEntity, tint: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(40.dp).background(tint.copy(alpha = 0.16f), CircleShape),
    ) {
        Icon(GroupStyle.icon(group), contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun ItemRowEditable(item: ItemEntity, tint: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .androidxClickable { onClick() }
            .padding(vertical = 8.dp),
    ) {
        if (item.hasStoredLook()) StoredProductGlyph(item, modifier = Modifier.size(34.dp))
        else ItemIcon(type = item.type, tint = tint, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = TextHigh,
            )
            val dose = Format.dose(item)
            val line = listOfNotNull(item.brand?.takeIf { it.isNotBlank() }, dose.ifBlank { null })
                .joinToString(" · ")
            if (line.isNotBlank()) {
                Text(line, style = MaterialTheme.typography.bodyMedium, color = TextMid)
            }
        }
        Icon(Icons.Rounded.Tune, contentDescription = "Edit", tint = TextLow, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun InlineAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.16f))
            .androidxClickable { onClick() }
            .padding(vertical = 11.dp, horizontal = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun ReorderButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Surface2)
            .androidxClickable { onClick() },
    ) {
        Icon(icon, contentDescription = desc, tint = TextMid, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AddGroupButton(onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .androidxClickable { onClick() }
            .padding(vertical = 16.dp),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = TextMid, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("New group", style = MaterialTheme.typography.labelLarge, color = TextMid)
    }
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(56.dp).background(TintNeutral.copy(alpha = 0.14f), CircleShape),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = TintNeutral, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Build your stack", style = MaterialTheme.typography.titleLarge, color = TextHigh)
        Text(
            "Create your first group to start.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMid,
        )
    }
}
