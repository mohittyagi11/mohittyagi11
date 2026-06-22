package com.quietdose.ui.scan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quietdose.brain.skills.DraftItem
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.ItemType
import com.quietdose.ui.analysis.AnalysisScreen
import com.quietdose.ui.icons.ItemIcon
import com.quietdose.ui.stack.ChipGroup
import com.quietdose.ui.stack.EditorSection
import com.quietdose.ui.stack.FieldLabel
import com.quietdose.ui.stack.FilledButton
import com.quietdose.ui.stack.StackTextField
import com.quietdose.ui.stack.TextButtonGhost
import com.quietdose.ui.stack.androidxClickable
import com.quietdose.ui.stack.label
import com.quietdose.ui.theme.Accent
import com.quietdose.ui.theme.GroupStyle
import com.quietdose.ui.theme.Ink
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.Surface2
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid

/**
 * Full-screen camera-scan flow: launch the system camera, run on-device OCR +
 * barcode + the agent, then confirm a prefilled item and save it into a group.
 *
 * Entry point for the feature — host it however suits (a full-screen route, a
 * dialog, or a sheet). It owns its own [ScanViewModel].
 *
 * @param onClose dismiss without saving (also called on "X" and on cancel).
 * @param onSaved called after the item is persisted; the host should dismiss.
 */
@Composable
fun ScanScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    vm: ScanViewModel = viewModel(),
) {
    val phase by vm.phase.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()

    LaunchedEffect(saved) { if (saved) onSaved() }

    // Hold the Uri we asked the camera to write to so onCaptured can read it back.
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    // A confirmed draft awaiting contextual analysis before it's saved.
    var pendingItem by remember { mutableStateOf<ItemEntity?>(null) }
    // The recognized label text for that draft, so the analysis can ingredientize it.
    var pendingText by remember { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingUri
        if (success && uri != null) vm.onCaptured(uri) else vm.onCaptureCancelled()
    }

    fun launchCamera() {
        val uri = runCatching { vm.newCaptureUri() }.getOrNull()
        if (uri == null) {
            vm.onCaptureCancelled()
            return
        }
        pendingUri = uri
        runCatching { cameraLauncher.launch(uri) }.onFailure { vm.onCaptureCancelled() }
    }

    fun startOver() {
        vm.reset()
        launchCamera()
    }

    // Auto-open the camera the first time the screen appears.
    LaunchedEffect(Unit) { launchCamera() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding(),
    ) {
        when (val p = phase) {
            is ScanViewModel.Phase.Capturing -> CenterStatus(
                icon = { CameraGlyph() },
                title = "Opening camera…",
                body = "Frame the supplement label or barcode.",
                onClose = onClose,
            )

            is ScanViewModel.Phase.Reviewing -> ReviewContent(
                shots = p.shots,
                canAddMore = p.shots < ScanViewModel.MAX_SHOTS,
                onAddAnother = ::launchCamera,
                onUse = vm::useShots,
                onStartOver = ::startOver,
                onClose = onClose,
            )

            is ScanViewModel.Phase.Working -> CenterStatus(
                icon = { CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(36.dp)) },
                title = "Reading the labels…",
                body = "On-device only. Nothing leaves your phone.",
                onClose = onClose,
            )

            is ScanViewModel.Phase.Empty -> CenterStatus(
                icon = { CameraGlyph() },
                title = "Nothing to add yet",
                body = p.reason,
                onClose = onClose,
                primary = "Retake" to ::startOver,
            )

            is ScanViewModel.Phase.Confirm -> ConfirmContent(
                draft = p.draft,
                fromModel = p.fromModel,
                barcode = p.barcode,
                groups = groups,
                onClose = onClose,
                onRetake = ::startOver,
                // Route a scanned item through the same contextual analysis.
                onSave = { pendingItem = it; pendingText = p.sourceText },
            )
        }
    }

    pendingItem?.let { item ->
        AnalysisScreen(
            item = item,
            onAdd = { configured -> vm.save(configured) },
            onDismiss = { pendingItem = null },
            product = com.quietdose.brain.analysis.ProductSignals(ingredientsText = pendingText.ifBlank { null }),
        )
    }
}

/* ----------------------------- Status states ----------------------------- */

@Composable
private fun CameraGlyph() {
    val t = rememberInfiniteTransition(label = "scan")
    val a by t.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(72.dp).clip(CircleShape).background(Accent.copy(alpha = 0.12f)),
    ) {
        Icon(
            Icons.Rounded.PhotoCamera,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(34.dp).graphicsLayer { alpha = a },
        )
    }
}

@Composable
private fun CenterStatus(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    onClose: () -> Unit,
    primary: Pair<String, () -> Unit>? = null,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Scan", onClose = onClose)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        ) {
            icon()
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = TextHigh)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid,
                modifier = Modifier.fillMaxWidth(),
            )
            if (primary != null) {
                Spacer(Modifier.height(28.dp))
                FilledButton(
                    label = primary.first,
                    accent = Accent,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = primary.second,
                )
            }
        }
    }
}

@Composable
private fun TopBar(title: String, onClose: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
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
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextHigh)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/* ----------------------------- Review shots ----------------------------- */

@Composable
private fun ReviewContent(
    shots: Int,
    canAddMore: Boolean,
    onAddAnother: () -> Unit,
    onUse: () -> Unit,
    onStartOver: () -> Unit,
    onClose: () -> Unit,
) {
    val labels = listOf("Front", "Back", "Extra")
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Review shots", onClose = onClose)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 20.dp),
        ) {
            Text("Capture front and back", style = MaterialTheme.typography.headlineSmall, color = TextHigh)
            Text(
                "Two or three angles read the label far better — the front for the name, the back for dose and ingredients.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                for (i in 0 until ScanViewModel.MAX_SHOTS) {
                    val kind = when {
                        i < shots -> ShotKind.FILLED
                        i == shots && canAddMore -> ShotKind.ADD
                        else -> ShotKind.EMPTY
                    }
                    ShotTile(
                        modifier = Modifier.weight(1f),
                        label = labels.getOrElse(i) { "Shot" },
                        kind = kind,
                        onClick = if (kind == ShotKind.ADD) onAddAnother else null,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            FilledButton(
                label = if (shots >= 2) "Use $shots photos" else "Use this photo",
                accent = Accent,
                enabled = shots >= 1,
                modifier = Modifier.fillMaxWidth(),
                onClick = onUse,
            )
            if (shots == 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tip: add the back too — that's where dose and ingredients are.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButtonGhost("Start over", color = TextLow, onClick = onStartOver)
            }
        }
    }
}

private enum class ShotKind { FILLED, ADD, EMPTY }

@Composable
private fun ShotTile(modifier: Modifier, label: String, kind: ShotKind, onClick: (() -> Unit)?) {
    val base = modifier.height(96.dp).clip(RoundedCornerShape(14.dp))
    val withClick = if (onClick != null) base.androidxClickable(onClick) else base
    Box(
        contentAlignment = Alignment.Center,
        modifier = withClick.background(
            when (kind) {
                ShotKind.FILLED -> Accent.copy(alpha = 0.16f)
                ShotKind.ADD -> Surface2
                ShotKind.EMPTY -> Surface2.copy(alpha = 0.4f)
            },
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (kind) {
                ShotKind.FILLED -> {
                    Text("✓", style = MaterialTheme.typography.titleLarge, color = Accent)
                    Text(label, style = MaterialTheme.typography.labelMedium, color = TextHigh)
                }
                ShotKind.ADD -> {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = "Add photo", tint = Accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelMedium, color = Accent)
                }
                ShotKind.EMPTY -> Text(label, style = MaterialTheme.typography.labelMedium, color = TextLow)
            }
        }
    }
}

/* ----------------------------- Confirm screen ----------------------------- */

@Composable
private fun ConfirmContent(
    draft: DraftItem,
    fromModel: Boolean,
    barcode: String?,
    groups: List<GroupEntity>,
    onClose: () -> Unit,
    onRetake: () -> Unit,
    onSave: (ItemEntity) -> Unit,
) {
    var name by remember { mutableStateOf(draft.name) }
    var brand by remember { mutableStateOf(draft.brand ?: "") }
    var category by remember { mutableStateOf(draft.category ?: "") }
    var type by remember { mutableStateOf(draft.type) }
    var doseAmount by remember { mutableStateOf(formatAmount(draft.doseAmount)) }
    var doseUnit by remember { mutableStateOf(draft.doseUnit) }
    var note by remember { mutableStateOf(draft.note ?: "") }

    var selectedGroupId by remember {
        mutableStateOf(groups.firstOrNull()?.id)
    }
    // If groups arrive after first composition, default to the first one.
    LaunchedEffect(groups) {
        if (selectedGroupId == null) selectedGroupId = groups.firstOrNull()?.id
    }

    val selectedGroup = groups.firstOrNull { it.id == selectedGroupId }
    val accent = selectedGroup?.let { GroupStyle.tint(it) } ?: Accent

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "Confirm",
            onClose = onClose,
            trailing = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp).clip(CircleShape).androidxClickable(onRetake),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Retake", tint = TextMid, modifier = Modifier.size(22.dp))
                }
            },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            ScanPreview(type = type, tint = accent, title = name.ifBlank { "New item" }, fromModel = fromModel)

            if (!barcode.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                BarcodeChip(barcode)
            }

            EditorSection("Name") {
                StackTextField(name, { name = it }, "Vitamin D, Magnesium…", accent = accent)
            }

            EditorSection("Brand & category") {
                StackTextField(brand, { brand = it }, "Brand (optional)", accent = accent)
                Spacer(Modifier.height(8.dp))
                StackTextField(category, { category = it }, "Category (optional)", accent = accent)
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

            EditorSection("Add to") {
                if (groups.isEmpty()) {
                    Text(
                        "No groups yet. Create a group first, then scan into it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMid,
                    )
                } else {
                    GroupPicker(
                        groups = groups,
                        selectedId = selectedGroupId,
                        onSelect = { selectedGroupId = it },
                    )
                }
            }

            EditorSection("Note") {
                StackTextField(note, { note = it }, "Anything worth remembering (optional)", accent = accent, singleLine = false)
            }

            Spacer(Modifier.height(24.dp))

            FilledButton(
                label = "Save to stack",
                accent = accent,
                enabled = name.isNotBlank() && selectedGroupId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val groupId = selectedGroupId ?: return@FilledButton
                onSave(
                    ItemEntity(
                        groupId = groupId,
                        name = name.trim(),
                        brand = brand.trim().ifBlank { null },
                        category = category.trim().ifBlank { null },
                        type = type,
                        doseAmount = doseAmount.toDoubleOrNull() ?: 1.0,
                        doseUnit = doseUnit,
                        flags = draft.flags,
                        note = note.trim().ifBlank { null },
                        createdAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButtonGhost("Retake photo", color = TextLow, onClick = onRetake)
            }
        }
    }
}

@Composable
private fun ScanPreview(type: ItemType, tint: Color, title: String, fromModel: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Surface2)
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(64.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
        ) {
            ItemIcon(type = type, tint = tint, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = TextHigh)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fromModel) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    if (fromModel) "Drafted on-device — check the details" else "Read from the label — fill in the rest",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                )
            }
        }
    }
}

@Composable
private fun BarcodeChip(barcode: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Surface1)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = TextMid, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(barcode, style = MaterialTheme.typography.bodyMedium, color = TextMid)
    }
}

@Composable
private fun GroupPicker(
    groups: List<GroupEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        groups.forEach { group ->
            val selected = group.id == selectedId
            val tint = GroupStyle.tint(group)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) tint.copy(alpha = 0.18f) else Surface2)
                    .androidxClickable { onSelect(group.id) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
                ) {
                    Icon(GroupStyle.icon(group), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) TextHigh else TextMid,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun formatAmount(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
