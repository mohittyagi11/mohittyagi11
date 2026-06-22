package com.quietdose.ui.add

/**
 * Where an add-draft came from — shown on the unified confirm screen so the user
 * always sees *what was read and from where*, on every channel (typed, scanned,
 * or a link). No black boxes.
 */
sealed interface AddProvenance {
    data object Typed : AddProvenance
    data class Scanned(val photoCount: Int, val ocrText: String) : AddProvenance
    data class Linked(val url: String, val sourceTitle: String?) : AddProvenance
}
