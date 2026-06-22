package com.quietdose.brain.enrich

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import com.quietdose.brain.web.NaturalBrowsing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * A tiny, on-device colour sampler for the product's packaging photo. The LLM is
 * text-only and can't generate images, so the [ProductGlyph][com.quietdose.ui.analysis.ProductGlyph]
 * is drawn on-device — but a bland category tint reads as an abstract blob. This
 * util pulls the *real* packaging colours from the og:image so the glyph's body
 * and cap echo the actual product.
 *
 * It mirrors the app's existing single network call (same browser headers /
 * timeouts as [ProductEnricher]/[NaturalBrowsing]), downscales hard with
 * [BitmapFactory]'s `inSampleSize` so we never hold a big bitmap, time-boxes the
 * whole thing, and — crucially — NEVER throws: every failure path returns null so
 * the caller silently falls back to the category palette.
 */
object ImagePalette {

    private const val CONNECT_TIMEOUT = 4_000
    private const val READ_TIMEOUT = 4_000
    private const val MAX_BYTES = 2 * 1024 * 1024 // a product photo, not a poster
    private const val TARGET_PX = 64              // decode down to ~<=64px on the long edge
    private const val OVERALL_BUDGET_MS = 4_000L

    /** Two representative colours from the packaging. */
    data class PaletteResult(
        /** The dominant/average colour — the glyph body tint. */
        val primary: Color,
        /** A more saturated accent (cap / orbit hint), or [primary] if the image is flat. */
        val accent: Color,
    )

    /**
     * Fetch + decode + sample the dominant colours of [url]. Off the main thread,
     * time-boxed, OOM-guarded. Returns null on any failure (no URL, network error,
     * decode failure, blank/transparent image) — never throws.
     */
    suspend fun dominant(url: String?): PaletteResult? {
        if (url.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(OVERALL_BUDGET_MS) {
                runCatching { sample(url) }.getOrNull()
            }
        }
    }

    private fun sample(url: String): PaletteResult? {
        val bytes = fetchBytes(url) ?: return null
        val bmp = decodeDownscaled(bytes) ?: return null
        try {
            return analyse(bmp)
        } finally {
            bmp.recycle()
        }
    }

    // --- fetch (same fingerprint as the rest of the app) ---------------------

    private fun fetchBytes(urlStr: String): ByteArray? {
        var current = urlStr
        var hops = 0
        while (hops < 4) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                instanceFollowRedirects = false
                requestMethod = "GET"
                NaturalBrowsing.applyHeaders(this, referer = "https://www.google.com/")
            }
            c.connect()
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc.isNullOrBlank()) return null
                current = URL(URL(current), loc).toString()
                hops++
                continue
            }
            if (code !in 200..299) { c.disconnect(); return null }
            return c.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    if (total >= MAX_BYTES) break
                }
                out.toByteArray()
            }
        }
        return null
    }

    // --- decode (downscaled, OOM-guarded) ------------------------------------

    private fun decodeDownscaled(bytes: ByteArray): Bitmap? {
        return try {
            // First pass: bounds only, so we can pick a power-of-two inSampleSize.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return null
            var sample = 1
            while (longest / (sample * 2) >= TARGET_PX) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    // --- sampling: average + a saturated accent via simple bucketing ---------

    private fun analyse(bmp: Bitmap): PaletteResult? {
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return null

        // Average (weighted) for the body, plus coarse hue/value bucketing to find
        // a representative saturated accent. We skip near-transparent and the most
        // washed-out near-white/near-black pixels for the accent so it actually
        // references the packaging colour, not the studio background.
        var rSum = 0.0; var gSum = 0.0; var bSum = 0.0; var n = 0

        // 12 hue buckets, accumulating saturation-weighted colour mass.
        val bucketR = DoubleArray(12)
        val bucketG = DoubleArray(12)
        val bucketB = DoubleArray(12)
        val bucketW = DoubleArray(12)

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val p = bmp.getPixel(x, y)
                val a = (p ushr 24) and 0xFF
                if (a >= 16) {
                    val r = (p ushr 16) and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = p and 0xFF
                    rSum += r; gSum += g; bSum += b; n++

                    val (hue, sat, value) = rgbToHsv(r, g, b)
                    // Reject background-ish pixels for accent selection.
                    if (sat > 0.18f && value in 0.12f..0.96f) {
                        val bi = ((hue / 30f).toInt()).coerceIn(0, 11)
                        // Weight by saturation*value so vivid mid pixels dominate.
                        val wgt = (sat * value).toDouble()
                        bucketR[bi] += r * wgt
                        bucketG[bi] += g * wgt
                        bucketB[bi] += b * wgt
                        bucketW[bi] += wgt
                    }
                }
                x++
            }
            y++
        }
        if (n == 0) return null

        val primary = Color(
            red = (rSum / n / 255.0).toFloat().coerceIn(0f, 1f),
            green = (gSum / n / 255.0).toFloat().coerceIn(0f, 1f),
            blue = (bSum / n / 255.0).toFloat().coerceIn(0f, 1f),
        )

        // The heaviest saturated bucket is the packaging's signature colour.
        var best = -1
        var bestW = 0.0
        for (i in 0 until 12) {
            if (bucketW[i] > bestW) { bestW = bucketW[i]; best = i }
        }
        val accent = if (best >= 0 && bestW > 0.0) {
            Color(
                red = (bucketR[best] / bestW / 255.0).toFloat().coerceIn(0f, 1f),
                green = (bucketG[best] / bestW / 255.0).toFloat().coerceIn(0f, 1f),
                blue = (bucketB[best] / bestW / 255.0).toFloat().coerceIn(0f, 1f),
            )
        } else {
            primary
        }
        return PaletteResult(primary = primary, accent = accent)
    }

    /** Minimal RGB(0..255) → HSV. Hue 0..360, sat/value 0..1. */
    private fun rgbToHsv(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
        val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min
        val hue = when {
            delta < 1e-4f -> 0f
            max == rf -> 60f * (((gf - bf) / delta) % 6f)
            max == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val sat = if (max <= 0f) 0f else delta / max
        return Triple(hue, sat, max)
    }
}
