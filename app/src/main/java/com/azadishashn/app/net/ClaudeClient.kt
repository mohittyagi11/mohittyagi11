package com.azadishashn.app.net

import com.azadishashn.app.model.Ideologies
import com.azadishashn.app.model.OptionCard
import com.azadishashn.app.model.RoundData
import com.azadishashn.app.model.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Generate a fresh round leaning into [themes] (any of PESTEL, deep, dark, trigger, etc.).
     * [avoidTitles] nudges Claude away from repeats.
     */
    suspend fun generateRound(
        themes: List<String>,
        context: String,
        avoidTitles: List<String>,
    ): RoundData = withContext(Dispatchers.IO) {
        val avoid = if (avoidTitles.isEmpty()) "" else
            "\nDo NOT reuse these scenario titles: ${avoidTitles.joinToString(", ")}."
        val themeLine = if (themes.isEmpty()) "any compelling dimension" else themes.joinToString(", ")
        val user = """
            Generate ONE round for SHASN: Azadi, a debate game about power and governance.

            Set the scenario in a $context context. Draw on dilemmas that leaders, reformers, and
            revolutionaries ANYWHERE in the world have actually faced, but frame each as a situation
            playing out in $context — its institutions, regions, society, and stakes. This is NOT
            limited to $context's own historical problems; it is the world's dilemmas, localised.

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
            - The dilemma must be a decision real leaders or revolutionaries actually faced;
              real_world_note says what real ones did and how it turned out.
            - Each option is that ideology's genuine, defensible course of action (NOT true/false)
              — four live positions worth arguing over.
            - Keep every field punchy: one or two sentences.$avoid
        """.trimIndent()

        val text = call(system = ROUND_SYSTEM, user = user, schema = roundSchema())
        val round = json.decodeFromString<RoundData>(text)
        round.copy(options = normaliseOptions(round.options))
    }

    /** Re-cast a scenario with a complication that makes the easy answer costly. */
    suspend fun twistRound(current: RoundData): RoundData = withContext(Dispatchers.IO) {
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
            Keep every field punchy.
        """.trimIndent()

        val text = call(system = ROUND_SYSTEM, user = user, schema = roundSchema())
        val round = json.decodeFromString<RoundData>(text)
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
    ): Verdict = withContext(Dispatchers.IO) {
        val opts = round.options.joinToString("\n") { "- ${it.ideology}: ${it.label}" }
        val user = """
            Scenario: ${round.scenario.title} — ${round.scenario.situation}
            Question: ${round.dilemma.question}

            For reference only, how each of the four ideologies might lean here:
            $opts

            The player answered the question in their OWN words (they were NOT shown the list above):
            "$argument"

            Be an impartial judge — ignore who benefits in the game. Judge the player's actual words.
            1. primary_ideology: the ONE of the four ideologies (Capitalist, Supremo, Showstopper,
               Idealist) the answer MOST embodies.
            2. secondary_ideology: the NEXT most dominant ideology present in the answer, chosen from
               the OTHER three. It MUST be different from primary_ideology.
            3. reasoning: one line on the dominant and secondary leanings.
            4. historical_outcome: one line on what real leaders who took the primary path got.
        """.trimIndent()

        val text = call(system = ADJUDICATE_SYSTEM, user = user, schema = judgeSchema())
        json.decodeFromString<Verdict>(text)
    }

    // -- HTTP plumbing -------------------------------------------------------

    private fun call(system: String, user: String, schema: JsonObject): String {
        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", 2048)
            put("system", system)
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
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw IOException("Claude API ${it.code}: ${extractError(body)}")
            }
            return extractText(body)
        }
    }

    /** Pull the first text block out of the Messages API response. */
    private fun extractText(body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
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

        /** Short brief on the four SHASN ideologies, used in every prompt. */
        private val IDEOLOGY_BRIEF: String = Ideologies.ALL.joinToString("\n") {
            "- ${it.name} (earns ${it.resource}): ${it.blurb}"
        }

        private fun objSchema(required: List<String>, props: Map<String, JsonElement>): JsonObject =
            buildJsonObject {
                put("type", "object")
                put("additionalProperties", false)
                put("required", buildJsonArray { required.forEach { add(it) } })
                put("properties", buildJsonObject { props.forEach { (k, v) -> put(k, v) } })
            }

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
            return objSchema(
                listOf("scenario", "dilemma", "options"),
                mapOf(
                    "scenario" to objSchema(
                        listOf("title", "setting", "era", "situation"),
                        mapOf(
                            "title" to strProp(),
                            "setting" to strProp(),
                            "era" to strProp(),
                            "situation" to strProp(),
                        ),
                    ),
                    "dilemma" to objSchema(
                        listOf("question", "real_world_note"),
                        mapOf("question" to strProp(), "real_world_note" to strProp()),
                    ),
                    "options" to buildJsonObject {
                        put("type", "array")
                        put("items", optionSchema)
                    },
                ),
            )
        }

        private fun judgeSchema(): JsonObject = objSchema(
            listOf("primary_ideology", "secondary_ideology", "reasoning", "historical_outcome"),
            mapOf(
                "primary_ideology" to ideologyEnumProp(),
                "secondary_ideology" to ideologyEnumProp(),
                "reasoning" to strProp(),
                "historical_outcome" to strProp(),
            ),
        )
    }
}
