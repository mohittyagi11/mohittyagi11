package com.quietdose.brain.enrich

import android.content.Context
import android.util.Log
import com.quietdose.brain.BrainProvider
import com.quietdose.brain.analysis.ModelCapability
import com.quietdose.brain.skills.DraftItem
import com.quietdose.brain.skills.IdentifyProductSkill
import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.ItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Turns a shared product URL (Amazon/Flipkart/any page) into a [DraftItem],
 * entirely on-device. Fetches the page over the one network call we control,
 * extracts STRONGLY-VALIDATED fields (JSON-LD product data, OpenGraph/title,
 * dose/count regex), and — when a model is loaded — lets it refine the name.
 * Never throws; missing fields are simply left for the user to fill.
 */
object ProductEnricher {

    private const val TAG = "ProductEnricher"
    private const val MAX_BYTES = 512 * 1024
    private const val CONNECT_TIMEOUT = 20_000
    private const val READ_TIMEOUT = 20_000

    data class EnrichResult(
        val draft: DraftItem?,
        val url: String,
        val sourceTitle: String?,
        val note: String?,
    )

    /** Pull the first http(s) URL out of arbitrary shared text. */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val m = Regex("https?://[^\\s\"'<>]+").find(text) ?: return null
        return m.value.trim().trimEnd('.', ',', ')')
    }

    suspend fun enrich(context: Context, url: String): EnrichResult = withContext(Dispatchers.IO) {
        val html = runCatching { fetch(url) }.getOrNull()
            ?: return@withContext EnrichResult(null, url, null, "Couldn't open the link. Fill it in manually.")

        val jsonld = parseJsonLdProduct(html)
        val title = jsonld?.optString("name")?.ifBlank { null }
            ?: meta(html, "og:title")
            ?: titleTag(html)
        val brand = jsonLdBrand(jsonld)
            ?: meta(html, "og:brand")
            ?: meta(html, "product:brand")

        val cleanedTitle = cleanTitle(title)
        val combined = listOfNotNull(cleanedTitle, jsonld?.optString("description")?.take(300)).joinToString(". ")

        // Structured, validated extraction is the spine; the model only refines the name.
        var name = cleanedTitle
        var type = ItemType.CAPSULE
        var doseAmount = 1.0
        var doseUnit = DoseUnit.UNIT

        detectForm(title.orEmpty() + " " + html.take(4000))?.let { type = it }
        detectDose(title.orEmpty())?.let { (amt, unit) -> doseAmount = amt; doseUnit = unit }

        // Optional on-device refinement of the product name (never overrides a good title).
        if (ModelCapability.engineReady(context) && !combined.isNullOrBlank()) {
            runCatching {
                val drafted = IdentifyProductSkill().run(BrainProvider.engine(context), combined)
                if (drafted != null) {
                    if (name.isNullOrBlank()) name = drafted.name
                    if (type == ItemType.CAPSULE) type = drafted.type
                    if (doseUnit == DoseUnit.UNIT && drafted.doseUnit != DoseUnit.UNIT) {
                        doseAmount = drafted.doseAmount; doseUnit = drafted.doseUnit
                    }
                }
            }
        }

        if (name.isNullOrBlank()) {
            return@withContext EnrichResult(null, url, title, "Couldn't read a product name. Fill it in manually.")
        }

        EnrichResult(
            draft = DraftItem(
                name = name!!.take(80),
                brand = brand?.take(60),
                category = null,
                type = type,
                doseAmount = doseAmount,
                doseUnit = doseUnit,
                note = null,
            ),
            url = url,
            sourceTitle = title,
            note = null,
        )
    }

    // --- fetch -------------------------------------------------------------

    private fun fetch(urlStr: String): String? {
        var current = urlStr
        var hops = 0
        while (hops < 5) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36",
                )
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
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
        }
        return null
    }

    // --- extraction --------------------------------------------------------

    private fun parseJsonLdProduct(html: String): JSONObject? {
        val blocks = Regex(
            "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(html)
        for (b in blocks) {
            val raw = b.groupValues[1].trim()
            val product = runCatching { findProduct(raw) }.getOrNull()
            if (product != null) return product
        }
        return null
    }

    private fun findProduct(raw: String): JSONObject? {
        val trimmed = raw.trimStart()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (isProduct(o)) return o
            }
            return null
        }
        val o = JSONObject(raw)
        if (isProduct(o)) return o
        // @graph wrapper
        o.optJSONArray("@graph")?.let { g ->
            for (i in 0 until g.length()) {
                val node = g.optJSONObject(i) ?: continue
                if (isProduct(node)) return node
            }
        }
        return null
    }

    private fun isProduct(o: JSONObject): Boolean {
        val t = o.opt("@type")
        return when (t) {
            is String -> t.equals("Product", ignoreCase = true)
            is JSONArray -> (0 until t.length()).any { t.optString(it).equals("Product", ignoreCase = true) }
            else -> false
        } && o.has("name")
    }

    private fun jsonLdBrand(product: JSONObject?): String? {
        val b = product?.opt("brand") ?: return null
        return when (b) {
            is String -> b.ifBlank { null }
            is JSONObject -> b.optString("name").ifBlank { null }
            else -> null
        }
    }

    private fun meta(html: String, property: String): String? {
        // matches property=... or name=... in either attribute order
        val patterns = listOf(
            "<meta[^>]*(?:property|name)=[\"']$property[\"'][^>]*content=[\"']([^\"']+)[\"']",
            "<meta[^>]*content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"']$property[\"']",
        )
        for (p in patterns) {
            Regex(p, RegexOption.IGNORE_CASE).find(html)?.let { return decode(it.groupValues[1]).ifBlank { null } }
        }
        return null
    }

    private fun titleTag(html: String): String? =
        Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.let { decode(it).trim().ifBlank { null } }

    private fun cleanTitle(title: String?): String? {
        if (title.isNullOrBlank()) return null
        // Drop marketplace noise after a separator, keep the product head.
        var t = title.substringBefore(" | ").substringBefore(" - ").substringBefore(" : ")
        t = t.replace(Regex("\\(.*?\\)"), "").trim()
        return t.take(80).ifBlank { title.take(80) }
    }

    private fun detectForm(text: String): ItemType? {
        val t = text.lowercase()
        return when {
            t.contains("softgel") -> ItemType.SOFTGEL
            t.contains("tablet") -> ItemType.TABLET
            t.contains("gummies") || t.contains("gummy") -> ItemType.GUMMY
            t.contains("capsule") || t.contains("veg caps") || t.contains("caps") -> ItemType.CAPSULE
            t.contains("powder") -> ItemType.POWDER
            t.contains("spray") -> ItemType.SPRAY
            t.contains("liquid") || t.contains("syrup") -> ItemType.LIQUID
            t.contains("drops") -> ItemType.LIQUID
            t.contains("sublingual") -> ItemType.SUBLINGUAL
            else -> null
        }
    }

    private fun detectDose(text: String): Pair<Double, DoseUnit>? {
        val m = Regex("(\\d+(?:\\.\\d+)?)\\s*(mcg|µg|mg|iu|g|ml)", RegexOption.IGNORE_CASE).find(text) ?: return null
        val amt = m.groupValues[1].toDoubleOrNull() ?: return null
        val unit = when (m.groupValues[2].lowercase()) {
            "mg" -> DoseUnit.MG
            "mcg", "µg" -> DoseUnit.MCG
            "g" -> DoseUnit.G
            "iu" -> DoseUnit.IU
            "ml" -> DoseUnit.ML
            else -> return null
        }
        return amt to unit
    }

    private fun decode(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&nbsp;", " ").trim()
}
