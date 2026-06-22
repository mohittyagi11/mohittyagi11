package com.quietdose.brain.web

import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import kotlin.random.Random

/**
 * Makes the app's web requests read like a person quietly reviewing a product,
 * not a scraper hammering a site: a real, rotating desktop browser fingerprint,
 * the usual navigation headers, a plausible referer, and small human pauses
 * between page loads. Scope stays deliberately small — a couple of pages per
 * analysis — so it behaves like one curious reader, which is exactly what it is.
 */
object NaturalBrowsing {

    private val USER_AGENTS = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64; rv:125.0) Gecko/20100101 Firefox/125.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
    )

    fun userAgent(): String = USER_AGENTS.random()

    /** Apply a normal browser's navigation headers. Leave gzip to the platform. */
    fun applyHeaders(c: HttpURLConnection, referer: String? = null) {
        c.setRequestProperty("User-Agent", userAgent())
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        c.setRequestProperty("Upgrade-Insecure-Requests", "1")
        c.setRequestProperty("Sec-Fetch-Dest", "document")
        c.setRequestProperty("Sec-Fetch-Mode", "navigate")
        c.setRequestProperty("Sec-Fetch-Site", if (referer == null) "none" else "cross-site")
        c.setRequestProperty("Sec-Fetch-User", "?1")
        referer?.let { c.setRequestProperty("Referer", it) }
    }

    /** A short, irregular pause — the beat between a human clicking through. */
    suspend fun humanPause(minMs: Long = 250, maxMs: Long = 850) {
        delay(Random.nextLong(minMs, maxMs))
    }
}
