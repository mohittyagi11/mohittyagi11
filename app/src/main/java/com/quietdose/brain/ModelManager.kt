package com.quietdose.brain

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.zip.GZIPInputStream
import kotlin.coroutines.coroutineContext

/**
 * Installs / removes the active on-device model file at
 * `filesDir/`[MediaPipeLlmEngine.DEFAULT_MODEL_NAME]. Downloads happen over the
 * network — this fetches a model **file**, it is not a cloud LLM call; all
 * inference still runs on-device via [MediaPipeLlmEngine].
 *
 * Two gated sources are supported, chosen per download:
 *  - **Hugging Face** — bearer token sent only to huggingface.co.
 *  - **Kaggle** — HTTP Basic (username + key) sent only to kaggle.com; the
 *    response is a `.tar.gz` (or `.zip`) archive, so we extract the `.task`
 *    inside automatically.
 *
 * Every operation is suspend / off the main thread, cooperatively cancellable,
 * cleans up partial files on failure, and returns a [Result] rather than
 * throwing. After any change to the installed file we call [BrainProvider.reset]
 * so the next `get()` re-detects the model.
 */
object ModelManager {

    private const val TAG = "ModelManager"
    private const val PART_SUFFIX = ".part"
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val BUFFER = 64 * 1024

    /** Which gated host a download targets. */
    enum class Source { HUGGING_FACE, KAGGLE }

    /** The active model file, e.g. filesDir/brain.task. */
    private fun modelFile(context: Context): File =
        File(context.applicationContext.filesDir, MediaPipeLlmEngine.DEFAULT_MODEL_NAME)

    private fun partFile(context: Context): File =
        File(context.applicationContext.filesDir, MediaPipeLlmEngine.DEFAULT_MODEL_NAME + PART_SUFFIX)

    /**
     * Open [urlStr] following redirects manually, attaching the right credential
     * **only on the gated host**: a Hugging Face bearer token on huggingface.co,
     * or Kaggle HTTP Basic on kaggle.com. Both hosts 302-redirect to a signed CDN
     * URL that needs no auth (and must not receive the credential). Returns the
     * connection at the first non-redirect response.
     */
    private fun openFollowingRedirects(
        urlStr: String,
        hfToken: String?,
        kaggle: ModelAuth.KaggleCreds?,
    ): HttpURLConnection {
        var current = urlStr
        var hops = 0
        while (true) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", "application/octet-stream")
                val host = url.host ?: ""
                if (!hfToken.isNullOrBlank() && host.endsWith("huggingface.co")) {
                    setRequestProperty("Authorization", "Bearer $hfToken")
                }
                if (kaggle != null && kaggle.isComplete && host.endsWith("kaggle.com")) {
                    val raw = "${kaggle.username}:${kaggle.key}"
                    val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
                    setRequestProperty("Authorization", "Basic $encoded")
                }
            }
            c.connect()
            val code = c.responseCode
            if (code in 300..399 && hops < 5) {
                val location = c.getHeaderField("Location")
                c.disconnect()
                if (location.isNullOrBlank()) return c
                current = URL(URL(current), location).toString() // resolve relative
                hops++
                continue
            }
            return c
        }
    }

    /** Info about whatever model file is currently installed, or null if none. */
    data class InstalledModel(val fileName: String, val sizeBytes: Long)

    /** Returns details of the installed model file, or null when none is present. */
    fun installedModelInfo(context: Context): InstalledModel? {
        val f = modelFile(context)
        return if (f.exists() && f.length() > 0L) {
            InstalledModel(fileName = f.name, sizeBytes = f.length())
        } else {
            null
        }
    }

    /**
     * Download [model] from [source] to a temporary `.part` file, then install it
     * onto the active model path (extracting the `.task` first if the download is
     * an archive). [onProgress] receives 0f..1f (or -1f when size is unknown).
     *
     * Streams on [Dispatchers.IO]; cancellable between chunks. On any failure or
     * cancellation the partial files are deleted and the previously installed
     * model (if any) is left untouched.
     */
    suspend fun download(
        context: Context,
        model: OnDeviceModel,
        source: Source,
        hfToken: String?,
        kaggle: ModelAuth.KaggleCreds?,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val url = when (source) {
            Source.HUGGING_FACE -> model.directUrl
            Source.KAGGLE -> model.kaggleUrl
        } ?: return@withContext Result.failure(
            IllegalArgumentException("No ${source.label} download for ${model.displayName}; use its page."),
        )

        if (source == Source.KAGGLE && (kaggle == null || !kaggle.isComplete)) {
            return@withContext Result.failure(
                IllegalStateException("Add your Kaggle username and API key first, then Download."),
            )
        }

        val part = partFile(context)
        val target = modelFile(context)
        var connection: HttpURLConnection? = null

        try {
            part.delete()
            connection = openFollowingRedirects(url, hfToken, kaggle)

            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException(httpHint(code, source)))
            }

            val total = connection.contentLengthLong // -1 if unknown
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buf = ByteArray(BUFFER)
                    var downloaded = 0L
                    onProgress(if (total > 0) 0f else -1f)
                    while (true) {
                        coroutineContext.ensureActive() // cooperative cancellation
                        val read = input.read(buf)
                        if (read < 0) break
                        output.write(buf, 0, read)
                        downloaded += read
                        if (total > 0) {
                            onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }

            if (part.length() <= 0L) {
                part.delete()
                return@withContext Result.failure(IllegalStateException("Downloaded an empty file."))
            }

            // Kaggle (and some HF) downloads are archives — pull the .task out.
            // Raw .task downloads pass straight through.
            target.delete()
            materialize(part, target)
            part.delete()

            if (!target.exists() || target.length() <= 0L) {
                target.delete()
                return@withContext Result.failure(
                    IllegalStateException("Downloaded an archive but found no .task model inside."),
                )
            }

            onProgress(1f)
            BrainProvider.reset()
            Result.success(Unit)
        } catch (t: Throwable) {
            runCatching { part.delete() }
            Log.w(TAG, "Download failed for ${model.id}", t)
            Result.failure(t)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    /**
     * Turn a downloaded/imported [part] into the installed model at [target].
     *
     * Important: a MediaPipe `.task` model is **itself a zip bundle**, so we must
     * never unpack a zip — that would corrupt the model (or, scanning a multi-GB
     * zip for a non-existent inner file, appear to hang). We only unpack the
     * **gzip-tar** wrapper that Kaggle serves; everything else (a raw `.task`,
     * which sniffs as a zip, or any other file) is installed whole.
     */
    private suspend fun materialize(part: File, target: File) {
        when (archiveKind(part)) {
            ArchiveKind.GZIP_TAR ->
                GZIPInputStream(part.inputStream().buffered()).use { gz -> extractTar(gz, target) }
            // A .task bundle is a zip — install it as-is, do not unpack it.
            ArchiveKind.ZIP, ArchiveKind.RAW -> installAsIs(part, target)
        }
    }

    /** Move [part] onto [target] without touching its contents. */
    private fun installAsIs(part: File, target: File) {
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
        }
    }

    private enum class ArchiveKind { RAW, GZIP_TAR, ZIP }

    /** Sniff the first bytes to tell a raw model from a gzip/zip archive. */
    private fun archiveKind(file: File): ArchiveKind {
        val head = ByteArray(4)
        val n = file.inputStream().use { it.read(head) }
        if (n < 2) return ArchiveKind.RAW
        val b0 = head[0].toInt() and 0xFF
        val b1 = head[1].toInt() and 0xFF
        return when {
            b0 == 0x1F && b1 == 0x8B -> ArchiveKind.GZIP_TAR        // gzip magic
            b0 == 0x50 && b1 == 0x4B -> ArchiveKind.ZIP             // "PK"
            else -> ArchiveKind.RAW
        }
    }

    /** True for a tar entry that is a loadable model bundle. */
    private fun isModelEntry(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".task") || lower.endsWith(".litertlm")
    }

    /**
     * Minimal USTAR reader: walk 512-byte headers, and when we reach a model
     * entry, stream exactly its declared size into [target]. Enough for the
     * single-file model archives Kaggle serves; not a general-purpose tar tool.
     */
    private suspend fun extractTar(input: InputStream, target: File) {
        val header = ByteArray(512)
        val stream = BufferedInputStream(input)
        while (true) {
            coroutineContext.ensureActive()
            if (!readFully(stream, header)) return
            if (header.all { it.toInt() == 0 }) return // end-of-archive padding
            val name = String(header, 0, 100, Charsets.UTF_8).trimEnd('\u0000', ' ')
            val size = parseOctal(header, 124, 12)
            if (name.isNotEmpty() && isModelEntry(name)) {
                target.outputStream().use { out -> copyExact(stream, out, size) }
                return
            }
            // Skip this entry's data, rounded up to the 512-byte block boundary.
            skipExact(stream, roundUp512(size))
        }
    }

    // ---- small stream helpers -------------------------------------------

    private suspend fun copyExact(input: InputStream, output: java.io.OutputStream, size: Long) {
        val buf = ByteArray(BUFFER)
        var remaining = size
        while (remaining > 0) {
            coroutineContext.ensureActive()
            val want = minOf(remaining, buf.size.toLong()).toInt()
            val read = input.read(buf, 0, want)
            if (read < 0) break
            output.write(buf, 0, read)
            remaining -= read
        }
        output.flush()
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val read = input.read(buf, off, buf.size - off)
            if (read < 0) return off == buf.size
            off += read
        }
        return true
    }

    private fun skipExact(input: InputStream, count: Long) {
        var remaining = count
        val scratch = ByteArray(BUFFER)
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val read = input.read(scratch, 0, minOf(remaining, scratch.size.toLong()).toInt())
                if (read < 0) return
                remaining -= read
            }
        }
    }

    private fun parseOctal(buf: ByteArray, offset: Int, len: Int): Long {
        val s = String(buf, offset, len, Charsets.US_ASCII).trim('\u0000', ' ')
        return s.toLongOrNull(8) ?: 0L
    }

    private fun roundUp512(size: Long): Long = ((size + 511) / 512) * 512

    private fun httpHint(code: Int, source: Source): String = when (code) {
        401, 403 -> "Access denied (HTTP $code). Open the model's ${source.label} page, accept " +
            "the licence once, and check your ${source.credentialName} — then retry, or import the file."
        404 -> "Not found (HTTP $code). The ${source.label} file may have been renamed or revisioned — " +
            "open the page and import the .task instead."
        else -> "Download failed (HTTP $code). Try the other source, or import the file."
    }

    private val Source.label: String
        get() = if (this == Source.HUGGING_FACE) "Hugging Face" else "Kaggle"

    private val Source.credentialName: String
        get() = if (this == Source.HUGGING_FACE) "token" else "username + key"

    /**
     * Import a `.task` model the user picked via the document picker. Streams the
     * content from [uri] to a `.part` file, then atomically installs it. Returns
     * a failure (never throws) if the stream can't be opened or is empty.
     */
    suspend fun importFrom(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val part = partFile(appContext)
        val target = modelFile(appContext)
        try {
            part.delete()
            val input: InputStream = appContext.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Couldn't open the selected file."))
            input.use { stream ->
                part.outputStream().use { output ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = stream.read(buf)
                        if (read < 0) break
                        output.write(buf, 0, read)
                    }
                    output.flush()
                }
            }

            if (part.length() <= 0L) {
                part.delete()
                return@withContext Result.failure(IllegalStateException("The selected file was empty."))
            }

            // Allow importing an archive too (extract the .task), not just a raw file.
            target.delete()
            materialize(part, target)
            part.delete()
            if (!target.exists() || target.length() <= 0L) {
                target.delete()
                return@withContext Result.failure(IllegalStateException("Couldn't install the imported file."))
            }

            BrainProvider.reset()
            Result.success(Unit)
        } catch (t: Throwable) {
            runCatching { part.delete() }
            Log.w(TAG, "Import failed", t)
            Result.failure(t)
        }
    }

    /**
     * Remove the installed model (and any leftover `.part`). Resets the brain so
     * it falls back to the heuristic. Safe to call when nothing is installed.
     */
    fun remove(context: Context) {
        runCatching {
            modelFile(context).delete()
            partFile(context).delete()
        }
        BrainProvider.reset()
    }
}
