package com.quietdose.brain.analysis

import android.content.Context
import com.quietdose.brain.LlmEngine
import com.quietdose.brain.web.WebSearch
import org.json.JSONArray

/**
 * Holds a product's marketing CLAIMS up to honest scrutiny. In ONE bounded SLM
 * pass it extracts the top few claims the material asserts ("real results",
 * "firms and plumps") and weighs each across three angles at once — mechanism
 * plausibility, the general evidence/consensus, and what the web/reviews actually
 * say — returning a [ClaimStatus] (SUPPORTED / PLAUSIBLE / UNVERIFIED / OVERREACH)
 * plus a one-line basis.
 *
 * Conclusions persist across sessions: priors are loaded from [ContextStore]
 * (source "claim-verifier") and any already-concluded claim is reused instead of
 * being re-verified, so the intelligence accumulates and the budget shrinks over
 * time. With no model it degrades to listing the extracted claims as UNVERIFIED.
 * Never throws.
 */
object ClaimVerifier {

    private const val SOURCE = "claim-verifier"
    private const val MAX_CLAIMS = 4

    suspend fun verify(
        context: Context,
        engine: LlmEngine,
        name: String,
        sourceMaterial: String,
        web: List<WebSearch.WebResult>,
    ): List<ClaimLine> {
        // 1) Cross-session priors: claims we've already concluded for this item.
        val priors = runCatching { loadPriors(context, name) }.getOrDefault(emptyList())
        val priorByClaim = priors.associateBy { it.claim.lowercase() }

        // 2) No model → degrade: just surface what we can, unverified.
        if (!engine.isReady()) {
            if (priors.isNotEmpty()) return priors.take(MAX_CLAIMS)
            return extractFallback(sourceMaterial).map {
                ClaimLine(it, ClaimStatus.UNVERIFIED, "Not independently checked yet.")
            }.take(MAX_CLAIMS)
        }

        // 3) Single bounded pass: extract + verify the claims we don't already know.
        val fresh = runCatching {
            engine.complete(prompt(name, sourceMaterial, web)).trim()
        }.getOrNull()?.let { parse(it) }.orEmpty()

        // 4) Merge: reuse a prior conclusion when its claim re-appears; keep new ones.
        val out = LinkedHashMap<String, ClaimLine>()
        priors.forEach { out[it.claim.lowercase()] = it }
        fresh.forEach { line ->
            val k = line.claim.lowercase()
            // A prior conclusion wins (already verified); otherwise take the fresh one.
            if (k !in priorByClaim) out[k] = line
        }
        val merged = out.values.take(MAX_CLAIMS)

        // 5) Persist any newly-concluded claim for next time.
        val toPersist = fresh.filter { it.claim.lowercase() !in priorByClaim }
        if (toPersist.isNotEmpty()) {
            runCatching {
                ContextStore.add(
                    context, name,
                    toPersist.map {
                        ContextStore.entry(ContextKind.DEDUCTION, encode(it), SOURCE, 0.95f)
                    },
                )
            }
        }
        return merged
    }

    // --- persistence round-trip --------------------------------------------
    // Stored as a single ContextStore DEDUCTION text line, source "claim-verifier":
    //   "claim|<STATUS>|<claim> :: <basis>"

    private fun encode(line: ClaimLine): String =
        "claim|${line.status.name}|${line.claim} :: ${line.basis}"

    private fun decode(text: String): ClaimLine? {
        if (!text.startsWith("claim|")) return null
        val rest = text.removePrefix("claim|")
        val bar = rest.indexOf('|')
        if (bar < 0) return null
        val status = runCatching { ClaimStatus.valueOf(rest.take(bar)) }.getOrNull() ?: return null
        val body = rest.substring(bar + 1)
        val sep = body.indexOf(" :: ")
        val claim = (if (sep >= 0) body.take(sep) else body).trim()
        val basis = if (sep >= 0) body.substring(sep + 4).trim() else ""
        if (claim.isBlank()) return null
        return ClaimLine(claim, status, basis)
    }

    private suspend fun loadPriors(context: Context, name: String): List<ClaimLine> =
        ContextStore.snapshot(context, name)
            .filter { it.source == SOURCE }
            .mapNotNull { decode(it.text) }

    // --- deterministic claim extraction (no model) -------------------------

    /**
     * Pull a few short marketing phrases from the product's own text — sentence-ish
     * fragments that read like an assertion. Honest and conservative: it never judges,
     * it only surfaces what the material says so the user can see it flagged as unverified.
     */
    private fun extractFallback(sourceMaterial: String): List<String> {
        val text = sourceMaterial
            .substringBefore("FROM THE WEB")
            .substringBefore("DIRECTIONS TO USE")
        return text.split(Regex("[\\n.!|•]+"))
            .map { it.trim().removePrefix("- ").trim() }
            .filter { it.length in 12..90 && it.any { c -> c.isLetter() } }
            .filterNot { it.startsWith("PRODUCT TEXT", ignoreCase = true) }
            .distinct()
            .take(MAX_CLAIMS)
    }

    // --- model prompt + parse ----------------------------------------------

    private fun prompt(name: String, sourceMaterial: String, web: List<WebSearch.WebResult>): String = buildString {
        appendLine("You are a careful, skeptical reviewer. From the product's own marketing below, pick the TOP ${MAX_CLAIMS} CLAIMS it asserts")
        appendLine("(a phrase the product promises, e.g. \"firms and plumps\", \"real results\", \"clinically proven\").")
        appendLine("For EACH claim, weigh THREE angles together and give one honest verdict:")
        appendLine("  - mechanism: does it plausibly work the way implied?")
        appendLine("  - evidence: what does general consensus / typical evidence say?")
        appendLine("  - reports: what do the web snippets and reviews below suggest?")
        appendLine()
        appendLine("Return ONLY a JSON array, no prose, of objects with these keys:")
        appendLine("  claim: the marketing phrase, short")
        appendLine("  status: one of SUPPORTED, PLAUSIBLE, UNVERIFIED, OVERREACH")
        appendLine("  basis: ONE plain line (max 18 words) explaining the verdict across mechanism + evidence + reports")
        appendLine("Do NOT invent studies, certifications, percentages or numbers. If you cannot judge a claim, mark it UNVERIFIED.")
        appendLine()
        appendLine("Product: $name")
        appendLine("Marketing material:")
        appendLine(sourceMaterial.take(MAX_SOURCE_CHARS).ifBlank { name })
        if (web.isNotEmpty()) {
            appendLine("What the web says:")
            web.take(4).forEach { r -> appendLine("- ${r.domain}: ${(r.snippet.ifBlank { r.title }).take(160)}") }
        }
        appendLine()
        appendLine("Reply with only the JSON array.")
    }

    private const val MAX_SOURCE_CHARS = 1200

    private fun parse(raw: String): List<ClaimLine> {
        val candidate = IngredientSkill.arrayFrom(raw) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(candidate)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val claim = o.optString("claim").trim()
                if (claim.isBlank()) return@mapNotNull null
                val status = runCatching {
                    ClaimStatus.valueOf(o.optString("status").trim().uppercase())
                }.getOrDefault(ClaimStatus.UNVERIFIED)
                ClaimLine(claim.take(100), status, o.optString("basis").trim().take(140))
            }
        }.getOrDefault(emptyList())
    }
}
