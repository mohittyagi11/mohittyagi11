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
 * Capture-only: camera → review (multi-shot) → OCR. When the label is read it hands
 * the draft + recognized text back via [onScanned]; the shared confirm + analysis
 * (in AddItemActivity) takes over from there. No confirm/analysis lives here anymore,
 * so every channel uses the one flow.
 */
@Composable
fun ScanScreen(
    onScanned: (DraftItem, String, Int) -> Unit,
    onClose: () -> Unit,
    vm: ScanViewModel = viewModel(),
) {
    val phase by vm.phase.collectAsStateWithLifecycle()

    // Hold the Uri we asked the camera to write to so onCaptured can read it back.
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }

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

            is ScanViewModel.Phase.Confirm -> {
                // Hand off to the shared confirm/analysis flow.
                LaunchedEffect(p) { onScanned(p.draft, p.sourceText, p.shotCount) }
                CenterStatus(
                    icon = { CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(36.dp)) },
                    title = "Reading the labels…",
                    body = "On-device only. Nothing leaves your phone.",
                    onClose = onClose,
                )
            }
        }
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
