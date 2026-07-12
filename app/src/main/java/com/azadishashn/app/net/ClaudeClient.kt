package com.azadishashn.app.net

import com.azadishashn.app.model.Epilogue
import com.azadishashn.app.model.Ideologies
import com.azadishashn.app.model.OptionCard
import com.azadishashn.app.model.RoundData
import com.azadishashn.app.model.Scandal
import com.azadishashn.app.model.ScandalVerdict
import com.azadishashn.app.model.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * Thin client over the Anthropic Messages API.
 *
 * Every call uses structured outputs (output_config.format) so the response is
 * guaranteed-parseable JSON matching our schema — no prose parsing on-device.
 *
 * All network access in the app funnels through this class; a backend proxy
 * would replace the [baseUrl] / auth here without touching game logic.
 */
class ClaudeClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://api.anthropic.com/v1/messages",
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Generate a fresh round leaning into [themes] (any of PESTEL, deep, dark, trigger, etc.).
     * [avoidTitles] nudges Claude away from repeats. [era] pins the period; [storySoFar]
     * conditions the scenario on the nation's meters and the table's recent choices, so the
     * world remembers what players did. [onProgress] receives streamed display text
     * (title, then the situation as it types out) for the loader.
     */
    suspend fun generateRound(
        themes: List<String>,
        context: String,
        avoidTitles: List<String>,
        language: String = "en",
        readLangs: List<String> = listOf("en"),
        era: String = "",
        storySoFar: String = "",
        onProgress: ((String) -> Unit)? = null,
    ): RoundData = withContext(Dispatchers.IO) {
        val avoid = if (avoidTitles.isEmpty()) "" else
            "\nDo NOT reuse these scenario titles: ${avoidTitles.joinToString(", ")}."
        val themeLine = if (themes.isEmpty()) "any compelling dimension" else themes.joinToString(", ")
        val eraLine = if (era.isBlank()) "" else
            "\nSET THE SCENARIO IN THIS ERA: $era. Use its vocabulary, institutions, technology, " +
                "and what was politically thinkable then."
        val storyLine = if (storySoFar.isBlank()) "" else
            "\nTHE STORY SO FAR (this game's running world-state — let it shape the scenario; if a " +
                "meter is strained, crises of that kind are brewing; recent choices may echo):\n$storySoFar"
        val user = """
            Generate ONE round for SHASN: Azadi, a debate game about power and governance.

            Set the scenario in a $context context. Draw on dilemmas that leaders, reformers, and
            revolutionaries ANYWHERE in the world have actually faced, but frame each as a situation
            playing out in $context — its institutions, regions, society, and stakes. This is NOT
            limited to $context's own historical problems; it is the world's dilemmas, localised.$eraLine$storyLine

            Theme(s) to lean hard into: $themeLine.
            Range WIDELY across eras and governance structures. Pull from political, social,
            technological, environmental, legal, ethical, philosophical, dark, and provocative
            angles. Do NOT default to economic or industrial crises.

            The four ideologies are fixed:
            $IDEOLOGY_BRIEF

            Produce EXACTLY 4 options — ONE for EACH ideology above (use each ideology name
            exactly once in the "ideology" field).

            Requirements:
            - The scenario is a concrete government/revolution situation, real-history-inspired
              or a plausible future, that fits the theme(s) above.
            - scenario.dimension: a short category label (1-4 words) naming the question's
              dimension(s), e.g. "Surveillance · Technology" or "Migration · Society".
            - scenario.situation: a vivid paragraph (2-4 sentences) that sets the scene.
            - The dilemma must be a decision real leaders or revolutionaries actually faced;
              real_world_note says what real ones did and how it turned out.
            - Each option is that ideology's genuine, defensible course of action (NOT true/false)
              — four live positions worth arguing over.
            - Keep titles, option labels, and the question itself to one line each.
            - paths: EXACTLY 4 entries, ONE per ideology (use each ideology name once). For each,
              think like a high-end policy analyst: stance (its one-line position), outcome (where
              that plausibly leads), risk (what it costs). Crisp, causal, non-partisan — one line each.
            - blocs: 2-3 stakeholder blocs with real skin in THIS decision (e.g. farmers,
              industrialists, students, army, clergy, unions, urban middle class, media barons) —
              short names, fitting the scenario. They will judge the answer from the sidelines.
            - mood: the question's political weather, derived from the scenario's nature.
              favors = the ONE ideology this moment naturally plays toward; suspects = the ONE
              ideology facing headwind here (must differ); note = one crisp line why. Vary this
              genuinely with the scenario — different questions, different moods.$avoid
            ${languageLine(language)}
            ${narrationLine(readLangs, "the scenario title", "the situation followed by the dilemma question")}
        """.trimIndent()

        val round = requestJson<RoundData>(ROUND_SYSTEM, user, roundSchema(), onProgress)
        round.copy(options = normaliseOptions(round.options))
    }

    /** Re-cast a scenario with a complication that makes the easy answer costly. */
    suspend fun twistRound(
        current: RoundData,
        language: String = "en",
        readLangs: List<String> = listOf("en"),
        onProgress: ((String) -> Unit)? = null,
    ): RoundData = withContext(Dispatchers.IO) {
        val user = """
            Here is the current round:
            Title: ${current.scenario.title}
            Situation: ${current.scenario.situation}
            Question: ${current.dilemma.question}

            Re-issue the SAME scenario but inject one hard complication (a crisis, a constraint,
            a hidden cost) that makes the most obvious or popular answer politically costly —
            so a player cannot easily "farm" the safe ideology.

            Keep the same title. Produce EXACTLY 4 options, ONE for EACH ideology:
            $IDEOLOGY_BRIEF
            Keep every field punchy. Also refresh paths, keep (or sharpen) the same 2-3
            stakeholder blocs, and RE-READ the mood: the complication may shift which ideology
            the moment favors/suspects.
            ${languageLine(language)}
            ${narrationLine(readLangs, "the scenario title", "the situation followed by the dilemma question")}
        """.trimIndent()

        val round = requestJson<RoundData>(ROUND_SYSTEM, user, roundSchema(), onProgress)
        round.copy(options = normaliseOptions(round.options))
    }

    /**
     * Impartial verdict on a player's argument. Decides which of the four ideologies the
     * argument genuinely makes the case for and scores its strength 0..3. This is the award
     * decision — it does not depend on the other players, so it can't be stalled.
     */
    suspend fun judge(
        round: RoundData,
        argument: String,
        language: String = "en",
        readLangs: List<String> = listOf("en"),
        rebuttal: String = "",
        closer: String = "",
        rebutterName: String = "",
        pastPositions: List<String> = emptyList(),
        crisis: String = "",
    ): Verdict = withContext(Dispatchers.IO) {
        val opts = round.options.joinToString("\n") { "- ${it.ideology}: ${it.label}" }
        val blocList = round.blocs.ifEmpty { listOf("the public") }
        val who = rebutterName.ifBlank { "An opponent" }
        val exchange = buildString {
            if (rebuttal.isNotBlank()) append("\nThe QUESTIONER $who then cross-examined: \"$rebuttal\"")
            if (closer.isNotBlank()) append("\nThe player CLOSED: \"$closer\"")
            if (isNotEmpty()) append(
                "\nWeigh the full exchange — a strong rebuttal left unanswered weakens the case; " +
                    "a sharp closer restores it.",
            )
        }
        val record = if (pastPositions.isEmpty()) "" else
            "\nTHE PLAYER'S PUBLIC RECORD this game (their past positions, on the books):\n" +
                pastPositions.joinToString("\n") { "- $it" }
        val leakLine = if (crisis.isBlank()) "" else
            "\nMID-ARGUMENT, this BREAKING news dropped on the player: \"$crisis\" — they had to " +
                "absorb it live. Weigh how composedly the answer handled the ambush."
        // When a cross-examination happened, the papers must cover the fight itself.
        val clashLine = if (rebuttal.isBlank()) "" else
            " A cross-examination HAPPENED tonight: at least ONE of the two front pages MUST " +
                "cover the CLASH itself — name $who, and say who drew blood in the exchange."
        val user = """
            Scenario: ${round.scenario.title} — ${round.scenario.situation}
            Question: ${round.dilemma.question}

            For reference only, how each of the four ideologies might lean here:
            $opts

            The player answered the question in their OWN words (they were NOT shown the list above):
            "$argument"$exchange$record$leakLine

            Be an impartial judge — ignore who benefits in the game. Judge the player's actual words.
            1. primary_ideology: the ONE of the four ideologies (Capitalist, Supremo, Showstopper,
               Idealist) the answer MOST embodies.
            2. secondary_ideology: the NEXT most dominant ideology present in the answer, chosen from
               the OTHER three. It MUST be different from primary_ideology.
            3. strength: on a 1-10 scale, how STRONGLY and convincingly the answer favoured the
               primary ideology (1 = barely, 5 = a solid case, 10 = an overwhelming, expert case).
            4. reasoning: one line on the dominant and secondary leanings.
            5. historical_outcome: one line on what real leaders who took the primary path got.
            6. causal_chain: 3-4 steps, reasoning like a high-end policy analyst — a tight causal
               chain from the chosen stance to its consequences. Each step: label (the effect, a
               few words), mechanism (the "→ because/therefore" that links the PREVIOUS step to
               this one), horizon (immediate | short_term | long_term, roughly ordered), polarity
               (gain | cost | mixed). End at the long-run / historical echo.
            7. tradeoff: one sharp line naming the central cost-of-power tradeoff of this path.
            8. stance_summary: ONE line recording, for the public record, the position the player took.
            9. headlines: EXACTLY 2 partisan front pages reporting this answer from OPPOSITE outlets
               (invent outlet names that fit the setting; slant = the outlet's leaning). Same answer,
               two spins — punchy, real-tabloid energy, one flattering and one brutal.$clashLine
            10. consistency: how this answer sits against the player's public record above. verdict:
               first_stand (no record yet) | consistent | evolved (a pivot argued well — reward it) |
               flipflop (a naked U-turn). note: one wry line, as the press would put it.
            11. bloc_reactions: for EACH of these watching blocs — ${blocList.joinToString(", ")} —
               one line in the bloc's own voice on how the answer lands with them, and delta -2..2
               (their support movement). A politician cannot please everyone; let reactions differ.
            12. poll_delta: -10..10 snap-poll approval movement, following from the bloc reactions,
               consistency, and the answer's raw persuasive force.
            13. nation_effects: if this answer were enacted, how each nation meter nudges, each
               -3..3: economy, liberty, stability, trust.
            ${languageLine(language)}
            ${narrationLine(readLangs, "a short verdict headline naming which ideology the answer served", "the one-line reasoning and the historical outcome")}
        """.trimIndent()

        requestJson<Verdict>(ADJUDICATE_SYSTEM, user, judgeSchema())
    }

    /** A scandal surfaced from the player's own record; the press demands an answer. */
    suspend fun scandal(
        playerName: String,
        pastStance: String,
        context: String,
        language: String = "en",
    ): Scandal = withContext(Dispatchers.IO) {
        val user = """
            In a $context political setting, invent ONE juicy but plausible scandal that surfaces
            from this politician's own record:

            Politician: $playerName
            Their past position, on the books: "$pastStance"

            The scandal must grow OUT of that position (a contract to a relative, a leaked memo,
            an inconvenient beneficiary, a broken promise it implies). Keep it sharp and playable:
            - headline: the breaking front-page line.
            - story: 2-3 sentences of what the press claims.
            - question: the single question the press pack is shouting — what they must answer NOW.
            ${languageLine(language)}
        """.trimIndent()
        requestJson<Scandal>(ROUND_SYSTEM, user, scandalSchema())
    }

    /** Score a scandal response purely as damage control. */
    suspend fun judgeScandal(
        scandal: Scandal,
        response: String,
        language: String = "en",
    ): ScandalVerdict = withContext(Dispatchers.IO) {
        val user = """
            A politician faces this scandal:
            "${scandal.headline}" — ${scandal.story}
            The press demands: ${scandal.question}

            Their public response: "$response"

            Score it purely as DAMAGE CONTROL (denial, deflection, apology, counter-attack are all
            legitimate plays — judge execution, not morality):
            - handling: 1-10 (1 = poured petrol on it, 10 = masterful defusal).
            - note: one wry press-gallery line on the performance.
            - poll_delta: -8..5 approval movement (scandals rarely help).
            ${languageLine(language)}
        """.trimIndent()
        requestJson<ScandalVerdict>(ADJUDICATE_SYSTEM, user, scandalVerdictSchema())
    }

    /** The end-of-game epilogue: where the nation landed, given everything the table chose. */
    suspend fun epilogue(
        context: String,
        meters: String,
        story: List<String>,
        language: String = "en",
    ): Epilogue = withContext(Dispatchers.IO) {
        val user = """
            The game is over. Write the closing chapter for this $context nation — a title and
            4-6 sentences of vivid, historically-voiced prose, like the final page of a great
            political biography.

            Final nation meters (0-100): $meters
            The choices made, in order:
            ${story.joinToString("\n") { "- $it" }}

            Derive the ending honestly from the meters and choices — triumphs, scars, and the one
            question history will keep asking this nation. No bullet points; flowing prose.
            ${languageLine(language)}
        """.trimIndent()
        requestJson<Epilogue>(ROUND_SYSTEM, user, epilogueSchema())
    }

    // -- HTTP plumbing -------------------------------------------------------

    /**
     * Call the API and decode the JSON reply into [T], retrying once on a
     * transient failure (a malformed or cut-off reply) — a fresh generation
     * usually succeeds. On a second failure, surface a friendly, actionable
     * message instead of a raw parser exception.
     */
    private inline fun <reified T> requestJson(
        system: String,
        user: String,
        schema: JsonObject,
        noinline onProgress: ((String) -> Unit)? = null,
    ): T {
        var attempt = 0
        while (true) {
            attempt++
            try {
                return json.decodeFromString<T>(call(system, user, schema, onProgress))
            } catch (e: SerializationException) {
                if (attempt >= 2) {
                    throw IOException("Claude returned a reply the game couldn't read — please try again.")
                }
            } catch (e: IOException) {
                // Includes the max_tokens cut-off below; retry once, then rethrow.
                if (attempt >= 2) throw e
            }
        }
    }

    private fun call(
        system: String,
        user: String,
        schema: JsonObject,
        onProgress: ((String) -> Unit)? = null,
    ): String {
        val stream = onProgress != null
        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", MAX_TOKENS)
            // System prompt as a cacheable block — it's identical on every call,
            // so prompt caching trims latency and cost.
            put("system", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", system)
                    put("cache_control", buildJsonObject { put("type", "ephemeral") })
                })
            })
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", user)
                })
            })
            put("output_config", buildJsonObject {
                put("format", buildJsonObject {
                    put("type", "json_schema")
                    put("schema", schema)
                })
            })
            if (stream) put("stream", true)
        }

        val request = Request.Builder()
            .url(baseUrl)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: InterruptedIOException) {
            throw IOException(
                "Claude took too long to respond. Try again, switch to a faster model " +
                    "(Sonnet or Haiku) in Settings, or use a bundled round.",
            )
        }
        response.use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                throw IOException("Claude API ${resp.code}: ${extractError(body)}")
            }
            if (stream) return readStream(resp, onProgress!!)
            val body = resp.body?.string().orEmpty()
            val root = json.parseToJsonElement(body).jsonObject
            // A truncated reply is incomplete JSON — catch it here with a clear
            // message rather than letting the parser choke on half a string.
            if (root["stop_reason"]?.jsonPrimitive?.content == "max_tokens") {
                throw IOException("Claude's reply was cut off before it finished — please try again.")
            }
            return extractText(root)
        }
    }

    /**
     * Read an SSE stream, accumulating the text deltas into the full JSON reply.
     * As the reply types out, surface the scenario's title (then its situation)
     * to [onProgress] so the loader shows the round taking shape live.
     */
    private fun readStream(resp: okhttp3.Response, onProgress: (String) -> Unit): String {
        val acc = StringBuilder()
        var lastShown = ""
        var stopReason: String? = null
        val source = resp.body?.source() ?: throw IOException("Empty streaming response")
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            if (data.isEmpty() || data == "[DONE]") continue
            val event = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
            when (event["type"]?.jsonPrimitive?.content) {
                "content_block_delta" -> {
                    event["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.content?.let { acc.append(it) }
                    val shown = progressPreview(acc)
                    if (shown.isNotEmpty() && shown != lastShown) {
                        lastShown = shown
                        onProgress(shown)
                    }
                }
                "message_delta" -> {
                    stopReason = event["delta"]?.jsonObject
                        ?.get("stop_reason")?.jsonPrimitive?.content ?: stopReason
                }
            }
        }
        if (stopReason == "max_tokens") {
            throw IOException("Claude's reply was cut off before it finished — please try again.")
        }
        return acc.toString()
    }

    /**
     * A human preview from a partially-streamed round JSON: the scenario title
     * once it's complete, then the situation as it types out (capped for the UI).
     */
    private fun progressPreview(acc: StringBuilder): String {
        val s = acc.toString()
        val title = STREAM_TITLE.find(s)?.groupValues?.get(1)?.let(::unescapeJson) ?: return ""
        val situation = STREAM_SITUATION.find(s)?.groupValues?.get(1)?.let(::unescapeJson)
        return if (situation.isNullOrBlank()) title else "$title\n${situation.take(160)}"
    }

    private fun unescapeJson(raw: String): String =
        raw.replace("\\n", " ").replace("\\\"", "\"").replace("\\\\", "\\")

    /** Pull the first text block out of the already-parsed Messages API response. */
    private fun extractText(root: JsonObject): String {
        val content = root["content"]?.jsonArray
            ?: throw IOException("Unexpected response shape")
        for (block in content) {
            val obj = block.jsonObject
            if (obj["type"]?.jsonPrimitive?.content == "text") {
                obj["text"]?.jsonPrimitive?.content?.let { return it }
            }
        }
        throw IOException("No text block in response")
    }

    private fun extractError(body: String): String = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]
            ?.jsonObject?.get("message")?.jsonPrimitive?.content
    }.getOrNull() ?: body.take(200)

    /** Defend against the model returning other than four options. */
    private fun normaliseOptions(options: List<OptionCard>): List<OptionCard> = when {
        options.size >= 4 -> options.take(4)
        else -> options
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()

        // Output budget. Generous headroom: one round packs scenario + 4 options +
        // 4 paths + blocs + up to 3 Devanagari narration entries; a verdict adds
        // headlines, bloc reactions, consistency, polls, and nation effects.
        private const val MAX_TOKENS = 10000

        // Extract "title" / "situation" values from a partially-streamed round JSON.
        private val STREAM_TITLE = Regex("\"title\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        private val STREAM_SITUATION = Regex("\"situation\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)")

        private const val ROUND_SYSTEM =
            "You are the game master for Azadi Shashn, a debate party game about freedom and " +
                "governance. You write vivid, historically-grounded government scenarios and four " +
                "sharply different ideological responses that real people would argue over. Stay " +
                "balanced — never editorialise about which ideology is correct."

        private const val ADJUDICATE_SYSTEM =
            "You are a neutral political historian adjudicating a debate game. You classify which " +
                "ideology an argument embodies and add grounded historical context, without taking " +
                "sides or deciding the winner — the players vote on that."

        // -- JSON-schema helpers (structured outputs) ------------------------

        private fun strProp(): JsonObject = buildJsonObject { put("type", "string") }

        private fun intProp(): JsonObject = buildJsonObject { put("type", "integer") }

        private fun ideologyEnumProp(): JsonObject = buildJsonObject {
            put("type", "string")
            put("enum", buildJsonArray { Ideologies.NAMES.forEach { add(it) } })
        }

        private fun enumProp(values: List<String>): JsonObject = buildJsonObject {
            put("type", "string")
            put("enum", buildJsonArray { values.forEach { add(it) } })
        }

        private fun arraySchema(items: JsonObject): JsonObject = buildJsonObject {
            put("type", "array")
            put("items", items)
        }

        /** Short brief on the four SHASN ideologies, used in every prompt. */
        private val IDEOLOGY_BRIEF: String = Ideologies.ALL.joinToString("\n") {
            "- ${it.name} (earns ${it.resource}): ${it.blurb}"
        }

        private const val KEEP_TOKENS =
            "Do NOT translate the fixed token fields: keep every \"ideology\" value exactly as " +
                "Capitalist, Supremo, Showstopper, or Idealist, keep \"horizon\" " +
                "(immediate|short_term|long_term) and \"polarity\" (gain|cost|mixed) as their " +
                "English enum values, and keep every \"lang\" value exactly as en, hinglish, or hi."

        /** Human description of a read-aloud language code. */
        private fun langDesc(code: String): String = when (code) {
            "hi" -> "Hindi in Devanagari script"
            "hinglish" -> "Hinglish — conversational Hindi written in the Roman/Latin alphabet (NOT Devanagari), mixing common English words as Indians naturally do"
            else -> "natural, fluent English"
        }

        /**
         * Force the output language for all natural-language fields, while keeping
         * the schema-constrained token fields in their fixed English form so the
         * structured output still validates.
         */
        private fun languageLine(code: String): String = when (code) {
            "en" -> "Write all natural-language output in natural, fluent English. $KEEP_TOKENS"
            else ->
                "IMPORTANT: write ALL natural-language fields — title, dimension, setting, era, " +
                    "situation, question, real_world_note, every option label and summary, every " +
                    "path stance/outcome/risk, reasoning, historical_outcome, every causal-chain " +
                    "label/mechanism, and tradeoff — in ${langDesc(code)}. $KEEP_TOKENS"
        }

        /**
         * Ask for a localized + read-aloud rendering in each selected language,
         * folded into the call. Crucially, Hinglish DISPLAY is Roman script but its
         * `speak` is Devanagari, so a Hindi TTS voice pronounces it naturally.
         */
        private fun narrationLine(readLangs: List<String>, titleDesc: String, bodyDesc: String): String {
            if (readLangs.isEmpty()) return ""
            val list = readLangs.joinToString(", ")
            return "Also fill `narration`: an ARRAY with exactly one entry per language in [$list]. " +
                "Each entry has: lang (en | hinglish | hi); title ($titleDesc, in that language); " +
                "text ($bodyDesc, in that language, 2-3 sentences); speak (the title and text together " +
                "as natural spoken sentences for text-to-speech). SCRIPT RULES — en: everything in " +
                "English. hinglish: write title and text in ROMAN/Latin Hindi (Hinglish, casually " +
                "mixing common English words), BUT write speak in DEVANAGARI Hindi (देवनागरी) so a Hindi " +
                "voice reads it correctly. hi: write everything in Devanagari Hindi. Keep ideology " +
                "names in English."
        }

        private fun objSchema(required: List<String>, props: Map<String, JsonElement>): JsonObject =
            buildJsonObject {
                put("type", "object")
                put("additionalProperties", false)
                put("required", buildJsonArray { required.forEach { add(it) } })
                put("properties", buildJsonObject { props.forEach { (k, v) -> put(k, v) } })
            }

        /** Schema for one localized narration entry: { lang, title, text, speak }. */
        private fun narrationItemSchema(): JsonObject = objSchema(
            listOf("lang", "title", "text", "speak"),
            mapOf(
                "lang" to enumProp(listOf("en", "hinglish", "hi")),
                "title" to strProp(),
                "text" to strProp(),
                "speak" to strProp(),
            ),
        )

        private fun roundSchema(): JsonObject {
            val optionSchema = objSchema(
                listOf("id", "ideology", "label", "summary"),
                mapOf(
                    "id" to strProp(),
                    "ideology" to ideologyEnumProp(),
                    "label" to strProp(),
                    "summary" to strProp(),
                ),
            )
            val pathSchema = objSchema(
                listOf("ideology", "stance", "outcome", "risk"),
                mapOf(
                    "ideology" to ideologyEnumProp(),
                    "stance" to strProp(),
                    "outcome" to strProp(),
                    "risk" to strProp(),
                ),
            )
            val moodSchema = objSchema(
                listOf("favors", "suspects", "note"),
                mapOf(
                    "favors" to ideologyEnumProp(),
                    "suspects" to ideologyEnumProp(),
                    "note" to strProp(),
                ),
            )
            return objSchema(
                listOf("scenario", "dilemma", "options", "paths", "narration", "blocs", "mood"),
                mapOf(
                    "mood" to moodSchema,
                    "scenario" to objSchema(
                        listOf("title", "dimension", "setting", "era", "situation"),
                        mapOf(
                            "title" to strProp(),
                            "dimension" to strProp(),
                            "setting" to strProp(),
                            "era" to strProp(),
                            "situation" to strProp(),
                        ),
                    ),
                    "dilemma" to objSchema(
                        listOf("question", "real_world_note"),
                        mapOf("question" to strProp(), "real_world_note" to strProp()),
                    ),
                    "options" to arraySchema(optionSchema),
                    "paths" to arraySchema(pathSchema),
                    "narration" to arraySchema(narrationItemSchema()),
                    "blocs" to arraySchema(strProp()),
                ),
            )
        }

        private fun judgeSchema(): JsonObject {
            val stepSchema = objSchema(
                listOf("label", "mechanism", "horizon", "polarity"),
                mapOf(
                    "label" to strProp(),
                    "mechanism" to strProp(),
                    "horizon" to enumProp(listOf("immediate", "short_term", "long_term")),
                    "polarity" to enumProp(listOf("gain", "cost", "mixed")),
                ),
            )
            val headlineSchema = objSchema(
                listOf("outlet", "slant", "headline"),
                mapOf("outlet" to strProp(), "slant" to strProp(), "headline" to strProp()),
            )
            val blocSchema = objSchema(
                listOf("bloc", "reaction", "delta"),
                mapOf("bloc" to strProp(), "reaction" to strProp(), "delta" to intProp()),
            )
            return objSchema(
                listOf(
                    "primary_ideology", "secondary_ideology", "strength", "reasoning",
                    "historical_outcome", "causal_chain", "tradeoff", "stance_summary",
                    "headlines", "consistency", "bloc_reactions", "poll_delta",
                    "nation_effects", "narration",
                ),
                mapOf(
                    "primary_ideology" to ideologyEnumProp(),
                    "secondary_ideology" to ideologyEnumProp(),
                    "strength" to intProp(),
                    "reasoning" to strProp(),
                    "historical_outcome" to strProp(),
                    "causal_chain" to arraySchema(stepSchema),
                    "tradeoff" to strProp(),
                    "stance_summary" to strProp(),
                    "headlines" to arraySchema(headlineSchema),
                    "consistency" to objSchema(
                        listOf("verdict", "note"),
                        mapOf(
                            "verdict" to enumProp(listOf("first_stand", "consistent", "evolved", "flipflop")),
                            "note" to strProp(),
                        ),
                    ),
                    "bloc_reactions" to arraySchema(blocSchema),
                    "poll_delta" to intProp(),
                    "nation_effects" to objSchema(
                        listOf("economy", "liberty", "stability", "trust"),
                        mapOf(
                            "economy" to intProp(),
                            "liberty" to intProp(),
                            "stability" to intProp(),
                            "trust" to intProp(),
                        ),
                    ),
                    "narration" to arraySchema(narrationItemSchema()),
                ),
            )
        }

        private fun scandalSchema(): JsonObject = objSchema(
            listOf("headline", "story", "question"),
            mapOf("headline" to strProp(), "story" to strProp(), "question" to strProp()),
        )

        private fun scandalVerdictSchema(): JsonObject = objSchema(
            listOf("handling", "note", "poll_delta"),
            mapOf("handling" to intProp(), "note" to strProp(), "poll_delta" to intProp()),
        )

        private fun epilogueSchema(): JsonObject = objSchema(
            listOf("title", "text"),
            mapOf("title" to strProp(), "text" to strProp()),
        )
    }
}
