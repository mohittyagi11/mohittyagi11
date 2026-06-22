package com.quietdose.brain

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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

    /**
     * A tar entry must be at least this big to be considered the model — any real
     * on-device LLM `.task` is hundreds of MB, while tokenizer/metadata entries
     * are tiny, so this cleanly skips them in a single forward pass.
     */
    private const val MIN_MODEL_BYTES = 8L * 1024 * 1024

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
     * Whether the installed model looks like a MediaPipe `.task` bundle — which is
     * a ZIP, so it starts with the "PK" signature. A raw `.tflite` flatbuffer does
     * not, and the LLM runtime rejects it with "Unable to open zip archive". Lets
     * the UI warn at install time instead of after a failed test. Null if absent.
     */
    fun installedLooksLoadable(context: Context): Boolean? {
        val f = modelFile(context)
        if (!f.exists() || f.length() <= 0L) return null
        val head = ByteArray(2)
        val n = runCatching { f.inputStream().use { it.read(head) } }.getOrDefault(-1)
        return n == 2 && head[0].toInt() == 'P'.code && head[1].toInt() == 'K'.code
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
            // Raw .task downloads pass straight through. Reuse the bar for the
            // (single-pass) extraction phase.
            target.delete()
            val produced = materialize(part, target) { p -> onProgress(p) }
            part.delete()

            if (!produced || !target.exists() || target.length() <= 0L) {
                target.delete()
                return@withContext Result.failure(
                    IllegalStateException("Downloaded an archive but found no model file inside."),
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
     * Download a model from a URL captured by the in-app browser — the user has
     * already signed in / accepted the licence there, so we reuse the WebView's
     * session by forwarding its [cookie] and [userAgent]. We control the fetch
     * (progress, archive extraction, format verification) instead of handing off
     * to the system download manager. The credential is sent only to the original
     * host; redirects to a signed CDN drop it.
     */
    suspend fun downloadFromBrowser(
        context: Context,
        url: String,
        cookie: String?,
        userAgent: String?,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val part = partFile(context)
        val target = modelFile(context)
        var connection: HttpURLConnection? = null
        try {
            part.delete()
            connection = openBrowserDownload(url, cookie, userAgent)
            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Download failed (HTTP $code). In the browser, sign in and accept the model's " +
                            "licence first, then tap the .task download again.",
                    ),
                )
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                part.outputStream().use { output -> streamTo(input, output, total, onProgress) }
            }
            if (part.length() <= 0L) {
                part.delete()
                return@withContext Result.failure(IllegalStateException("Downloaded an empty file."))
            }
            target.delete()
            val produced = materialize(part, target) { p -> onProgress(p) }
            part.delete()
            if (!produced || !target.exists() || target.length() <= 0L) {
                target.delete()
                return@withContext Result.failure(
                    IllegalStateException("Downloaded, but found no model file inside."),
                )
            }
            onProgress(1f)
            BrainProvider.reset()
            Result.success(Unit)
        } catch (t: Throwable) {
            runCatching { part.delete() }
            Log.w(TAG, "Browser download failed", t)
            Result.failure(t)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    /** Like [openFollowingRedirects], but forwards a browser session cookie + UA. */
    private fun openBrowserDownload(urlStr: String, cookie: String?, userAgent: String?): HttpURLConnection {
        var current = urlStr
        var hops = 0
        val firstHost = URL(urlStr).host ?: ""
        while (true) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", "application/octet-stream")
                if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
                // The cookie authorises only the gated host; the CDN it redirects
                // to uses a signed URL and must not receive it.
                if (!cookie.isNullOrBlank() && (url.host ?: "") == firstHost) {
                    setRequestProperty("Cookie", cookie)
                }
            }
            c.connect()
            val code = c.responseCode
            if (code in 300..399 && hops < 5) {
                val location = c.getHeaderField("Location")
                c.disconnect()
                if (location.isNullOrBlank()) return c
                current = URL(URL(current), location).toString()
                hops++
                continue
            }
            return c
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
    private suspend fun materialize(part: File, target: File, onProgress: (Float) -> Unit): Boolean =
        when (archiveKind(part)) {
            ArchiveKind.GZIP_TAR ->
                GZIPInputStream(part.inputStream().buffered()).use { gz -> extractTarTo(gz, target, onProgress) }
            // A .task bundle is a zip — install it as-is, do not unpack it.
            ArchiveKind.ZIP, ArchiveKind.RAW -> {
                installAsIs(part, target)
                onProgress(1f)
                true
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

    /**
     * Extract the model out of an already-gunzipped tar [tar] into [target] in a
     * **single pass**: walk the 512-byte headers and write the first regular file
     * that is large enough to be a model ([MIN_MODEL_BYTES]) — tiny metadata /
     * tokenizer-config entries are skipped. We stop as soon as it's written, so
     * we never decompress past the model. Returns true if a model was extracted.
     *
     * [onProgress] reports 0f..1f across the model entry once its size is known
     * (the dominant cost), and -1f while still scanning small leading entries.
     */
    private suspend fun extractTarTo(tar: InputStream, target: File, onProgress: (Float) -> Unit): Boolean {
        val stream = BufferedInputStream(tar)
        val header = ByteArray(512)
        onProgress(-1f)
        while (true) {
            coroutineContext.ensureActive()
            if (!readFully(stream, header)) return false
            if (header.all { it.toInt() == 0 }) return false // end-of-archive padding
            val size = parseOctal(header, 124, 12)
            if (isRegularFile(header) && size >= MIN_MODEL_BYTES) {
                target.outputStream().use { out -> copyExact(stream, out, size, onProgress) }
                onProgress(1f)
                return true
            }
            skipExact(stream, roundUp512(size)) // skip data to next 512-block
        }
    }

    /** USTAR typeflag at offset 156: '0' or NUL is a regular file. */
    private fun isRegularFile(header: ByteArray): Boolean {
        val t = header[156].toInt()
        return t == '0'.code || t == 0
    }

    // ---- small stream helpers -------------------------------------------

    /** Copy exactly [size] bytes, reporting 0f..1f across them. */
    private suspend fun copyExact(
        input: InputStream,
        output: java.io.OutputStream,
        size: Long,
        onProgress: (Float) -> Unit = {},
    ) {
        val buf = ByteArray(BUFFER)
        var remaining = size
        var written = 0L
        while (remaining > 0) {
            coroutineContext.ensureActive()
            val want = minOf(remaining, buf.size.toLong()).toInt()
            val read = input.read(buf, 0, want)
            if (read < 0) break
            output.write(buf, 0, read)
            remaining -= read
            written += read
            if (size > 0) onProgress((written.toFloat() / size).coerceIn(0f, 1f))
        }
        output.flush()
    }

    /** Copy until EOF, reporting progress against [total] (or -1f if unknown). */
    private suspend fun streamTo(
        input: InputStream,
        output: java.io.OutputStream,
        total: Long,
        onProgress: (Float) -> Unit,
    ) {
        val buf = ByteArray(BUFFER)
        var written = 0L
        onProgress(if (total > 0) 0f else -1f)
        while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buf)
            if (read < 0) break
            output.write(buf, 0, read)
            written += read
            if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
        }
        output.flush()
    }

    /** Best-effort size of a content [uri] (for import progress); -1 if unknown. */
    private fun querySize(context: Context, uri: Uri): Long =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !c.isNull(idx)) c.getLong(idx) else -1L
                } else -1L
            } ?: -1L
        }.getOrDefault(-1L)

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
     * Import a model the user picked via the document picker, with progress.
     *
     * Streams in a **single pass** straight from the content provider into the
     * install file — no intermediate compressed copy:
     *  - a `.tar.gz` is gunzipped + untarred on the fly (the model is extracted
     *    as it streams), and
     *  - a raw `.task` (which sniffs as a zip) or any other file is copied whole.
     *
     * [onProgress] reports 0f..1f (or -1f when the size is unknown). Never throws.
     */
    suspend fun importFrom(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val tmp = partFile(appContext)
        val target = modelFile(appContext)
        try {
            tmp.delete()
            val raw = appContext.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Couldn't open the selected file."))

            var wasArchive = false
            val produced = raw.buffered().use { buffered ->
                // Peek the magic without consuming it (BufferedInputStream supports reset).
                buffered.mark(8)
                val head = ByteArray(4)
                val n = buffered.read(head)
                buffered.reset()
                val isGzip = n >= 2 &&
                    (head[0].toInt() and 0xFF) == 0x1F && (head[1].toInt() and 0xFF) == 0x8B
                if (isGzip) {
                    wasArchive = true
                    GZIPInputStream(buffered).use { gz -> extractTarTo(gz, tmp, onProgress) }
                } else {
                    val total = querySize(appContext, uri)
                    tmp.outputStream().use { out -> streamTo(buffered, out, total, onProgress) }
                    true
                }
            }

            if (!produced || tmp.length() <= 0L) {
                tmp.delete()
                return@withContext Result.failure(
                    IllegalStateException(
                        if (wasArchive) {
                            "That .tar.gz didn't contain a model file. Open the archive and import the .task directly."
                        } else {
                            "The selected file was empty."
                        },
                    ),
                )
            }

            target.delete()
            installAsIs(tmp, target)
            tmp.delete()
            if (!target.exists() || target.length() <= 0L) {
                target.delete()
                return@withContext Result.failure(IllegalStateException("Couldn't install the model file."))
            }

            onProgress(1f)
            BrainProvider.reset()
            Result.success(Unit)
        } catch (t: Throwable) {
            runCatching { tmp.delete() }
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
