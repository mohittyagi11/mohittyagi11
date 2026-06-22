package com.quietdose.ui.scan

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quietdose.brain.skills.DraftItem
import com.quietdose.brain.vision.LabelFusion
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
        /** Waiting for the next camera result. */
        data object Capturing : Phase

        /** One or more shots taken; review and add more (front + back) or proceed. */
        data class Reviewing(val shots: Int) : Phase

        /** Photos taken; OCR + barcode + model running over all of them. */
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

    /** Up to three captured photos (front, back, extra), in order. */
    private val shots = mutableListOf<Uri>()
    val shotCount: Int get() = shots.size

    companion object { const val MAX_SHOTS = 3 }

    init {
        viewModelScope.launch {
            repo.observeGroups().collect { _groups.value = it }
        }
    }

    /**
     * Build a fresh FileProvider [Uri] for the *next* shot (front/back/extra), so
     * each photo lands in its own cache file. The authority matches the manifest's
     * `<provider>`.
     */
    fun newCaptureUri(): Uri {
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "scans").apply { mkdirs() }
        val index = shots.size.coerceAtMost(MAX_SHOTS - 1)
        val file = File(dir, "scan_$index.jpg")
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }

    /** Start over (the host re-launches the camera for the first shot). */
    fun reset() {
        shots.clear()
        _phase.value = Phase.Capturing
    }

    /** The user dismissed the camera. If we already have shots, keep them; else empty. */
    fun onCaptureCancelled() {
        _phase.value = if (shots.isNotEmpty()) {
            Phase.Reviewing(shots.size)
        } else {
            Phase.Empty("No photo was taken. Try again when you're ready.")
        }
    }

    /** A photo was captured at [uri]; record it and return to review (front + back). */
    fun onCaptured(uri: Uri) {
        if (shots.size < MAX_SHOTS) shots.add(uri)
        _phase.value = Phase.Reviewing(shots.size)
    }

    /** Run OCR + barcode over every shot, fuse them, and draft an item. */
    fun useShots() {
        if (shots.isEmpty()) {
            onCaptureCancelled()
            return
        }
        _phase.value = Phase.Working
        viewModelScope.launch {
            // Belt-and-braces: OCR/model/decoding run off the main thread and are
            // individually guarded, but wrap the whole pipeline too so nothing —
            // not even an OutOfMemoryError — can take the screen down.
            try {
                val app = getApplication<Application>()
                val fused = withContext(Dispatchers.IO) {
                    val results = shots.map { LabelScanner.scan(app, it) }
                    LabelFusion.fuse(results)
                }

                if (fused.isEmpty) {
                    _phase.value = Phase.Empty(
                        "Couldn't read the label. Move closer, steady the bottle, and try again.",
                    )
                    return@launch
                }

                val drafted = runCatching {
                    ServiceLocator.agent(app).identifyProduct(fused.combinedText)
                }.getOrNull()

                if (drafted != null) {
                    _phase.value = Phase.Confirm(drafted, fromModel = true, barcode = fused.barcode)
                } else {
                    val name = fused.prominentLine ?: fused.lines.firstOrNull() ?: fused.barcode ?: ""
                    _phase.value = Phase.Confirm(
                        DraftItem(
                            name = name,
                            type = LabelFusion.guessType(fused),
                            doseAmount = fused.dose?.first ?: 1.0,
                            doseUnit = fused.dose?.second ?: DoseUnit.UNIT,
                        ),
                        fromModel = false,
                        barcode = fused.barcode,
                    )
                }
            } catch (t: Throwable) {
                _phase.value = Phase.Empty("Something went wrong reading the photos. Try again.")
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
