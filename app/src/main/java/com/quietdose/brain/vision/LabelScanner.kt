package com.quietdose.brain.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
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

    /** Max edge we feed ML Kit. Camera shots are 12MP+ (~48 MB decoded); a label
     *  reads fine at ~1600px and this keeps three photos well clear of OOM. */
    private const val MAX_EDGE = 1600

    /** Recognize text + barcode from [uri]. Never throws; returns an empty result on failure. */
    suspend fun scan(context: Context, uri: Uri): ScanResult {
        val empty = ScanResult(emptyList(), null, null, "")
        // Decode downsampled (not full-resolution) so big camera photos don't OOM.
        val decoded = runCatching { decodeDownsampled(context, uri, MAX_EDGE) }.getOrNull() ?: return empty
        val (bitmap, rotation) = decoded
        val image = runCatching { InputImage.fromBitmap(bitmap, rotation) }.getOrNull()
            ?: run { runCatching { bitmap.recycle() }; return empty }

        val lines = runCatching { recognizeText(image) }.getOrDefault(emptyList())
        val barcode = runCatching { recognizeBarcode(image) }.getOrNull()
        runCatching { bitmap.recycle() }

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

    /**
     * Decode [uri] downsampled so its longest edge is ~[maxEdge] px, and read the
     * EXIF orientation so ML Kit gets the right rotation. Returns the bitmap and
     * its rotation in degrees, or null if the image can't be read.
     */
    private fun decodeDownsampled(context: Context, uri: Uri, maxEdge: Int): Pair<Bitmap, Int>? {
        val resolver = context.contentResolver
        // 1) bounds only — cheap, no full decode.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val (w, h) = bounds.outWidth to bounds.outHeight
        if (w <= 0 || h <= 0) return null

        // 2) pick a power-of-two sample size that brings the long edge under maxEdge.
        var sample = 1
        while (maxOf(w, h) / sample > maxEdge) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        // 3) EXIF rotation so text isn't sideways.
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        return bitmap to rotation
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
