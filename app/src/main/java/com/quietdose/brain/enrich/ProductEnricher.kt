package com.quietdose.brain.enrich

import android.content.Context
import android.util.Log
import com.quietdose.brain.BrainProvider
import com.quietdose.brain.analysis.ModelCapability
import com.quietdose.brain.analysis.ProductSignals
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
        val signals: ProductSignals = ProductSignals(),
    )

    /**
     * Pull a URL out of arbitrary shared/pasted text and normalize it. Accepts a
     * full http(s) link, or a bare domain like "amazon.in/dp/…" (parsed, then
     * assumed https). Returns null only when there's no plausible link at all.
     */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE).find(text)?.let {
            return it.value.trim().trimEnd('.', ',', ')')
        }
        // No scheme — look for a bare domain/path and assume https.
        val bare = Regex("\\b([a-z0-9-]+\\.)+[a-z]{2,}(/[^\\s\"'<>]*)?", RegexOption.IGNORE_CASE).find(text)
            ?: return null
        return "https://" + bare.value.trim().trimEnd('.', ',', ')')
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
            ?: detectBrand(html)
        val category = jsonLdCategory(jsonld) ?: meta(html, "product:category")
        val (priceText, priceAmount, priceCurrency) = extractPrice(jsonld, html)
        val (ratingValue, ratingCount) = extractRating(jsonld, html)
        val servings = detectServings(title.orEmpty() + " " + html.take(6000))

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
                category = category?.take(40),
                type = type,
                doseAmount = doseAmount,
                doseUnit = doseUnit,
                note = null,
            ),
            url = url,
            sourceTitle = title,
            note = null,
            signals = ProductSignals(
                brand = brand?.take(60),
                priceText = priceText,
                priceAmount = priceAmount,
                priceCurrency = priceCurrency,
                ratingValue = ratingValue,
                ratingCount = ratingCount,
                servings = servings,
                sourceTitle = title,
                url = url,
            ),
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

    private fun jsonLdCategory(product: JSONObject?): String? {
        val c = product?.opt("category") ?: return null
        return when (c) {
            is String -> c.substringAfterLast(">").substringAfterLast("/").trim().ifBlank { null }
            is JSONArray -> c.optString(c.length() - 1).ifBlank { null }
            is JSONObject -> c.optString("name").ifBlank { null }
            else -> null
        }
    }

    /** Price from JSON-LD offers, then OpenGraph/meta, then a currency-symbol scan. */
    private fun extractPrice(product: JSONObject?, html: String): Triple<String?, Double?, String?> {
        // 1) JSON-LD offers.price / priceCurrency
        val offers = product?.opt("offers")
        val offer = when (offers) {
            is JSONObject -> offers
            is JSONArray -> offers.optJSONObject(0)
            else -> null
        }
        val ldPrice = offer?.optString("price")?.ifBlank { null }
            ?: offer?.optString("lowPrice")?.ifBlank { null }
        val ldCurrency = offer?.optString("priceCurrency")?.ifBlank { null }
        if (ldPrice != null) {
            val amt = ldPrice.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            return Triple(formatPrice(amt, ldCurrency) ?: ldPrice, amt, ldCurrency)
        }
        // 2) meta tags
        val metaPrice = meta(html, "product:price:amount") ?: meta(html, "og:price:amount")
        val metaCurrency = meta(html, "product:price:currency") ?: meta(html, "og:price:currency")
        if (metaPrice != null) {
            val amt = metaPrice.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            return Triple(formatPrice(amt, metaCurrency) ?: metaPrice, amt, metaCurrency)
        }
        // 3) a visible price with a currency symbol (₹, $, £, €) — best-effort, hedged.
        Regex("([₹$£€])\\s?([0-9][0-9.,]{1,9})").find(html)?.let {
            val sym = it.groupValues[1]
            val amt = it.groupValues[2].replace(",", "").toDoubleOrNull()
            return Triple("$sym${it.groupValues[2]}", amt, currencyForSymbol(sym))
        }
        return Triple(null, null, null)
    }

    private fun formatPrice(amount: Double?, currency: String?): String? {
        if (amount == null) return null
        val n = if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
        val sym = when (currency?.uppercase()) {
            "INR" -> "₹"; "USD" -> "$"; "GBP" -> "£"; "EUR" -> "€"; else -> null
        }
        return if (sym != null) "$sym$n" else listOfNotNull(currency, n).joinToString(" ")
    }

    private fun currencyForSymbol(sym: String): String? = when (sym) {
        "₹" -> "INR"; "$" -> "USD"; "£" -> "GBP"; "€" -> "EUR"; else -> null
    }

    /** Aggregate rating + review count from JSON-LD, then meta. */
    private fun extractRating(product: JSONObject?, html: String): Pair<Double?, Int?> {
        val agg = product?.optJSONObject("aggregateRating")
        if (agg != null) {
            val value = agg.optString("ratingValue").replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            val count = (agg.optString("reviewCount").ifBlank { agg.optString("ratingCount") })
                .replace(Regex("[^0-9]"), "").toIntOrNull()
            if (value != null) return value to count
        }
        val mv = meta(html, "og:rating")?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()
        return mv to null
    }

    /** Count of capsules/tablets/servings per pack, when stated. */
    private fun detectServings(text: String): Int? {
        val m = Regex("(\\d{2,3})\\s*(capsules|tablets|softgels|gummies|servings|veg(?:etarian)? caps|count|pcs)", RegexOption.IGNORE_CASE)
            .find(text) ?: return null
        return m.groupValues[1].toIntOrNull()?.takeIf { it in 5..1000 }
    }

    /** Last-resort brand extraction from common marketplace markup (e.g. Amazon byline). */
    private fun detectBrand(html: String): String? {
        val patterns = listOf(
            // Amazon: "Visit the Foo Store" / "Brand: Foo" in the byline.
            "Visit the\\s+(.+?)\\s+Store",
            "id=[\"']bylineInfo[\"'][^>]*>\\s*(?:Brand:\\s*)?(.+?)<",
            // Generic "Brand</…>…>Foo<" table rows and "Brand: Foo" labels.
            ">\\s*Brand\\s*</[^>]+>\\s*<[^>]+>\\s*(.+?)<",
            "\\bBrand\\s*[:\\-]\\s*([A-Z][\\w&'.\\- ]{1,40}?)\\s*[<\\n]",
        )
        for (p in patterns) {
            Regex(p, RegexOption.IGNORE_CASE).find(html)?.let {
                val v = decode(it.groupValues[1]).trim()
                if (v.length in 2..40 && !v.contains("<")) return v
            }
        }
        return null
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
