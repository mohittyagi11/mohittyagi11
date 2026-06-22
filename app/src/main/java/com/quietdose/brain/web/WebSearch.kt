package com.quietdose.brain.web

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A tiny, key-free web search the on-device app can actually use. It queries the
 * DuckDuckGo HTML endpoint (which returns parseable HTML rather than a JS shell,
 * unlike Amazon/Flipkart which actively block bots) and pulls out result titles,
 * snippets and the real destination URL. The on-device brain then reasons over
 * these *fetched* snippets — grounded in the live web, hedged as unverified.
 *
 * Best-effort by design: time-boxed, never throws, returns empty on any failure.
 */
object WebSearch {

    private const val TAG = "WebSearch"
    private const val ENDPOINT = "https://html.duckduckgo.com/html/"
    private const val MAX_BYTES = 400 * 1024
    private const val TIMEOUT_MS = 6000L

    data class WebResult(val title: String, val snippet: String, val url: String) {
        val domain: String
            get() = runCatching { URL(url).host.removePrefix("www.") }.getOrDefault("")
    }

    /** Run one query; returns up to [max] results, or empty if anything goes wrong. */
    suspend fun search(query: String, max: Int = 5): List<WebResult> =
        withTimeoutOrNull(TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                runCatching { fetchAndParse(query, max) }
                    .onFailure { Log.d(TAG, "search failed: ${it.message}") }
                    .getOrDefault(emptyList())
            }
        } ?: emptyList()

    private fun fetchAndParse(query: String, max: Int): List<WebResult> {
        val q = URLEncoder.encode(query, "UTF-8")
        val conn = (URL("$ENDPOINT?q=$q&kl=us-en").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "GET"
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36",
            )
            setRequestProperty("Accept", "text/html")
        }
        if (conn.responseCode !in 200..299) { conn.disconnect(); return emptyList() }
        val html = conn.inputStream.use { input ->
            val buf = ByteArray(16 * 1024)
            val sb = StringBuilder()
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                sb.append(String(buf, 0, n, Charsets.UTF_8))
                total += n
                if (total >= MAX_BYTES) break
            }
            sb.toString()
        }
        return parse(html, max)
    }

    private val LINK = Regex("class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val SNIPPET = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    private fun parse(html: String, max: Int): List<WebResult> {
        val links = LINK.findAll(html).toList()
        val snippets = SNIPPET.findAll(html).map { strip(it.groupValues[1]) }.toList()
        val out = mutableListOf<WebResult>()
        val seen = mutableSetOf<String>()
        links.forEachIndexed { i, m ->
            val url = realUrl(m.groupValues[1])
            val title = strip(m.groupValues[2])
            if (url.isBlank() || title.isBlank()) return@forEachIndexed
            val r = WebResult(title, snippets.getOrElse(i) { "" }, url)
            if (r.domain.isNotBlank() && seen.add(r.domain + title.take(20))) out += r
            if (out.size >= max) return out
        }
        return out
    }

    /** DDG wraps destinations as //duckduckgo.com/l/?uddg=<encoded>. Unwrap it. */
    private fun realUrl(href: String): String {
        val h = if (href.startsWith("//")) "https:$href" else href
        val uddg = Regex("[?&]uddg=([^&]+)").find(h)?.groupValues?.get(1)
        return if (uddg != null) runCatching { URLDecoder.decode(uddg, "UTF-8") }.getOrDefault(h) else h
    }

    private fun strip(s: String): String = s
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'").replace("&nbsp;", " ")
        .trim()
}
