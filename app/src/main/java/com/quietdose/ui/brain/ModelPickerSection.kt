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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.quietdose.brain.BrainProvider
import com.quietdose.brain.DeviceCapability
import com.quietdose.brain.LlmBrain
import com.quietdose.brain.ModelAuth
import com.quietdose.brain.ModelManager
import com.quietdose.brain.OnDeviceModel
import com.quietdose.di.ServiceLocator
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
import kotlinx.coroutines.withTimeoutOrNull
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
    var importing by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    // The model whose gated files are open in the in-app browser, if any.
    var browserModel by remember { mutableStateOf<OnDeviceModel?>(null) }

    // Optional Hugging Face token + Kaggle credentials for gated downloads. Loaded once.
    var hfToken by remember { mutableStateOf("") }
    var kaggleUser by remember { mutableStateOf("") }
    var kaggleKey by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        hfToken = ModelAuth.tokenFlow(context).first().orEmpty()
        val creds = ModelAuth.kaggleFlow(context).first()
        kaggleUser = creds.username
        kaggleKey = creds.key
    }

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
        importing = true
        progress = -1f
        note = "Importing…"
        scope.launch {
            val result = ModelManager.importFrom(context, uri) { p -> progress = p }
            busy = false
            importing = false
            refreshInstalled()
            note = if (result.isSuccess) {
                installedNote(context)
            } else {
                result.exceptionOrNull()?.message ?: "Import failed."
            }
        }
    }

    fun startDownload(model: OnDeviceModel, source: ModelManager.Source) {
        if (busy) return
        busy = true
        downloadingId = model.id
        progress = 0f
        val sourceLabel = if (source == ModelManager.Source.KAGGLE) "Kaggle" else "Hugging Face"
        note = "Downloading ${model.displayName} from $sourceLabel…"
        val token = hfToken.trim().ifEmpty { null }
        val creds = ModelAuth.KaggleCreds(kaggleUser.trim(), kaggleKey.trim())
        scope.launch {
            // Persist credentials so they're remembered for next time.
            runCatching { ModelAuth.setToken(context, token) }
            runCatching { ModelAuth.setKaggle(context, creds.username, creds.key) }
            val result = ModelManager.download(context, model, source, token, creds) { p -> progress = p }
            busy = false
            downloadingId = null
            refreshInstalled()
            note = if (result.isSuccess) {
                installedNote(context)
            } else {
                result.exceptionOrNull()?.message ?: "Download failed — open the page and import instead."
            }
        }
    }

    /** Download a file the in-app browser captured (with its session cookie). */
    fun startBrowserDownload(model: OnDeviceModel, url: String, cookie: String?, userAgent: String?) {
        if (busy) return
        busy = true
        downloadingId = model.id
        progress = -1f
        note = "Downloading ${model.displayName}…"
        scope.launch {
            val result = ModelManager.downloadFromBrowser(context, url, cookie, userAgent) { p -> progress = p }
            busy = false
            downloadingId = null
            refreshInstalled()
            note = if (result.isSuccess) {
                installedNote(context)
            } else {
                result.exceptionOrNull()?.message ?: "Download failed — try again in the browser."
            }
        }
    }

    /** The page to open in the in-app browser — the repo's file tree for HF. */
    fun browseUrl(model: OnDeviceModel): String {
        val page = model.sourcePageUrl
        return if (page.contains("huggingface.co") && !page.contains("/tree/")) "$page/tree/main" else page
    }

    /** Save the HF token, then immediately download the best-fit downloadable model. */
    fun saveTokenAndDownload() {
        if (busy) return
        val target = recommended.firstOrNull { it.directUrl != null }
        scope.launch { runCatching { ModelAuth.setToken(context, hfToken.trim().ifEmpty { null }) } }
        if (target == null) {
            note = "Token saved. No Hugging Face direct download fits this device — use Kaggle or import."
            return
        }
        startDownload(target, ModelManager.Source.HUGGING_FACE)
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

            // Gated-model access. Both sources need a one-time licence acceptance on
            // the model's page; after that, credentials let the in-app Download fetch
            // the file. Credentials stay on this device.
            Text(
                "Gated downloads",
                style = MaterialTheme.typography.titleMedium,
                color = TextHigh,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Accept the licence on the model's page once, then download from either source. Credentials stay on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
            )

            // --- Hugging Face ---
            Spacer(Modifier.height(12.dp))
            Text("Hugging Face token", style = MaterialTheme.typography.labelLarge, color = TextMid)
            Spacer(Modifier.height(6.dp))
            TokenField(value = hfToken, onValueChange = { hfToken = it }, placeholder = "hf_… (token)")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    label = "Create token",
                    onClick = { openUrl(context, "https://huggingface.co/settings/tokens") },
                )
                PillButton(
                    label = "Save & download",
                    enabled = !busy && hfToken.isNotBlank(),
                    onClick = { saveTokenAndDownload() },
                )
            }

            // --- Kaggle ---
            Spacer(Modifier.height(14.dp))
            Text("Kaggle username + key", style = MaterialTheme.typography.labelLarge, color = TextMid)
            Spacer(Modifier.height(6.dp))
            TokenField(value = kaggleUser, onValueChange = { kaggleUser = it }, placeholder = "Kaggle username", mask = false)
            Spacer(Modifier.height(6.dp))
            TokenField(value = kaggleKey, onValueChange = { kaggleKey = it }, placeholder = "Kaggle API key")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    label = "Create key",
                    onClick = { openUrl(context, "https://www.kaggle.com/settings/account") },
                )
                GhostButton(
                    label = "Save",
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            runCatching { ModelAuth.setKaggle(context, kaggleUser.trim(), kaggleKey.trim()) }
                            note = if (kaggleUser.isBlank() && kaggleKey.isBlank()) {
                                "Kaggle credentials cleared."
                            } else {
                                "Kaggle credentials saved. Tap “Kaggle” on a model to download."
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(14.dp))
            HairLine()
            Spacer(Modifier.height(14.dp))

            // Recommended — one clear pick, the rest folded away.
            val primary = recommended.firstOrNull()
            val others = recommended.drop(1)
            Text(
                "Recommended for your device",
                style = MaterialTheme.typography.titleMedium,
                color = TextHigh,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            if (primary == null) {
                Text(
                    "No catalog model comfortably fits this device. You can still import a .task file you trust.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                )
            } else {
                ModelCard(
                    model = primary,
                    enabled = !busy,
                    downloading = downloadingId == primary.id,
                    progress = progress,
                    onBrowse = { browserModel = primary },
                    onDirect = { startDownload(primary, ModelManager.Source.HUGGING_FACE) },
                )
            }

            // Other models (the rest of the recommended + the heavier ones), folded.
            val otherCount = others.size + tooLarge.size
            if (otherCount > 0) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { moreExpanded = !moreExpanded }
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        "More models ($otherCount)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextMid,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (moreExpanded) "Hide" else "Show",
                        style = MaterialTheme.typography.labelLarge,
                        color = Accent,
                    )
                }
                AnimatedVisibility(visible = moreExpanded) {
                    Column {
                        others.forEach { model ->
                            Spacer(Modifier.height(8.dp))
                            ModelCard(
                                model = model,
                                enabled = !busy,
                                downloading = downloadingId == model.id,
                                progress = progress,
                                onBrowse = { browserModel = model },
                                onDirect = { startDownload(model, ModelManager.Source.HUGGING_FACE) },
                            )
                        }
                        tooLarge.forEach { entry ->
                            Spacer(Modifier.height(8.dp))
                            TooLargeCard(
                                model = entry.model,
                                reason = entry.reason,
                                onBrowse = { browserModel = entry.model },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Import any file
            PillButton(
                label = if (importing) "Importing…" else "Import file…",
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    runCatching {
                        importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    }.onFailure { note = "Couldn't open the file picker." }
                },
            )
            if (importing) {
                Spacer(Modifier.height(8.dp))
                if (progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent,
                        trackColor = Outline,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(progress * 100).toInt()}% · extracting on-device",
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
                    Text("Reading file…", style = MaterialTheme.typography.bodySmall, color = TextMid)
                }
            }

            // Test whether the installed model actually loads + infers.
            if (installed != null) {
                Spacer(Modifier.height(8.dp))
                GhostButton(
                    label = if (busy) "Working…" else "Test model",
                    enabled = !busy,
                    color = Accent,
                    onClick = {
                        busy = true
                        note = "Testing the model… the first load can take a minute."
                        scope.launch {
                            try {
                                // Fresh engine each test, so a previous failed attempt
                                // doesn't poison the result.
                                BrainProvider.reset()
                                val brain = ServiceLocator.brain(context)
                                val hasFile = ModelManager.installedModelInfo(context) != null
                                if (brain is LlmBrain && hasFile) {
                                    // Hard cap so a too-large or unsupported model can never
                                    // leave the screen stuck on "Testing…".
                                    val reply = withTimeoutOrNull(180_000L) {
                                        runCatching { brain.probe("Reply with one short word.") }.getOrDefault("")
                                    }
                                    note = when {
                                        reply == null ->
                                            "Timed out after 3 min — this model is likely too large for this device."
                                        reply.isNotBlank() ->
                                            "Working ✓ — the model replied: “$reply”"
                                        else -> {
                                            val err = brain.lastError()
                                            when {
                                                err == null ->
                                                    "Installed but produced no output — this .task may not be " +
                                                        "supported by the on-device runtime. Try Gemma 3 1B int4."
                                                err.contains("zip", ignoreCase = true) ->
                                                    "This file isn't a MediaPipe .task bundle — it looks like a raw " +
                                                        ".tflite. You need a .task (a zip). Download Gemma 3 1B int4 .task."
                                                else -> "Couldn't run this model — $err  Try a Gemma 3 1B int4 .task."
                                            }
                                        }
                                    }
                                } else {
                                    note = "No model installed — import a .task or download one above."
                                }
                            } finally {
                                busy = false
                            }
                        }
                    },
                )
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

    // In-app browser for gated downloads: the user signs in / accepts the licence,
    // taps the file, and we fetch it ourselves (progress + verify) via its session.
    browserModel?.let { model ->
        ModelBrowserDialog(
            startUrl = browseUrl(model),
            title = model.displayName,
            onClose = { browserModel = null },
            onDownload = { url, cookie, ua ->
                browserModel = null
                startBrowserDownload(model, url, cookie, ua)
            },
        )
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
    onBrowse: () -> Unit,
    onDirect: () -> Unit,
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
                    // Primary: in-app browser — sign in + accept + download, all controlled here.
                    PillButton(label = "Download in app", enabled = enabled, onClick = onBrowse)
                    // Secondary: direct fetch with a saved token, when a direct link exists.
                    if (model.directUrl != null) {
                        GhostButton(label = "Direct (token)", enabled = enabled, onClick = onDirect)
                    }
                }
            }
        }
    }
}

@Composable
private fun TooLargeCard(model: OnDeviceModel, reason: String, onBrowse: () -> Unit) {
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
            GhostButton(label = "Download in app", color = TextLow, onClick = onBrowse)
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
private fun TokenField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    mask: Boolean = true,
) {
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
            visualTransformation = if (mask) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLow,
                    )
                }
                inner()
            },
        )
    }
}

/** Post-install note: warn immediately if the file isn't a loadable .task bundle. */
private fun installedNote(context: android.content.Context): String =
    if (ModelManager.installedLooksLoadable(context) == false) {
        "Installed, but this isn't a .task bundle — it looks like a raw .tflite/.litertlm. This build " +
            "loads .task (a zip). Try the Gemma 3 1B int4 .task."
    } else {
        "Model installed. Tap “Test model” to confirm it runs."
    }

/** Open an external URL in the browser; failures are silently ignored. */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
