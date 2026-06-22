package com.quietdose.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.quietdose.brain.BrainProvider
import com.quietdose.brain.MediaPipeLlmEngine
import com.quietdose.data.settings.Settings
import com.quietdose.data.source.DaySourceKind
import com.quietdose.di.ServiceLocator
import com.quietdose.trigger.TriggerEngine
import com.quietdose.trigger.TriggerPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Drives the Settings / onboarding surface. Pure wiring: it activates already-
 * built on-device capabilities (permissions, home/wake window, day sources, the
 * on-device brain model) and never touches the network.
 *
 * Permission *status* is read on demand (it can only change while we're paused,
 * via a system screen), so the screen calls [refreshStatus] on resume rather than
 * observing a flow. Settings themselves are reactive via [SettingsStore].
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = ServiceLocator.settings(app)
    private val daySources = ServiceLocator.daySources(app)

    /** Reactive scalar settings (home, wake window, …). */
    val settings: StateFlow<Settings> =
        settingsStore.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            Settings(),
        )

    private val _permissions = MutableStateFlow(TriggerPermissions.status(app))
    /** Snapshot of every runtime / special permission, refreshed on resume. */
    val permissions: StateFlow<TriggerPermissions.Status> = _permissions.asStateFlow()

    private val _sources = MutableStateFlow(currentSources())
    /** Per-[DaySourceKind] enabled flags, mirrored from the registry. */
    val sources: StateFlow<Map<DaySourceKind, Boolean>> = _sources.asStateFlow()

    private val _modelLoaded = MutableStateFlow(modelFile().exists())
    /** Whether an on-device brain model file is present on disk. */
    val modelLoaded: StateFlow<Boolean> = _modelLoaded.asStateFlow()

    private val _healthConnectGranted = MutableStateFlow(false)
    /**
     * Whether Health Connect sleep-read is granted. Unlike normal permissions
     * this can only be read asynchronously (via the HC permission controller), so
     * it lives in its own flow, refreshed off the main thread on every resume.
     */
    val healthConnectGranted: StateFlow<Boolean> = _healthConnectGranted.asStateFlow()

    private val _busy = MutableStateFlow(false)
    /** True while a long action (model import/remove) is running. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private fun currentSources(): Map<DaySourceKind, Boolean> =
        DaySourceKind.entries.associateWith { daySources.isEnabled(it) }

    private fun modelFile(): File =
        File(getApplication<Application>().filesDir, MediaPipeLlmEngine.DEFAULT_MODEL_NAME)

    init {
        // Health Connect grant is async; seed it once at construction.
        refreshHealthConnect()
    }

    // ---- Lifecycle hooks -------------------------------------------------

    /** Re-read permission status, source toggles and model presence (call on resume). */
    fun refreshStatus() {
        _permissions.value = TriggerPermissions.status(getApplication())
        _sources.value = currentSources()
        _modelLoaded.value = modelFile().exists()
        refreshHealthConnect()
    }

    private fun refreshHealthConnect() = viewModelScope.launch {
        _healthConnectGranted.value = runCatching {
            TriggerPermissions.hasHealthConnectSleep(getApplication())
        }.getOrDefault(false)
    }

    /** Re-arm alarms/geofence/sensors after a grant or home change. Never throws. */
    fun reArm() = viewModelScope.launch {
        refreshStatus()
        runCatching { TriggerEngine.reArmAll(getApplication()) }
    }

    // ---- Home & wake -----------------------------------------------------

    /**
     * Use the device's last known location as home. Guarded by FINE location
     * permission; a null/failed fix is a no-op. Saves via [SettingsStore.setHome]
     * then refreshes the geofence. [onResult] reports success for UI feedback.
     */
    @SuppressLint("MissingPermission") // gated by hasFineLocation
    fun useCurrentLocationAsHome(radiusM: Float, onResult: (Boolean) -> Unit) {
        val app = getApplication<Application>()
        if (!TriggerPermissions.hasFineLocation(app)) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val loc = runCatching { lastLocation() }.getOrNull()
            if (loc == null) {
                onResult(false)
                return@launch
            }
            settingsStore.setHome(loc.first, loc.second, radiusM)
            runCatching { TriggerEngine.refreshGeofence(app) }
            onResult(true)
        }
    }

    /** Re-save home at the current lat/lng with a new radius, then refresh geofence. */
    fun setHomeRadius(radiusM: Float) {
        val s = settings.value
        if (s.homeLat == 0.0 && s.homeLng == 0.0) return
        viewModelScope.launch {
            settingsStore.setHome(s.homeLat, s.homeLng, radiusM)
            runCatching { TriggerEngine.refreshGeofence(getApplication()) }
        }
    }

    fun setWakeWindow(earliestHour: Int, latestFallbackHour: Int) = viewModelScope.launch {
        settingsStore.setWakeWindow(
            earliestHour.coerceIn(0, 23),
            latestFallbackHour.coerceIn(0, 23),
        )
        runCatching { TriggerEngine.reArmAll(getApplication()) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastLocation(): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            val client = LocationServices.getFusedLocationProviderClient(getApplication())
            client.lastLocation
                .addOnSuccessListener { loc ->
                    cont.resume(loc?.let { it.latitude to it.longitude })
                }
                .addOnFailureListener { cont.resume(null) }
        }

    // ---- Data sources ----------------------------------------------------

    fun setSourceEnabled(kind: DaySourceKind, enabled: Boolean) {
        daySources.setEnabled(kind, enabled)
        _sources.value = currentSources()
    }

    // ---- Brain (on-device model) -----------------------------------------

    /** Copy the picked model file into filesDir/brain.task off the main thread. */
    fun importModel(uri: Uri, onResult: (Boolean) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val dest = File(app.filesDir, MediaPipeLlmEngine.DEFAULT_MODEL_NAME)
                    val tmp = File(app.filesDir, "${MediaPipeLlmEngine.DEFAULT_MODEL_NAME}.part")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Could not open source")
                    if (dest.exists()) dest.delete()
                    check(tmp.renameTo(dest)) { "Could not move model into place" }
                }.isSuccess
            }
            if (ok) BrainProvider.reset()
            _modelLoaded.value = modelFile().exists()
            _busy.value = false
            onResult(ok)
        }
    }

    /** Delete the on-device model file and reset the brain back to heuristic. */
    fun removeModel(onResult: (Boolean) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val f = modelFile()
                    if (f.exists()) f.delete() else true
                }.getOrDefault(false)
            }
            BrainProvider.reset()
            _modelLoaded.value = modelFile().exists()
            _busy.value = false
            onResult(ok)
        }
    }

    /** Convenience set of currently-needed runtime permissions (for batch requests). */
    val runtimePermissions: List<String> = TriggerPermissions.runtimePermissions

    companion object {
        val FINE_LOCATION: String = Manifest.permission.ACCESS_FINE_LOCATION
        val BACKGROUND_LOCATION: String = Manifest.permission.ACCESS_BACKGROUND_LOCATION
        val ACTIVITY_RECOGNITION: String = Manifest.permission.ACTIVITY_RECOGNITION
    }
}
