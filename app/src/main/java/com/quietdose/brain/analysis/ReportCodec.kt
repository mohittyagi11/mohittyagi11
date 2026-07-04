package com.quietdose.brain.analysis

import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.IngredientCodec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes a finished [AnalysisReport] to a compact JSON string for the single
 * [com.quietdose.data.entity.ItemEntity.analysis] column, so a completed analysis is
 * PERSISTED and re-opened instantly instead of being recomputed on the model each time.
 *
 * Only the fields the report page actually renders are stored — the dynamic [ReportBlock]
 * list, the [Recommendation] prefill, the parsed [AnalysisReport.ingredients], and a couple
 * of header fields. The unused analysis internals ([AnalysisReport.sections]/[synthesis]/
 * [safety]) are reconstructed as empty on decode. Decoding NEVER throws: a bad or
 * partial blob yields null, and callers simply fall back to running a fresh analysis.
 */
object ReportCodec {

    /** Bumped if the on-disk shape changes incompatibly; an older blob then decodes to null. */
    private const val VERSION = 1

    fun encode(report: AnalysisReport): String? = runCatching {
        JSONObject().apply {
            put("v", VERSION)
            put("title", report.title)
            report.matchedIngredientKey?.let { put("mk", it) }
            put("byModel", report.byModel)
            put("rec", encodeRecommendation(report.recommendation))
            IngredientCodec.encode(report.ingredients)?.let { put("ings", it) }
            put("blocks", JSONArray().apply { report.blocks.forEach { put(encodeBlock(it)) } })
        }.toString()
    }.getOrNull()

    fun decode(s: String?): AnalysisReport? {
        if (s.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(s)
            if (o.optInt("v") != VERSION) return null
            val blocks = o.optJSONArray("blocks")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::decodeBlock) }
            }.orEmpty()
            if (blocks.isEmpty()) return null
            AnalysisReport(
                title = o.optString("title"),
                matchedIngredientKey = o.optString("mk").ifBlank { null },
                sections = emptyList(),
                synthesis = Synthesis("", "", null, emptyList(), emptyList()),
                recommendation = decodeRecommendation(o.optJSONObject("rec")),
                safety = emptyList(),
                safetyReviewedClear = emptyList(),
                blocks = blocks,
                ingredients = IngredientCodec.decode(o.optString("ings").ifBlank { null }),
                grounded = emptyList(),
                byModel = o.optBoolean("byModel"),
            )
        }.getOrNull()
    }

    /* ------------------------------- Recommendation ------------------------------- */

    private fun encodeRecommendation(r: Recommendation) = JSONObject().apply {
        r.groupId?.let { put("gid", it) }
        r.groupReason?.let { put("gr", it) }
        put("da", r.doseAmount)
        put("du", r.doseUnit.name)
        r.typicalLow?.let { put("lo", it) }
        r.typicalHigh?.let { put("hi", it) }
        put("flags", r.flags)
        put("pair", JSONArray(r.pairings))
    }

    private fun decodeRecommendation(o: JSONObject?): Recommendation {
        if (o == null) return Recommendation(null, null, 1.0, DoseUnit.UNIT, null, null, 0, emptyList())
        return Recommendation(
            groupId = if (o.has("gid")) o.optLong("gid") else null,
            groupReason = o.optString("gr").ifBlank { null },
            doseAmount = o.optDouble("da", 1.0),
            doseUnit = runCatching { DoseUnit.valueOf(o.optString("du")) }.getOrDefault(DoseUnit.UNIT),
            typicalLow = if (o.has("lo")) o.optDouble("lo") else null,
            typicalHigh = if (o.has("hi")) o.optDouble("hi") else null,
            flags = o.optInt("flags"),
            pairings = o.optJSONArray("pair").toStringList(),
        )
    }

    /* ------------------------------- Blocks ------------------------------- */

    private fun encodeBlock(b: ReportBlock): JSONObject = when (b) {
        is ReportBlock.Verdict -> JSONObject().apply {
            put("t", "verdict")
            put("verdict", b.verdict); put("rationale", b.rationale)
            b.score?.let { put("score", it) }
            put("tags", JSONArray(b.tags)); put("byModel", b.byModel)
            put("dims", JSONArray().apply {
                b.dimensions.forEach { put(JSONObject().put("l", it.label).put("s", it.score).put("w", it.why)) }
            })
            put("benefits", JSONArray(b.benefits))
            put("bsym", JSONObject().apply { b.benefitSymbols.forEach { (k, v) -> put(k, v) } })
        }
        is ReportBlock.Ingredients -> JSONObject().apply {
            put("t", "ingredients")
            put("items", JSONArray().apply {
                b.items.forEach { put(JSONObject().put("n", it.name).put("r", it.role).put("note", it.note).put("sev", it.severity.name)) }
            })
        }
        is ReportBlock.Claims -> JSONObject().apply {
            put("t", "claims")
            put("items", JSONArray().apply {
                b.items.forEach { put(JSONObject().put("c", it.claim).put("st", it.status.name).put("b", it.basis)) }
            })
        }
        is ReportBlock.Reviews -> JSONObject().apply {
            put("t", "reviews")
            put("takeaway", b.takeaway)
            put("loved", JSONArray(b.loved)); put("watch", JSONArray(b.watch)); put("sources", JSONArray(b.sources))
            put("state", b.state.name)
        }
        is ReportBlock.Aspect -> JSONObject().apply {
            put("t", "aspect")
            put("aspect", b.aspect.name)
            b.summary?.let { put("summary", it) }
            put("lines", encodeLines(b.lines))
            b.score?.let { put("score", it) }
            put("state", b.state.name)
        }
        is ReportBlock.Chapter -> JSONObject().apply {
            put("t", "chapter")
            put("title", b.title)
            b.summary?.let { put("summary", it) }
            put("lines", encodeLines(b.lines))
            put("state", b.state.name)
        }
        is ReportBlock.Meter -> JSONObject().apply {
            put("t", "meter")
            put("label", b.label); put("valueText", b.valueText); put("fraction", b.fraction.toDouble())
            b.bandLow?.let { put("lo", it.toDouble()) }
            b.bandHigh?.let { put("hi", it.toDouble()) }
            b.markerFraction?.let { put("mark", it.toDouble()) }
            b.caption?.let { put("caption", it) }
        }
        is ReportBlock.Facts -> JSONObject().apply {
            put("t", "facts")
            put("title", b.title)
            put("rows", JSONArray().apply { b.rows.forEach { put(JSONObject().put("k", it.first).put("v", it.second)) } })
        }
        is ReportBlock.Reasoning -> JSONObject().apply {
            put("t", "reasoning")
            put("items", JSONArray(b.items)); put("byModel", b.byModel)
        }
    }

    private fun decodeBlock(o: JSONObject): ReportBlock? = when (o.optString("t")) {
        "verdict" -> ReportBlock.Verdict(
            verdict = o.optString("verdict"),
            rationale = o.optString("rationale"),
            score = if (o.has("score")) o.optInt("score") else null,
            tags = o.optJSONArray("tags").toStringList(),
            byModel = o.optBoolean("byModel"),
            dimensions = o.optJSONArray("dims")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val d = arr.optJSONObject(i) ?: return@mapNotNull null
                    RatingDim(d.optString("l"), d.optInt("s"), d.optString("w"))
                }
            }.orEmpty(),
            benefits = o.optJSONArray("benefits").toStringList(),
            benefitSymbols = o.optJSONObject("bsym")?.let { js ->
                js.keys().asSequence().associateWith { js.optString(it) }
            }.orEmpty(),
        )
        "ingredients" -> ReportBlock.Ingredients(
            items = o.optJSONArray("items")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val it = arr.optJSONObject(i) ?: return@mapNotNull null
                    IngredientLine(it.optString("n"), it.optString("r"), it.optString("note"), severityOf(it.optString("sev")))
                }
            }.orEmpty(),
        )
        "claims" -> ReportBlock.Claims(
            items = o.optJSONArray("items")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val it = arr.optJSONObject(i) ?: return@mapNotNull null
                    ClaimLine(it.optString("c"), claimStatusOf(it.optString("st")), it.optString("b"))
                }
            }.orEmpty(),
        )
        "reviews" -> ReportBlock.Reviews(
            takeaway = o.optString("takeaway"),
            loved = o.optJSONArray("loved").toStringList(),
            watch = o.optJSONArray("watch").toStringList(),
            sources = o.optJSONArray("sources").toStringList(),
            state = aspectStateOf(o.optString("state")),
        )
        "aspect" -> ReportBlock.Aspect(
            aspect = runCatching { AnalysisAspect.valueOf(o.optString("aspect")) }.getOrDefault(AnalysisAspect.WHAT_FOR),
            summary = o.optString("summary").ifBlank { null },
            lines = decodeLines(o.optJSONArray("lines")),
            score = if (o.has("score")) o.optInt("score") else null,
            state = aspectStateOf(o.optString("state")),
        )
        "chapter" -> ReportBlock.Chapter(
            title = o.optString("title"),
            summary = o.optString("summary").ifBlank { null },
            lines = decodeLines(o.optJSONArray("lines")),
            state = aspectStateOf(o.optString("state")),
        )
        "meter" -> ReportBlock.Meter(
            label = o.optString("label"),
            valueText = o.optString("valueText"),
            fraction = o.optDouble("fraction").toFloat(),
            bandLow = if (o.has("lo")) o.optDouble("lo").toFloat() else null,
            bandHigh = if (o.has("hi")) o.optDouble("hi").toFloat() else null,
            markerFraction = if (o.has("mark")) o.optDouble("mark").toFloat() else null,
            caption = o.optString("caption").ifBlank { null },
        )
        "facts" -> ReportBlock.Facts(
            title = o.optString("title"),
            rows = o.optJSONArray("rows")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val it = arr.optJSONObject(i) ?: return@mapNotNull null
                    it.optString("k") to it.optString("v")
                }
            }.orEmpty(),
        )
        "reasoning" -> ReportBlock.Reasoning(items = o.optJSONArray("items").toStringList(), byModel = o.optBoolean("byModel"))
        else -> null
    }

    /* ------------------------------- shared ------------------------------- */

    private fun encodeLines(lines: List<AnalysisLine>) = JSONArray().apply {
        lines.forEach { put(JSONObject().put("x", it.text).put("sev", it.severity.name)) }
    }

    private fun decodeLines(arr: JSONArray?): List<AnalysisLine> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            AnalysisLine(o.optString("x"), severityOf(o.optString("sev")))
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
    }

    private fun severityOf(s: String) = runCatching { Severity.valueOf(s) }.getOrDefault(Severity.NEUTRAL)
    private fun claimStatusOf(s: String) = runCatching { ClaimStatus.valueOf(s) }.getOrDefault(ClaimStatus.UNVERIFIED)
    private fun aspectStateOf(s: String) = runCatching { AspectState.valueOf(s) }.getOrDefault(AspectState.NOT_ASSESSED)
}
