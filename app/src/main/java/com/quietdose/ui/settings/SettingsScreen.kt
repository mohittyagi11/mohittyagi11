package com.quietdose.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.quietdose.ui.brain.ModelPickerSection
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quietdose.data.source.DaySourceKind
import com.quietdose.trigger.TriggerPermissions
import com.quietdose.ui.theme.Done
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val healthConnectGranted by vm.healthConnectGranted.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val modelLoaded by vm.modelLoaded.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    // Permission / special-access status changes happen while we're paused (the
    // user is on a system screen). Re-read it whenever we come back to the front.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { TopRow(onBack = onBack) }
        item { PermissionsSection(vm = vm, status = permissions, healthConnectGranted = healthConnectGranted) }
        item { HomeWakeSection(vm = vm, settings = settings) }
        item { DataSourcesSection(vm = vm, sources = sources) }
        item { ModelPickerSection() }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/* ----------------------------- Top row ----------------------------- */

@Composable
private fun TopRow(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 2.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Surface1)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onBack() },
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = TextHigh,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(14.dp))
        Text("Settings", style = MaterialTheme.typography.displaySmall, color = TextHigh)
    }
}

/* ----------------------------- Permissions ----------------------------- */

@Composable
private fun PermissionsSection(
    vm: SettingsViewModel,
    status: TriggerPermissions.Status,
    healthConnectGranted: Boolean,
) {
    val context = LocalContext.current

    val notificationsLauncher = rememberLauncherForActivityResult(
        TriggerPermissions.singlePermissionContract(),
    ) { vm.reArm() }

    val activityLauncher = rememberLauncherForActivityResult(
        TriggerPermissions.singlePermissionContract(),
    ) { vm.reArm() }

    val fineLauncher = rememberLauncherForActivityResult(
        TriggerPermissions.singlePermissionContract(),
    ) { vm.reArm() }

    val specialAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { vm.reArm() }

    val healthConnectLauncher = if (status.healthConnectAvailable) {
        rememberLauncherForActivityResult(
            TriggerPermissions.healthConnectRequestContract(),
        ) { vm.reArm() }
    } else {
        null
    }

    SettingsCard(
        title = "Permissions",
        subtitle = "Granted only as you wire each capability. Nothing leaves the device.",
    ) {
        StatusRow(
            granted = status.notifications,
            title = "Notifications",
            detail = "Quiet, on-time reminders.",
            actionLabel = "Allow",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationsLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
        StatusRow(
            granted = status.activityRecognition,
            title = "Activity recognition",
            detail = "Helps infer when you've woken.",
            actionLabel = "Allow",
            onAction = { activityLauncher.launch(SettingsViewModel.ACTIVITY_RECOGNITION) },
        )
        StatusRow(
            granted = status.fineLocation,
            title = "Location",
            detail = "Set home and arrive-home reminders.",
            actionLabel = "Allow",
            onAction = { fineLauncher.launch(SettingsViewModel.FINE_LOCATION) },
        )
        // Background location must be requested on its own, after FINE, with a
        // clear rationale (platform policy). Gate the row on fine being granted.
        if (status.fineLocation) {
            StatusRow(
                granted = status.backgroundLocation,
                title = "Background location",
                detail = "So arrive-home fires while Dose is closed. Opens app settings — set Location to \"Allow all the time\".",
                actionLabel = "Open",
                onAction = {
                    runCatching {
                        specialAccessLauncher.launch(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    }
                },
            )
        }
        StatusRow(
            granted = status.batteryExempt,
            title = "Ignore battery limits",
            detail = "Optional — keeps reminders reliable in deep sleep.",
            actionLabel = "Open",
            onAction = {
                runCatching {
                    specialAccessLauncher.launch(
                        TriggerPermissions.requestBatteryExemptionIntent(context),
                    )
                }
            },
        )
        if (status.healthConnectAvailable) {
            StatusRow(
                granted = healthConnectGranted,
                title = "Health Connect — sleep",
                detail = if (healthConnectGranted) {
                    "Connected — reading last night's sleep to time your morning dose."
                } else {
                    "Reads last night's sleep to time your morning dose."
                },
                actionLabel = "Connect",
                onAction = {
                    healthConnectLauncher?.let {
                        runCatching { it.launch(TriggerPermissions.healthConnectPermissions) }
                    }
                },
            )
        }
    }
}

/* ----------------------------- Home & wake ----------------------------- */

@Composable
private fun HomeWakeSection(vm: SettingsViewModel, settings: com.quietdose.data.settings.Settings) {
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }

    val locationLauncher = rememberLauncherForActivityResult(
        TriggerPermissions.singlePermissionContract(),
    ) { granted ->
        if (granted) {
            vm.useCurrentLocationAsHome(settings.homeRadiusM) { ok ->
                note = if (ok) "Home saved." else "Couldn't read a location yet."
            }
        } else {
            note = "Location permission is needed to set home."
        }
    }

    val homeSet = settings.homeLat != 0.0 || settings.homeLng != 0.0

    SettingsCard(
        title = "Home & wake",
        subtitle = "Where home is, and the window your morning routine can land in.",
    ) {
        PillButton(
            label = "Use current location as home",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (TriggerPermissions.hasFineLocation(context)) {
                    vm.useCurrentLocationAsHome(settings.homeRadiusM) { ok ->
                        note = if (ok) "Home saved." else "Couldn't read a location yet."
                    }
                } else {
                    locationLauncher.launch(SettingsViewModel.FINE_LOCATION)
                }
            },
        )
        if (homeSet) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Home · ${"%.4f".format(settings.homeLat)}, ${"%.4f".format(settings.homeLng)}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid,
            )
        }
        if (note != null) {
            Spacer(Modifier.height(6.dp))
            Text(note ?: "", style = MaterialTheme.typography.bodyMedium, color = TextLow)
        }

        Spacer(Modifier.height(14.dp))
        HairLine()
        Spacer(Modifier.height(10.dp))

        val radius = settings.homeRadiusM.toInt()
        Stepper(
            label = "Home radius",
            valueLabel = "$radius m",
            canDecrement = homeSet && radius > 50,
            canIncrement = homeSet && radius < 500,
            onDecrement = { vm.setHomeRadius((radius - 20).coerceAtLeast(50).toFloat()) },
            onIncrement = { vm.setHomeRadius((radius + 20).coerceAtMost(500).toFloat()) },
        )

        Spacer(Modifier.height(10.dp))

        Stepper(
            label = "Earliest wake",
            valueLabel = hourLabel(settings.earliestWakeHour),
            canDecrement = settings.earliestWakeHour > 0,
            canIncrement = settings.earliestWakeHour < settings.latestWakeFallbackHour - 1,
            onDecrement = {
                vm.setWakeWindow(settings.earliestWakeHour - 1, settings.latestWakeFallbackHour)
            },
            onIncrement = {
                vm.setWakeWindow(settings.earliestWakeHour + 1, settings.latestWakeFallbackHour)
            },
        )
        Stepper(
            label = "Latest fallback",
            valueLabel = hourLabel(settings.latestWakeFallbackHour),
            canDecrement = settings.latestWakeFallbackHour > settings.earliestWakeHour + 1,
            canIncrement = settings.latestWakeFallbackHour < 23,
            onDecrement = {
                vm.setWakeWindow(settings.earliestWakeHour, settings.latestWakeFallbackHour - 1)
            },
            onIncrement = {
                vm.setWakeWindow(settings.earliestWakeHour, settings.latestWakeFallbackHour + 1)
            },
        )
    }
}

private fun hourLabel(hour: Int): String {
    val h = ((hour % 24) + 24) % 24
    val suffix = if (h < 12) "am" else "pm"
    val twelve = when (h % 12) {
        0 -> 12
        else -> h % 12
    }
    return "$twelve $suffix"
}

/* ----------------------------- Data sources ----------------------------- */

@Composable
private fun DataSourcesSection(vm: SettingsViewModel, sources: Map<DaySourceKind, Boolean>) {
    SettingsCard(
        title = "Data sources",
        subtitle = "What feeds your day graph. Each runs on-device and is independent.",
    ) {
        DaySourceKind.entries.forEachIndexed { index, kind ->
            if (index > 0) {
                Spacer(Modifier.height(6.dp))
                HairLine()
                Spacer(Modifier.height(6.dp))
            }
            val meta = sourceMeta(kind)
            ToggleRow(
                title = meta.first,
                detail = meta.second,
                checked = sources[kind] == true,
                onChange = { vm.setSourceEnabled(kind, it) },
            )
        }
    }
}

private fun sourceMeta(kind: DaySourceKind): Pair<String, String> = when (kind) {
    DaySourceKind.SUPPLEMENT -> "Supplements" to "Doses you log in Dose."
    DaySourceKind.HEALTH_CONNECT -> "Health Connect" to "Sleep and activity, read on-device."
    DaySourceKind.SKINCARE -> "Skincare" to "Topical routines through the day."
    DaySourceKind.DEVICE_USAGE -> "Device usage" to "Sustained screen time as a wake signal."
}

/* ----------------------------- Brain ----------------------------- */

@Composable
private fun BrainSection(vm: SettingsViewModel, modelLoaded: Boolean, busy: Boolean) {
    var note by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            vm.importModel(uri) { ok ->
                note = if (ok) "Model installed." else "Import failed."
            }
        }
    }

    SettingsCard(
        title = "Brain",
        subtitle = "An optional on-device model writes warmer summaries. Defaults to a built-in heuristic.",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (modelLoaded) Done else TextLow, CircleShape),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                if (modelLoaded) "Model loaded" else "No model — using heuristic",
                style = MaterialTheme.typography.titleMedium,
                color = if (modelLoaded) TextHigh else TextMid,
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                label = if (busy) "Working…" else "Import model…",
                enabled = !busy,
                onClick = {
                    // Any binary; .task files often surface as octet-stream.
                    runCatching {
                        importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    }
                },
            )
            if (modelLoaded) {
                GhostButton(
                    label = "Remove model",
                    enabled = !busy,
                    onClick = {
                        vm.removeModel { ok -> note = if (ok) "Model removed." else "Couldn't remove model." }
                    },
                )
            }
        }

        if (note != null) {
            Spacer(Modifier.height(8.dp))
            Text(note ?: "", style = MaterialTheme.typography.bodyMedium, color = TextLow)
        }
    }
}
