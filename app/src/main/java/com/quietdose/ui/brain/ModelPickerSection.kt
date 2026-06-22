package com.quietdose.ui.brain

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.quietdose.brain.DeviceCapability
import com.quietdose.brain.ModelAuth
import com.quietdose.brain.ModelManager
import com.quietdose.brain.OnDeviceModel
import kotlinx.coroutines.flow.first
import com.quietdose.ui.theme.Accent
import com.quietdose.ui.theme.Done
import com.quietdose.ui.theme.Outline
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.Surface2
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * A self-contained on-device model catalog + picker. Drop it into the Settings
 * "Brain" card (see INTEGRATION.md). Shows device RAM and current model status,
 * a "Recommended for your device" list (download / get-page / import), a
 * collapsed "Heavier than this device" list with reasons, and a Remove action.
 *
 * All IO runs through [ModelManager] (suspend / off the main thread). Nothing
 * here crashes on network or permission failure — failures surface as a note.
 */
@Composable
fun ModelPickerSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Installed model: re-read on (re)composition and after install/remove.
    var installed by remember {
        mutableStateOf(ModelManager.installedModelInfo(context))
    }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Per-model download progress: id -> 0f..1f (or -1f when size unknown).
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var heavierExpanded by remember { mutableStateOf(false) }

    // Optional Hugging Face token for gated model downloads. Loaded once.
    var hfToken by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { hfToken = ModelAuth.tokenFlow(context).first().orEmpty() }

    val totalRamMb = remember { DeviceCapability.totalRamMb(context) }
    val recommended = remember { DeviceCapability.recommended(context) }
    val tooLarge = remember { DeviceCapability.tooLarge(context) }

    fun refreshInstalled() {
        installed = ModelManager.installedModelInfo(context)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        note = "Importing…"
        scope.launch {
            val result = ModelManager.importFrom(context, uri)
            busy = false
            refreshInstalled()
            note = if (result.isSuccess) "Model installed." else "Import failed."
        }
    }

    fun openPage(model: OnDeviceModel) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(model.sourcePageUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { note = "Couldn't open the model page." }
    }

    fun startDownload(model: OnDeviceModel) {
        if (busy) return
        busy = true
        downloadingId = model.id
        progress = 0f
        note = "Downloading ${model.displayName}…"
        val token = hfToken.trim().ifEmpty { null }
        scope.launch {
            // Persist the token so it's remembered for next time.
            runCatching { ModelAuth.setToken(context, token) }
            val result = ModelManager.download(context, model, token) { p -> progress = p }
            busy = false
            downloadingId = null
            refreshInstalled()
            note = if (result.isSuccess) {
                "Model installed."
            } else {
                result.exceptionOrNull()?.message ?: "Download failed — try \"Get model\" instead."
            }
        }
    }

    Surface(color = Surface1, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "ON-DEVICE MODEL",
                style = MaterialTheme.typography.labelSmall,
                color = TextLow,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick a model that fits this device. Inference stays on-device; only the model file is downloaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid,
            )

            Spacer(Modifier.height(14.dp))
            DeviceStatusRow(totalRamMb = totalRamMb, installed = installed)

            Spacer(Modifier.height(14.dp))
            HairLine()
            Spacer(Modifier.height(14.dp))

            // Gated-model access: a Hugging Face token lets the in-app Download
            // fetch license-gated files directly (accept the licence once on the
            // page, then it just works — same as the curl command's auth header).
            Text(
                "Gated downloads",
                style = MaterialTheme.typography.titleMedium,
                color = TextHigh,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Most models need a one-time licence acceptance on their page. Paste a Hugging Face token here and in-app Download works; the token stays on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
            )
            Spacer(Modifier.height(8.dp))
            TokenField(value = hfToken, onValueChange = { hfToken = it })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    label = "Create token",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/settings/tokens"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
                GhostButton(
                    label = if (hfToken.isBlank()) "Save" else "Save token",
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            runCatching { ModelAuth.setToken(context, hfToken.trim().ifEmpty { null }) }
                            note = if (hfToken.isBlank()) "Token cleared." else "Token saved."
                        }
                    },
                )
            }

            Spacer(Modifier.height(14.dp))
            HairLine()
            Spacer(Modifier.height(14.dp))

            // Recommended
            Text(
                "Recommended for your device",
                style = MaterialTheme.typography.titleMedium,
                color = TextHigh,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            if (recommended.isEmpty()) {
                Text(
                    "No catalog model comfortably fits this device. You can still import a .task file you trust.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                )
            } else {
                recommended.forEach { model ->
                    ModelCard(
                        model = model,
                        enabled = !busy,
                        downloading = downloadingId == model.id,
                        progress = progress,
                        onDownload = { startDownload(model) },
                        onGetPage = { openPage(model) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(6.dp))

            // Import any file
            PillButton(
                label = if (busy && downloadingId == null) "Working…" else "Import file…",
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    runCatching {
                        importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    }.onFailure { note = "Couldn't open the file picker." }
                },
            )

            // Heavier than this device (collapsed)
            if (tooLarge.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                HairLine()
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { heavierExpanded = !heavierExpanded }
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        "Heavier than this device (${tooLarge.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextMid,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (heavierExpanded) "Hide" else "Show",
                        style = MaterialTheme.typography.labelLarge,
                        color = Accent,
                    )
                }
                AnimatedVisibility(visible = heavierExpanded) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        tooLarge.forEach { entry ->
                            TooLargeCard(
                                model = entry.model,
                                reason = entry.reason,
                                onGetPage = { openPage(entry.model) },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Remove
            if (installed != null) {
                Spacer(Modifier.height(8.dp))
                GhostButton(
                    label = "Remove installed model",
                    enabled = !busy,
                    color = TextMid,
                    onClick = {
                        ModelManager.remove(context)
                        refreshInstalled()
                        note = "Model removed."
                    },
                )
            }

            if (note != null) {
                Spacer(Modifier.height(8.dp))
                Text(note ?: "", style = MaterialTheme.typography.bodyMedium, color = TextLow)
            }
        }
    }
}

/* ----------------------------- Pieces ----------------------------- */

@Composable
private fun DeviceStatusRow(totalRamMb: Int, installed: ModelManager.InstalledModel?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (installed != null) Done else TextLow, CircleShape),
        )
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (installed != null) "Model installed" else "No model — using built-in heuristic",
                style = MaterialTheme.typography.titleMedium,
                color = if (installed != null) TextHigh else TextMid,
            )
            val ramLabel =
                if (totalRamMb > 0) "${formatGb(totalRamMb.toLong() * 1024L * 1024L)} RAM" else "RAM unknown"
            val detail = if (installed != null) {
                "$ramLabel · ${formatBytes(installed.sizeBytes)} on disk"
            } else {
                ramLabel
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
    }
}

@Composable
private fun ModelCard(
    model: OnDeviceModel,
    enabled: Boolean,
    downloading: Boolean,
    progress: Float,
    onDownload: () -> Unit,
    onGetPage: () -> Unit,
) {
    Surface(color = Surface2, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                model.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = TextHigh,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${formatBytes(model.approxBytes)} · ${model.backend} · ${model.license}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid,
            )
            if (model.notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(model.notes, style = MaterialTheme.typography.bodySmall, color = TextLow)
            }

            Spacer(Modifier.height(10.dp))

            if (downloading) {
                if (progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent,
                        trackColor = Outline,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMid,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent,
                        trackColor = Outline,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Downloading…", style = MaterialTheme.typography.bodySmall, color = TextMid)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (model.directUrl != null) {
                        PillButton(label = "Download", enabled = enabled, onClick = onDownload)
                        GhostButton(label = "Open page", enabled = enabled, onClick = onGetPage)
                    } else {
                        // No safe direct link — must obtain from the page, then import.
                        PillButton(label = "Get model", enabled = enabled, onClick = onGetPage)
                    }
                }
            }
        }
    }
}

@Composable
private fun TooLargeCard(model: OnDeviceModel, reason: String, onGetPage: () -> Unit) {
    Surface(color = Surface2.copy(alpha = 0.5f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                model.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = TextLow,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${formatBytes(model.approxBytes)} · needs ~${model.minRamMb / 1024} GB RAM",
                style = MaterialTheme.typography.bodyMedium,
                color = TextLow,
            )
            Spacer(Modifier.height(4.dp))
            Text(reason, style = MaterialTheme.typography.bodySmall, color = TextLow)
            Spacer(Modifier.height(8.dp))
            GhostButton(label = "Open page", color = TextLow, onClick = onGetPage)
        }
    }
}

/* ----------------------------- Local buttons / misc ----------------------------- */
// Kept local so this section is self-contained and doesn't couple to the
// settings package (its button helpers are private to that screen's style).

@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Accent,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) tint.copy(alpha = 0.18f) else Surface2)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) tint else TextLow,
        )
    }
}

@Composable
private fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = TextMid,
) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) color else TextLow,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun TokenField(value: String, onValueChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextHigh),
            cursorBrush = SolidColor(Accent),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "hf_… (Hugging Face token)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLow,
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun HairLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Outline))
}

/* ----------------------------- Formatting ----------------------------- */

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val gb = 1024L * 1024L * 1024L
    val mb = 1024L * 1024L
    return when {
        bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / gb)
        bytes >= mb -> String.format(Locale.US, "%.0f MB", bytes.toDouble() / mb)
        else -> String.format(Locale.US, "%d KB", bytes / 1024L)
    }
}

private fun formatGb(bytes: Long): String {
    val gb = 1024L * 1024L * 1024L
    return String.format(Locale.US, "%.1f GB", bytes.toDouble() / gb)
}
