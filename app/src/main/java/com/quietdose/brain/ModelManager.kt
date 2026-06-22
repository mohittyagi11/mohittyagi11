package com.quietdose.brain

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Installs / removes the active on-device model file at
 * `filesDir/`[MediaPipeLlmEngine.DEFAULT_MODEL_NAME]. Downloads happen over the
 * network — this fetches a model **file**, it is not a cloud LLM call; all
 * inference still runs on-device via [MediaPipeLlmEngine].
 *
 * Every operation is suspend / off the main thread, cooperatively cancellable,
 * cleans up partial files on failure, and returns a [Result] rather than
 * throwing. After any change to the installed file we call
 * [BrainProvider.reset] so the next `get()` re-detects the model.
 */
object ModelManager {

    private const val TAG = "ModelManager"
    private const val PART_SUFFIX = ".part"
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val BUFFER = 64 * 1024

    /** The active model file, e.g. filesDir/brain.task. */
    private fun modelFile(context: Context): File =
        File(context.applicationContext.filesDir, MediaPipeLlmEngine.DEFAULT_MODEL_NAME)

    private fun partFile(context: Context): File =
        File(context.applicationContext.filesDir, MediaPipeLlmEngine.DEFAULT_MODEL_NAME + PART_SUFFIX)

    /**
     * Open [urlStr] following redirects manually, attaching the Hugging Face
     * bearer [token] ONLY on requests to huggingface.co. HF's `resolve` endpoint
     * 302-redirects to a signed CDN URL that needs no auth (and must not receive
     * the token). Returns the connection at the first non-redirect response.
     */
    private fun openFollowingRedirects(urlStr: String, token: String?): HttpURLConnection {
        var current = urlStr
        var hops = 0
        while (true) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", "application/octet-stream")
                if (!token.isNullOrBlank() && (url.host ?: "").endsWith("huggingface.co")) {
                    setRequestProperty("Authorization", "Bearer $token")
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
     * Download [model] (which must have a non-null [OnDeviceModel.directUrl]) to a
     * temporary `.part` file, then atomically rename it onto the active model
     * path. [onProgress] receives 0f..1f (or -1f when total size is unknown).
     *
     * Streams on [Dispatchers.IO]; cancellable between chunks. On any failure or
     * cancellation the `.part` file is deleted and the previously installed model
     * (if any) is left untouched.
     */
    suspend fun download(
        context: Context,
        model: OnDeviceModel,
        token: String?,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val url = model.directUrl
            ?: return@withContext Result.failure(
                IllegalArgumentException("No direct download for ${model.displayName}; use the source page."),
            )

        val part = partFile(context)
        val target = modelFile(context)
        var connection: HttpURLConnection? = null

        try {
            part.delete()

            // Follow redirects ourselves so the bearer token is sent ONLY to
            // huggingface.co — the redirect target (a signed CDN URL) needs no
            // auth and must not receive the token.
            connection = openFollowingRedirects(url, token)

            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Download failed (HTTP $code). If the model is gated, accept its " +
                            "licence on the page and paste a Hugging Face token, then retry — " +
                            "or import the file.",
                    ),
                )
            }

            val total = connection.contentLengthLong // -1 if unknown
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buf = ByteArray(BUFFER)
                    var read: Int
                    var downloaded = 0L
                    onProgress(if (total > 0) 0f else -1f)
                    while (true) {
                        coroutineContext.ensureActive() // cooperative cancellation
                        read = input.read(buf)
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

            // Atomic-ish install: replace the active model with the completed part.
            target.delete()
            val renamed = part.renameTo(target)
            if (!renamed) {
                // Fallback: copy then delete the part.
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            if (!target.exists() || target.length() <= 0L) {
                target.delete()
                return@withContext Result.failure(IllegalStateException("Couldn't install the model file."))
            }

            onProgress(1f)
            BrainProvider.reset()
            Result.success(Unit)
        } catch (t: Throwable) {
            runCatching { part.delete() }
            Log.w(TAG, "Download failed for ${model.id}", t)
            // Propagate cancellation as a failure with the cause; caller can ignore.
            Result.failure(t)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

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

            target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
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
