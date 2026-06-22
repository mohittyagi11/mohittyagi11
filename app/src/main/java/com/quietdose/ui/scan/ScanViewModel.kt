package com.quietdose.ui.scan

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quietdose.brain.skills.DraftItem
import com.quietdose.brain.vision.LabelScanner
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.ItemType
import com.quietdose.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Holds the camera → recognition → draft pipeline for [ScanScreen]. All OCR and
 * agent work runs off the main thread; recognition or model failures never crash
 * the screen — they surface as a calm [Phase.Empty] or partial prefill instead.
 */
class ScanViewModel(app: Application) : AndroidViewModel(app) {

    /** Where the scan is in its lifecycle. The UI renders one screen per phase. */
    sealed interface Phase {
        /** Waiting for the camera result (initial, and after Retake). */
        data object Capturing : Phase

        /** Photo taken; OCR + barcode + model running. */
        data object Working : Phase

        /** Nothing usable came back (denied camera, blurry photo, empty label). */
        data class Empty(val reason: String) : Phase

        /** A draft is ready to confirm — prefilled from the model or, failing that, OCR. */
        data class Confirm(
            val draft: DraftItem,
            /** True when the on-device model drafted this; false = OCR/barcode-only prefill. */
            val fromModel: Boolean,
            val barcode: String?,
        ) : Phase
    }

    private val repo = ServiceLocator.repository(app)

    private val _phase = MutableStateFlow<Phase>(Phase.Capturing)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _groups = MutableStateFlow<List<GroupEntity>>(emptyList())
    val groups: StateFlow<List<GroupEntity>> = _groups.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** Cached capture target so Retake reuses one cache file. */
    private var captureFile: File? = null

    init {
        viewModelScope.launch {
            repo.observeGroups().collect { _groups.value = it }
        }
    }

    /**
     * Build (or reuse) a FileProvider [Uri] in cacheDir for the camera to write into.
     * The authority must match the `<provider>` declared in the manifest (see INTEGRATION.md).
     */
    fun newCaptureUri(): Uri {
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "scans").apply { mkdirs() }
        val file = File(dir, "scan_capture.jpg").also { captureFile = it }
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }

    /** The camera was launched again. */
    fun onRetake() {
        _phase.value = Phase.Capturing
    }

    /** The user dismissed the camera without taking a photo, or denied permission. */
    fun onCaptureCancelled() {
        _phase.value = Phase.Empty("No photo was taken. Try Retake when you're ready.")
    }

    /** A photo was captured at [uri]; run recognition + the agent off the main thread. */
    fun onCaptured(uri: Uri) {
        _phase.value = Phase.Working
        viewModelScope.launch {
            val app = getApplication<Application>()
            val scan = withContext(Dispatchers.IO) { LabelScanner.scan(app, uri) }

            if (scan.isEmpty) {
                _phase.value = Phase.Empty(
                    "Couldn't read the label. Move closer, steady the bottle, and retake.",
                )
                return@launch
            }

            val drafted = runCatching {
                ServiceLocator.agent(app).identifyProduct(scan.combinedText)
            }.getOrNull()

            if (drafted != null) {
                _phase.value = Phase.Confirm(drafted, fromModel = true, barcode = scan.barcode)
            } else {
                // No model (or it declined): still help by prefilling the name from OCR.
                val name = scan.prominentLine
                    ?: scan.lines.firstOrNull()
                    ?: scan.barcode
                    ?: ""
                _phase.value = Phase.Confirm(
                    DraftItem(
                        name = name,
                        type = ItemType.CAPSULE,
                        doseAmount = 1.0,
                        doseUnit = DoseUnit.UNIT,
                    ),
                    fromModel = false,
                    barcode = scan.barcode,
                )
            }
        }
    }

    /** Persist a confirmed item into [groupId]; flips [saved] so the host can dismiss. */
    fun save(item: ItemEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.upsertItem(item) }
            _saved.value = true
        }
    }
}
