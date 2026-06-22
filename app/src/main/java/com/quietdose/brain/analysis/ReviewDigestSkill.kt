package com.quietdose.brain.analysis

import com.quietdose.brain.LlmEngine
import com.quietdose.brain.web.WebSearch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Distils a wall of fetched web snippets into a calm, honest review read — what
 * people actually report, as short keyword phrases, not a list of URLs. The SLM
 * reads the snippets and returns a small JSON object; we parse it tolerantly and
 * never throw. With no model (or no snippets) it degrades to a plain, hedged
 * fallback so the [ReportBlock.Reviews] block always renders something honest.
 *
 * Nothing here is treated as fact: everything is "what people report", the sources
 * are surfaced as bare domains so the reader can weigh them, and the model is
 * forbidden from inventing praise, complaints or numbers.
 */
object ReviewDigestSkill {

    suspend fun digest(
        engine: LlmEngine,
        name: String,
        web: List<WebSearch.WebResult>,
    ): ReportBlock.Reviews {
        val sources = distinctDomains(web)
        if (web.isEmpty() || !engine.isReady()) return fallbackFromSources(sources)

        val raw = runCatching { engine.complete(prompt(name, web)).trim() }.getOrNull()
        val parsed = (if (!raw.isNullOrBlank()) parse(raw) else null)
            ?: return fallbackFromSources(sources)

        val takeaway = parsed.takeaway.takeIf { it.isNotBlank() }
            ?: "People's reports are mixed — weigh the source."
        val loved = parsed.loved
        val watch = parsed.watch
        val state = inferState(loved, watch)
        return ReportBlock.Reviews(
            takeaway = takeaway.take(160),
            loved = loved,
            watch = watch,
            sources = sources,
            state = state,
        )
    }

    /** No-model fallback straight from the web results — distinct domains, no SLM call. */
    fun fallback(web: List<WebSearch.WebResult>): ReportBlock.Reviews = fallbackFromSources(distinctDomains(web))

    /** No-model / empty-web read: honest about the thin evidence, still lists sources. */
    fun fallbackFromSources(sources: List<String>): ReportBlock.Reviews = ReportBlock.Reviews(
        takeaway = "Limited independent reviews found — weigh the source.",
        loved = emptyList(),
        watch = emptyList(),
        sources = sources,
        state = if (sources.isEmpty()) AspectState.NOT_ASSESSED else AspectState.MIXED,
    )

    private data class Digest(val takeaway: String, val loved: List<String>, val watch: List<String>)

    /** Distinct domains in the order they were fetched. */
    private fun distinctDomains(web: List<WebSearch.WebResult>): List<String> {
        val seen = LinkedHashSet<String>()
        web.forEach { it.domain.takeIf { d -> d.isNotBlank() }?.let { d -> seen.add(d) } }
        return seen.toList().take(6)
    }

    /** GOOD if clearly positive, CAUTION if clearly negative, otherwise MIXED. */
    private fun inferState(loved: List<String>, watch: List<String>): AspectState = when {
        loved.isEmpty() && watch.isEmpty() -> AspectState.MIXED
        watch.isEmpty() && loved.isNotEmpty() -> AspectState.GOOD
        loved.isEmpty() && watch.isNotEmpty() -> AspectState.CAUTION
        else -> AspectState.MIXED
    }

    private fun prompt(name: String, web: List<WebSearch.WebResult>): String = buildString {
        appendLine("You read web snippets about a product and report ONLY what people actually say — never your own opinion, never invented details.")
        appendLine("Return ONLY a JSON object, no prose, with these keys:")
        appendLine("  takeaway: ONE short, calm sentence summarising the consensus (max 22 words). Phrase it as what people report.")
        appendLine("  loved: array of SHORT keyword phrases (2-4 words) for recurring praise — e.g. \"absorbs fast\", \"no breakouts\". Empty if none.")
        appendLine("  watch: array of SHORT keyword phrases for recurring complaints / watch-outs — e.g. \"pricey\", \"strong scent\". Empty if none.")
        appendLine("Be honest and specific to the snippets. Do NOT fabricate praise, complaints, ratings or numbers. If the snippets are thin, say so in the takeaway and keep the arrays short or empty.")
        appendLine()
        appendLine("Product: $name")
        appendLine("Snippets:")
        web.take(6).forEach { r -> appendLine("- ${r.domain}: ${(r.snippet.ifBlank { r.title }).take(200)}") }
        appendLine()
        appendLine("Reply with only the JSON object.")
    }

    /** Dig the JSON object out of whatever the model returned; never throw. */
    private fun parse(raw: String): Digest? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            val o = JSONObject(raw.substring(start, end + 1))
            Digest(
                takeaway = o.optString("takeaway").trim(),
                loved = o.optJSONArray("loved").toPhrases(),
                watch = o.optJSONArray("watch").toPhrases(),
            )
        }.getOrNull()
    }

    private fun JSONArray?.toPhrases(): List<String> {
        if (this == null) return emptyList()
        return (0 until length())
            .mapNotNull { optString(it).trim().ifBlank { null } }
            .map { it.take(40) }
            .distinct()
            .take(6)
    }
}
