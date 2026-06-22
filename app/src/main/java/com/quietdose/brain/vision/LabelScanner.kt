package com.quietdose.brain.vision

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device label reader. Runs ML Kit Latin text-recognition and barcode scanning
 * over a captured photo, then folds the results into one structured [ScanResult]
 * the agent (and the confirm screen) can use. Everything stays on the device — no
 * network, no cloud — and recognition failures degrade to an empty/partial result
 * rather than throwing.
 *
 * ML Kit returns [Task]s; since the project does not depend on
 * kotlinx-coroutines-play-services we bridge them by hand with
 * [suspendCancellableCoroutine] + success/failure listeners (no extra dependency).
 */
object LabelScanner {

    /**
     * The folded output of a scan.
     *
     * @param lines recognized text lines, roughly ordered top-to-bottom, largest blocks first.
     * @param barcode the first decoded barcode value, if any.
     * @param prominentLine the best guess at the product name line (longest alphabetic line).
     * @param combinedText everything joined into one blob for the on-device model.
     */
    data class ScanResult(
        val lines: List<String>,
        val barcode: String?,
        val prominentLine: String?,
        val combinedText: String,
    ) {
        val isEmpty: Boolean get() = lines.isEmpty() && barcode.isNullOrBlank()
    }

    /** Recognize text + barcode from [uri]. Never throws; returns an empty result on failure. */
    suspend fun scan(context: Context, uri: Uri): ScanResult {
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
            ?: return ScanResult(emptyList(), null, null, "")

        val lines = runCatching { recognizeText(image) }.getOrDefault(emptyList())
        val barcode = runCatching { recognizeBarcode(image) }.getOrNull()

        val prominent = lines
            .filter { it.any(Char::isLetter) }
            .maxByOrNull { it.count(Char::isLetter) }

        val combined = buildString {
            lines.forEach { appendLine(it) }
            if (!barcode.isNullOrBlank()) append("Barcode: ").append(barcode)
        }.trim()

        return ScanResult(
            lines = lines,
            barcode = barcode?.ifBlank { null },
            prominentLine = prominent,
            combinedText = combined,
        )
    }

    private suspend fun recognizeText(image: InputImage): List<String> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = recognizer.process(image).await()
        // Keep block order (top-to-bottom) and split into trimmed, non-blank lines.
        return result.textBlocks
            .flatMap { block -> block.lines.map { it.text.trim() } }
            .filter { it.isNotBlank() }
    }

    private suspend fun recognizeBarcode(image: InputImage): String? {
        val scanner = BarcodeScanning.getClient()
        val barcodes = scanner.process(image).await()
        return barcodes.firstNotNullOfOrNull { it.rawValue?.trim()?.ifBlank { null } }
    }

    /** Minimal Task -> coroutine bridge so we add no new dependency. */
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { value -> if (cont.isActive) cont.resume(value) }
        addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        addOnCanceledListener { if (cont.isActive) cont.cancel() }
    }
}
