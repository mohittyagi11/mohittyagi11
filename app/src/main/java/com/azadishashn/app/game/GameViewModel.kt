package com.azadishashn.app.game

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azadishashn.app.data.CrisisDeck
import com.azadishashn.app.data.GameStore
import com.azadishashn.app.data.OfflineContent
import com.azadishashn.app.data.SettingsStore
import com.azadishashn.app.model.AwardResult
import com.azadishashn.app.model.ClashRuling
import com.azadishashn.app.model.DossierEntry
import com.azadishashn.app.model.Epilogue
import com.azadishashn.app.model.Ideologies
import com.azadishashn.app.model.NationState
import com.azadishashn.app.model.NewsItem
import com.azadishashn.app.model.Player
import com.azadishashn.app.model.RoundData
import com.azadishashn.app.model.Scandal
import com.azadishashn.app.model.Themes
import com.azadishashn.app.model.Verdict
import com.azadishashn.app.net.ClaudeClient
import com.azadishashn.app.tts.Speaker
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.random.Random

@Serializable
enum class Screen { Library, Setup, Settings, Round, Result, Standings, Edit, Transfer, Playbook }

/**
 * What one ideology demands of the current answerer RIGHT NOW: the [required]
 * strength to KEEP its card, how many they [held], and the visible modifiers —
 * [moodAdj] (±1 already folded into [required]) and [meterAdj] (crisis +1 /
 * golden-age −1 applied to argued STRENGTH, not the bar).
 */
data class LiveBar(
    val ideology: String,
    val required: Int,
    val held: Int,
    val moodAdj: Int,
    val meterAdj: Int,
    /** The calibrated table anchor the bar starts from (median of recent verdicts − 1). */
    val anchor: Double,
    /** The table's average holding of this ideology — [held] above it raises the bar. */
    val tableAvg: Double,
)

@Serializable
data class GameState(
    val screen: Screen = Screen.Setup,
    /** Stable identity for the multi-game library (UUID). Empty for a brand-new, unsaved state. */
    val id: String = "",
    /** Display name shown in the library; defaults from the player names. */
    val title: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** True once the game has been finished (so the library can mark it done). */
    val finished: Boolean = false,
    val players: List<Player> = emptyList(),
    val activeIndex: Int = 0,
    val round: Int = 1,
    val starterId: Int? = null,
    val current: RoundData? = null,
    val championedOptionId: String? = null,
    val secondaryOptionId: String? = null,
    val lastResult: AwardResult? = null,
    val twistsUsedThisTurn: Int = 0,
    val loading: Boolean = false,
    /** What we're loading: "generate" | "twist" | "judge" | null — drives the loader captions. */
    val loadingKind: String? = null,
    /** Titles of scenarios already seen this game, so generation avoids repeats. Persisted. */
    val seenTitles: List<String> = emptyList(),
    val error: String? = null,
    val usingOffline: Boolean = false,
    // Theme picking (questioner, before the question is generated).
    val availableThemes: List<String> = emptyList(),
    val selectedThemes: Set<String> = emptySet(),
    val lastThemes: List<String> = emptyList(),
    /** When set, Standings is being viewed mid-game as a dashboard; resume returns here. */
    val dashboardReturn: Screen? = null,
    /** When set, the Export/Import screen is open; closing returns here. */
    val transferReturn: Screen? = null,
    /** When set, the Playbook (house-rules reference) is open; closing returns here. */
    val playbookReturn: Screen? = null,
    // -- The living nation (political-realism systems) ------------------------
    /** Four 0..100 meters the table's choices push around; conditions generation. */
    val nation: NationState = NationState(),
    /** playerId -> snap-poll approval 0..100 (default 50 until first verdict). */
    val approval: Map<Int, Int> = emptyMap(),
    /** playerId -> bloc name -> accumulated support (each verdict's deltas). */
    val blocSupport: Map<Int, Map<String, Int>> = emptyMap(),
    /** The public record: every position every player has taken this game. */
    val dossier: List<DossierEntry> = emptyList(),
    /** The party whip's secret instruction for this turn's answerer (null = no whip). */
    val assignedIdeology: String? = null,
    /** playerId -> blocs that have ENDORSED the player. EXCLUSIVE: one patron per
     *  bloc; a rival steals it by strictly out-courting the incumbent. */
    val endorsements: Map<Int, List<String>> = emptyMap(),
    /** Ideologies that received a card in the CURRENT round — neglected fronts decay. */
    val cardsThisRound: List<String> = emptyList(),
    /** THE PAPERS: every front page the judge has printed, oldest first. */
    val news: List<NewsItem> = emptyList(),
    // -- The Champion's Court (a turn's cross-examination, mid-flight) --------
    /** Provisional verdict awaiting the challenge window (null = no trial pending). */
    val pendingVerdict: Verdict? = null,
    /** The answer under trial, kept for the qualify/ruling calls. */
    val pendingArgument: String = "",
    /** Who holds standing: the topic's Champion (or runner-up). Null = nobody qualifies. */
    val challengerId: Int? = null,
    val pendingRebuttal: String = "",
    /** The court's qualification: CONTRADICTION | HERESY | SMEAR (null until qualified). */
    val challengeCategory: String? = null,
    val challengeConditions: String? = null,
    val compromiseHeadline: String? = null,
    // -- The questioner's tripwire (armed secretly during the question phase) --
    /** "word" | "time" | null (not armed). */
    val tripwireType: String? = null,
    /** The landmine word/phrase (word tripwire only). */
    val tripwireWord: String = "",
    val tripwireFired: Boolean = false,
    /** The crisis that fired, and the ideology it targets (the answerer's base). */
    val crisisLine: String = "",
    val crisisTarget: String = "",
    /** A pending scandal the active player must answer before the round starts. */
    val scandal: Scandal? = null,
    /** One-line result of the last scandal response (shown before theme pick). */
    val scandalResult: String? = null,
    /** Complication was leaked mid-argument this turn (judge weighs composure). */
    val leakUsed: Boolean = false,
    /** Pre-generated round waiting to make "Surprise me" instant. */
    val prefetched: RoundData? = null,
    /** Live streamed text (title/situation) while a round is generating. */
    val streamHint: String? = null,
    /** The generated end-of-game closing chapter. */
    val epilogue: Epilogue? = null,
) {
    val activePlayer: Player? get() = players.getOrNull(activeIndex)

    /** The questioner is the player seated before the turn-player: they set the theme and pose the card. */
    val questionerIndex: Int
        get() = if (players.isEmpty()) 0 else (activeIndex - 1 + players.size) % players.size
    val questioner: Player? get() = players.getOrNull(questionerIndex)
}

/**
 * Owns all game logic.
 *
 * Flow: configure players + pick who starts -> each turn the questioner chooses
 * theme(s) -> Claude generates a scenario in those themes -> turn-player argues
 * -> Claude judges and awards. The award is impartial (rivals can't stall); the
 * baseline only raises the bar the more of an ideology you already hold.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val store = GameStore(app)

    /** True while the read-aloud is speaking; the UI flips the button to Stop. */
    var isReading by mutableStateOf(false)
        private set
    private val mainHandler = Handler(Looper.getMainLooper())
    private val speaker = Speaker(app) { speaking -> mainHandler.post { isReading = speaking } }

    fun readOut(text: String) = speaker.speak(text, settings.language)
    fun readOut(text: String, lang: String) = speaker.speak(text, lang)
    fun stopReadOut() = speaker.stop()

    /** True when a non-English read-aloud should use the Devanagari `speak` text (a Hindi voice exists). */
    fun prefersDevanagari(lang: String): Boolean = lang != "en" && speaker.hindiAvailable()

    override fun onCleared() {
        speaker.shutdown()
        super.onCleared()
    }

    var state by mutableStateOf(GameState())
        private set

    val apiKey: String get() = settings.apiKey
    val model: String get() = settings.model
    val context: String get() = settings.context
    /** App language code ("en" | "hi") — drives AI output, narration and voice input. */
    val language: String get() = settings.language

    /** BCP-47 tag for the speech recogniser, auto-derived from [language]. */
    val speechTag: String get() = SettingsStore.bcp47(settings.language)

    /** Read-aloud languages chosen in Settings, in the canonical en → hinglish → hi order. */
    val readLangs: List<String>
        get() = listOf("en", "hinglish", "hi").filter { it in settings.readLangs }

    val hasKey: Boolean get() = settings.hasKey

    private var nextPlayerId = 0
    private val twistLimit = 2

    init {
        // Narrate in the chosen language.
        speaker.setLanguage(settings.language)

        // Fold any pre-library single-slot game into the library (one-time).
        store.migrateLegacyIfNeeded()

        // Restore the active game so app updates / restarts don't kill it. When
        // any games exist we land on the library (a games home); a clean first
        // run starts at Setup.
        val summaries = store.listSummaries()
        if (summaries.isNotEmpty()) {
            val activeId = store.activeId() ?: summaries.first().id
            val saved = store.loadGame(activeId)
            if (saved != null && saved.players.isNotEmpty()) {
                nextPlayerId = (saved.players.maxOfOrNull { it.id } ?: -1) + 1
                var restored = saved.copy(loading = false, loadingKind = null, error = null, screen = Screen.Library)
                // If we were killed at the theme-pick stage, repopulate the chips.
                if (restored.current == null && settings.hasKey && restored.availableThemes.isEmpty()) {
                    restored = restored.copy(availableThemes = Themes.sample(12))
                }
                state = restored
                store.setActive(activeId)
            } else {
                state = GameState(screen = Screen.Library)
            }
        }
        // Persist the active game on every change. The library view itself isn't
        // game progress, so don't fold its screen into the saved blob.
        viewModelScope.launch {
            snapshotFlow { state }.collect {
                if (it.screen != Screen.Library && it.players.isNotEmpty()) store.saveGame(it)
            }
        }
    }

    // -- Library (multiple games) --------------------------------------------

    /** Saved games for the library list, most-recently-updated first. */
    fun librarySummaries(): List<com.azadishashn.app.data.GameSummary> = store.listSummaries()

    /** Show the games home. Keeps the active game in memory so Resume is instant. */
    fun openLibrary() {
        state = state.copy(screen = Screen.Library)
    }

    /** Start configuring a brand-new game without disturbing the existing ones. */
    fun newGame() {
        val now = System.currentTimeMillis()
        val id = store.newId()
        nextPlayerId = 0
        ackedBars = emptyMap()
        store.setActive(id)
        state = GameState(screen = Screen.Setup, id = id, createdAt = now, updatedAt = now)
    }

    /** Re-open a saved game where it left off. */
    fun resumeGame(id: String) {
        val saved = store.loadGame(id)?.takeIf { it.players.isNotEmpty() } ?: return
        nextPlayerId = (saved.players.maxOfOrNull { it.id } ?: -1) + 1
        ackedBars = emptyMap()
        store.setActive(id)
        var restored = saved.copy(loading = false, loadingKind = null, error = null)
        if (restored.screen == Screen.Library) restored = restored.copy(screen = Screen.Round)
        if (restored.screen == Screen.Playbook) {
            restored = restored.copy(screen = restored.playbookReturn ?: Screen.Round, playbookReturn = null)
        }
        if (restored.screen == Screen.Round && restored.current == null &&
            settings.hasKey && restored.availableThemes.isEmpty()
        ) {
            restored = restored.copy(availableThemes = Themes.sample(12))
        }
        state = restored
    }

    fun deleteGame(id: String) {
        store.deleteGame(id)
        // If we deleted the in-memory game, drop it so it can't be re-saved.
        if (state.id == id) state = GameState(screen = Screen.Library)
    }

    fun renameGame(id: String, title: String) {
        val clean = title.trim().ifEmpty { return }
        if (state.id == id) {
            state = state.copy(title = clean)
        } else {
            store.loadGame(id)?.let { store.saveGame(it.copy(title = clean)) }
        }
    }

    // -- Setup ---------------------------------------------------------------

    fun addPlayer(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        state = state.copy(players = state.players + Player(nextPlayerId++, trimmed))
    }

    fun removePlayer(id: Int) {
        state = state.copy(players = state.players.filterNot { it.id == id })
    }

    /** Start the game oriented on the chosen first player. */
    fun startGame(firstPlayerId: Int) {
        if (state.players.size < 2) return
        val idx = state.players.indexOfFirst { it.id == firstPlayerId }.coerceAtLeast(0)
        // Ensure the game has an identity (a clean first run reaches Setup with none)
        // and a title derived from the table.
        val now = System.currentTimeMillis()
        val id = state.id.ifBlank { store.newId() }
        val title = state.title.ifBlank { state.players.joinToString(" · ") { it.name } }
        if (state.id.isBlank()) store.setActive(id)
        state = state.copy(
            id = id,
            title = title,
            createdAt = if (state.createdAt == 0L) now else state.createdAt,
            screen = Screen.Round,
            activeIndex = idx,
            round = 1,
            starterId = firstPlayerId,
        )
        beginTurn()
    }

    // -- Standing: who is heard on a cause -------------------------------------

    /**
     * The CHAMPION of a cause: the player holding STRICTLY more kept cards on
     * that line than everyone else. A tie means nobody is heard over the noise.
     */
    fun championOf(ideology: String): Player? {
        val best = state.players.maxOfOrNull { it.counts[ideology] ?: 0 } ?: 0
        if (best <= 0) return null
        return state.players.filter { (it.counts[ideology] ?: 0) == best }.singleOrNull()
    }

    /** Causes this player currently champions — worn as 📣 badges at the table. */
    fun championsOf(playerId: Int): List<String> =
        Ideologies.NAMES.filter { championOf(it)?.id == playerId }

    /**
     * Who holds standing to cross-examine an answer that served [primary]:
     * the cause's Champion — or, when the answerer IS the champion (on trial
     * on their own turf), the runner-up. Ties or an unclaimed cause → nobody.
     */
    private fun challengerFor(primary: String, answererId: Int): Player? {
        val champ = championOf(primary) ?: return null
        if (champ.id != answererId) return champ
        val rest = state.players.filter { it.id != answererId }
        val best = rest.maxOfOrNull { it.counts[primary] ?: 0 } ?: 0
        if (best <= 0) return null
        return rest.filter { (it.counts[primary] ?: 0) == best }.singleOrNull()
    }

    // -- Settings ------------------------------------------------------------

    fun openSettings() {
        state = state.copy(screen = Screen.Settings)
    }

    fun saveSettings(
        apiKey: String,
        model: String,
        context: String,
        language: String,
        readLangs: Set<String>,
        era: String = settings.era,
        partyLines: Boolean = settings.partyLines,
        fastJudge: Boolean = settings.fastJudge,
        scandals: Boolean = settings.scandals,
    ) {
        settings.apiKey = apiKey
        settings.model = model
        settings.context = context
        settings.language = language
        settings.readLangs = readLangs
        settings.era = era
        settings.partyLines = partyLines
        settings.fastJudge = fastJudge
        settings.scandals = scandals
        speaker.setLanguage(language)
    }

    val era: String get() = settings.era
    val partyLines: Boolean get() = settings.partyLines
    val fastJudge: Boolean get() = settings.fastJudge
    val scandalsOn: Boolean get() = settings.scandals

    fun closeSettings() {
        val back = if (state.players.isEmpty()) Screen.Setup else Screen.Round
        state = state.copy(screen = back)
    }

    // -- The Playbook & coach cards (natural learning for the house rules) ----

    /** Dismissed coach-card lesson ids, mirrored into state so the UI recomposes. */
    private var seenLessonsState by mutableStateOf(settings.seenLessons)

    fun hasSeenLesson(id: String): Boolean = id in seenLessonsState

    fun markLessonSeen(id: String) {
        settings.seenLessons = settings.seenLessons + id
        seenLessonsState = settings.seenLessons
    }

    /** Re-arm every coach card (Playbook's "replay" button). */
    fun resetLessons() {
        settings.seenLessons = emptySet()
        seenLessonsState = emptySet()
    }

    /** Open the house-rules reference; closing returns to wherever we were. */
    fun openPlaybook() {
        if (state.screen == Screen.Playbook) return
        state = state.copy(playbookReturn = state.screen, screen = Screen.Playbook)
    }

    fun closePlaybook() {
        state = state.copy(screen = state.playbookReturn ?: Screen.Library, playbookReturn = null)
    }

    // -- A turn --------------------------------------------------------------

    fun beginTurn() {
        speaker.stop()
        val base = state.copy(
            current = null,
            championedOptionId = null,
            secondaryOptionId = null,
            lastResult = null,
            twistsUsedThisTurn = 0,
            error = null,
            usingOffline = false,
            selectedThemes = emptySet(),
            screen = Screen.Round,
            // Political-realism turn resets. The whip is an EVENT, not a constant:
            // roughly every other turn it hands the answerer sealed instructions —
            // and it's ADVERSARIAL to their board build: it demands one of the two
            // ideologies they hold LEAST, pulling them off their farming track.
            // Obey for patronage, or rebel to protect the build.
            assignedIdeology = if (settings.partyLines && settings.hasKey && Random.nextFloat() < 0.5f) {
                val counts = state.activePlayer?.counts.orEmpty()
                Ideologies.NAMES.sortedBy { counts[it] ?: 0 }.take(2).random()
            } else null,
            scandal = null,
            scandalResult = null,
            leakUsed = false,
            streamHint = null,
            tripwireType = null,
            tripwireWord = "",
            tripwireFired = false,
            crisisLine = "",
            crisisTarget = "",
            pendingVerdict = null,
            pendingArgument = "",
            challengerId = null,
            pendingRebuttal = "",
            challengeCategory = null,
            challengeConditions = null,
            compromiseHeadline = null,
        )
        if (settings.hasKey) {
            // Show the questioner a theme picker; generation waits for [generate].
            state = base.copy(availableThemes = Themes.sample(12))
            maybeSurfaceScandal()
        } else {
            // Offline deck ignores themes — go straight to a bundled round.
            state = base.copy(availableThemes = emptyList())
            useOfflineRound()
        }
    }

    // -- Scandals: skeletons surface from the player's own record --------------

    /** Occasionally dig a scandal out of the active player's record (~1 turn in 4). */
    private fun maybeSurfaceScandal() {
        if (!settings.scandals || !settings.hasKey) return
        val active = state.activePlayer ?: return
        val record = state.dossier.filter { it.playerId == active.id }
        if (record.isEmpty() || Random.nextFloat() > 0.25f) return
        val past = record.random()
        val turnId = state.id
        viewModelScope.launch {
            runCatching {
                ClaudeClient(settings.apiKey, settings.model)
                    .scandal(active.name, "${past.roundTitle}: ${past.stance}", settings.context, settings.language)
            }.onSuccess { s ->
                // Only surface it if the table is still on this turn's theme pick.
                if (state.id == turnId && state.current == null && state.screen == Screen.Round) {
                    state = state.copy(scandal = s)
                }
            }
            // A failed scandal fetch is silently dropped — it was optional colour.
        }
    }

    /** The player answers the press; judged purely as damage control. */
    fun respondScandal(response: String) {
        val s = state.scandal ?: return
        val active = state.activePlayer ?: return
        if (response.isBlank()) return
        state = state.copy(loading = true, loadingKind = "judge", error = null)
        viewModelScope.launch {
            runCatching {
                judgeClient().judgeScandal(s, response, settings.language)
            }.onSuccess { v ->
                val newApproval = (approvalOf(active.id) + v.pollDelta).coerceIn(0, 100)
                state = state.copy(
                    loading = false,
                    loadingKind = null,
                    scandal = null,
                    scandalResult = "${v.note}  ·  Snap poll: $newApproval% (${signed(v.pollDelta)})",
                    approval = state.approval + (active.id to newApproval),
                )
            }.onFailure {
                // Scandals are colour, not core — dismiss rather than block the turn.
                state = state.copy(loading = false, loadingKind = null, scandal = null)
            }
        }
    }

    /** "No comment." The press smells blood: a small fixed approval hit. */
    fun dismissScandal() {
        val active = state.activePlayer ?: return
        val newApproval = (approvalOf(active.id) - 3).coerceIn(0, 100)
        state = state.copy(
            scandal = null,
            scandalResult = "\"No comment.\" The press smells blood.  ·  Snap poll: $newApproval% (-3)",
            approval = state.approval + (active.id to newApproval),
        )
    }

    /** Current snap-poll approval for a player (everyone starts at 50). */
    fun approvalOf(playerId: Int): Int = state.approval[playerId] ?: 50

    private fun signed(v: Int): String = if (v >= 0) "+$v" else "$v"

    /** Judge calls can route to the fastest model — verdicts are classification-shaped. */
    private fun judgeClient(): ClaudeClient =
        ClaudeClient(settings.apiKey, if (settings.fastJudge) SettingsStore.FAST_MODEL else settings.model)

    /** The running world-state fed into generation, so scenarios remember the game. */
    private fun storySoFar(): String {
        if (state.dossier.isEmpty()) return ""
        val n = state.nation
        val meters = "Economy ${n.economy}/100, Liberty ${n.liberty}/100, " +
            "Stability ${n.stability}/100, Institutional Trust ${n.trust}/100"
        val crises = listOf(
            "Economy" to n.economy, "Liberty" to n.liberty,
            "Stability" to n.stability, "Institutional Trust" to n.trust,
        ).filter { it.second < 25 }
        val crisisLine = if (crises.isEmpty()) "" else
            "\nTHE NATION IS IN CRISIS on: ${crises.joinToString(", ") { it.first }} — " +
                "the scenario MUST confront this crisis head-on."
        val recent = state.dossier.takeLast(3).joinToString("\n") {
            "- ${it.roundTitle}: ${it.playerName} took a ${it.ideology} line — ${it.stance}"
        }
        return "Nation meters: $meters$crisisLine\nRecent decisions:\n$recent"
    }

    fun toggleTheme(theme: String) {
        val sel = state.selectedThemes.toMutableSet()
        if (!sel.add(theme)) sel.remove(theme)
        state = state.copy(selectedThemes = sel)
    }

    /** Surface a fresh set of theme chips (keeping any already selected). */
    fun refreshThemes() {
        val fresh = (state.selectedThemes + Themes.sample(12)).toList().distinct()
        state = state.copy(availableThemes = fresh)
    }

    /** Generate the scenario in the selected themes (or a random one if none picked). */
    fun generate() {
        // No themes picked + a prefetched round waiting → instant surprise.
        if (state.selectedThemes.isEmpty()) {
            val ready = state.prefetched
            if (ready != null && !state.seenTitles.any { it.trim().equals(ready.scenario.title.trim(), true) }) {
                state = state.copy(
                    current = ready,
                    prefetched = null,
                    seenTitles = state.seenTitles + ready.scenario.title,
                    usingOffline = false,
                    error = null,
                )
                return
            }
        }
        val themes = if (state.selectedThemes.isNotEmpty()) {
            state.selectedThemes.toList()
        } else {
            listOf(Themes.random())
        }
        loadRound(themes)
    }

    private fun loadRound(themes: List<String>, retry: Boolean = false) {
        if (!settings.hasKey) {
            useOfflineRound()
            return
        }
        state = state.copy(
            loading = true, loadingKind = "generate", error = null,
            lastThemes = themes, streamHint = null,
        )
        val avoid = state.seenTitles.takeLast(20)
        viewModelScope.launch {
            runCatching {
                ClaudeClient(settings.apiKey, settings.model).generateRound(
                    themes, settings.context, avoid, settings.language, settings.readLangs.toList(),
                    era = settings.era,
                    storySoFar = storySoFar(),
                    onProgress = { hint -> mainHandler.post { if (state.loading) state = state.copy(streamHint = hint) } },
                )
            }.onSuccess { round ->
                val title = round.scenario.title.trim()
                val dup = state.seenTitles.any { it.trim().equals(title, ignoreCase = true) }
                if (dup && !retry) {
                    // Claude repeated a scenario — record it and try once more, explicitly avoiding it.
                    state = state.copy(seenTitles = state.seenTitles + round.scenario.title)
                    loadRound(themes, retry = true)
                } else {
                    state = state.copy(
                        current = round,
                        seenTitles = state.seenTitles + round.scenario.title,
                        loading = false,
                        loadingKind = null,
                        usingOffline = false,
                        streamHint = null,
                    )
                }
            }.onFailure { e ->
                state = state.copy(
                    loading = false, loadingKind = null, streamHint = null,
                    error = e.message ?: "Generation failed",
                )
            }
        }
    }

    /**
     * Quietly generate the NEXT round while the table debates this one, so the
     * next "Surprise me" is instant. Best-effort: failures are silently dropped.
     */
    private fun prefetchNext() {
        if (!settings.hasKey || state.prefetched != null) return
        val avoid = state.seenTitles.takeLast(20)
        val themes = listOf(Themes.random())
        viewModelScope.launch {
            runCatching {
                ClaudeClient(settings.apiKey, settings.model).generateRound(
                    themes, settings.context, avoid, settings.language, settings.readLangs.toList(),
                    era = settings.era,
                    storySoFar = storySoFar(),
                )
            }.onSuccess { round ->
                if (state.prefetched == null) state = state.copy(prefetched = round)
            }
        }
    }

    fun useOfflineRound() {
        // Avoid handing out the same bundled round twice in a row.
        val currentTitle = state.current?.scenario?.title
        val pool = OfflineContent.ROUNDS.filter { it.scenario.title != currentTitle }
            .ifEmpty { OfflineContent.ROUNDS }
        val round = pool[pool.indices.random()]
        state = state.copy(current = round, loading = false, loadingKind = null, error = null, usingOffline = true)
    }

    fun retryRound() {
        loadRound(state.lastThemes.ifEmpty { listOf(Themes.random()) })
    }

    /** Swap the current question for a fresh, non-repeating one (questioner's "change the card"). */
    fun changeQuestion() {
        if (state.loading) return
        val cur = state.current
        if (cur != null) {
            state = state.copy(seenTitles = state.seenTitles + cur.scenario.title)
        }
        val themes = state.lastThemes.ifEmpty {
            if (state.selectedThemes.isNotEmpty()) state.selectedThemes.toList() else listOf(Themes.random())
        }
        loadRound(themes)
    }

    fun champion(optionId: String) {
        state = state.copy(championedOptionId = optionId)
    }

    /** Offline only: the secondary (+1) ideology tag; tapping again clears it. */
    fun championSecondary(optionId: String) {
        state = state.copy(secondaryOptionId = if (state.secondaryOptionId == optionId) null else optionId)
    }

    fun twist() {
        val round = state.current ?: return
        if (!settings.hasKey || state.twistsUsedThisTurn >= twistLimit) return
        state = state.copy(loading = true, loadingKind = "twist", error = null)
        viewModelScope.launch {
            runCatching {
                ClaudeClient(settings.apiKey, settings.model).twistRound(round, settings.language, settings.readLangs.toList())
            }.onSuccess { twisted ->
                state = state.copy(
                    current = twisted,
                    championedOptionId = null,
                    twistsUsedThisTurn = state.twistsUsedThisTurn + 1,
                    loading = false,
                    loadingKind = null,
                )
            }.onFailure { e ->
                state = state.copy(loading = false, loadingKind = null, error = e.message ?: "Twist failed")
            }
        }
    }

    // -- The questioner's tripwire: an ambush armed BEFORE the answer ----------

    /**
     * Secretly arm a mid-argument crisis: [type] "word" fires the instant the
     * answer contains [word]; "time" fires at a hidden random moment. The crisis
     * TARGETS the answerer's most-stacked ideology — a strike at their base.
     */
    fun armTripwire(type: String, word: String = "") {
        if (!settings.hasKey || state.tripwireType != null) return
        if (type == "word" && word.isBlank()) return
        state = state.copy(tripwireType = type, tripwireWord = word.trim())
    }

    /**
     * The tripwire springs (called by the UI when the landmine word appears or
     * the timebomb's moment arrives). Instant + offline: the crisis line comes
     * from [CrisisDeck], aimed at the answerer's base. Stakes settle in [award].
     */
    fun fireTripwire() {
        if (state.tripwireFired || state.tripwireType == null) return
        val active = state.activePlayer ?: return
        val target = active.counts.filterValues { it > 0 }.maxByOrNull { it.value }?.key
            ?: Ideologies.NAMES.random()
        state = state.copy(
            tripwireFired = true,
            leakUsed = true,
            crisisLine = CrisisDeck.draw(target),
            crisisTarget = target,
        )
    }

    // -- Resolve: the Champion's Court -----------------------------------------
    //
    // The answer is judged FIRST (the topic emerges), then the cause's Champion
    // may rise; the judge qualifies their cross-examination into a category with
    // a PREDEFINED wager, and the answerer accepts the trial or compromises.

    /** The predefined tariff of a category: approval swings + a resource bounty. */
    private data class Wager(
        val fallAnswerer: Int,
        val fallChallenger: Int,
        val fallResource: Boolean,
        val holdChallenger: Int,
        val holdAnswerer: Int,
        val holdResource: Boolean,
    )

    private val wagers = mapOf(
        // Their own record betrays them — the heaviest, symmetric charge.
        "CONTRADICTION" to Wager(-4, 4, true, -4, 4, true),
        // The answer betrays the cause it claims — middleweight, symmetric.
        "HERESY" to Wager(-3, 3, true, -3, 3, true),
        // Theatre, thin on substance — cheap upside, brutal backlash. Your own
        // words qualify you: recklessness is priced by the tariff itself.
        "SMEAR" to Wager(-2, 2, false, -5, 3, false),
    )

    /** One line the wager card and Result can both print for a category. */
    fun wagerTerms(category: String, cause: String): Pair<String, String> {
        val w = wagers[category] ?: return "" to ""
        val res = Ideologies.resourceOf(cause)
        val fall = buildString {
            append("answerer ${w.fallAnswerer} approval · challenger +${w.fallChallenger}")
            if (w.fallResource) append(" and +1 $res")
        }
        val hold = buildString {
            append("challenger ${w.holdChallenger} approval · answerer +${w.holdAnswerer}")
            if (w.holdResource) append(" and +1 $res")
        }
        return fall to hold
    }

    private fun primaryOf(v: Verdict): String =
        v.primaryIdeology.takeIf { it in Ideologies.NAMES } ?: Ideologies.NAMES.first()

    private fun secondaryOf(v: Verdict): String {
        val p = primaryOf(v)
        return v.secondaryIdeology.takeIf { it in Ideologies.NAMES && it != p }
            ?: Ideologies.NAMES.first { it != p }
    }

    private fun recordOf(active: Player): List<String> =
        state.dossier.filter { it.playerId == active.id }
            .takeLast(4)
            .map { "\"${it.roundTitle}\": served ${it.ideology} — ${it.stance}" }

    /** How the street reads tonight — fed to the court so conditions carry the mood. */
    private fun nationReactionLine(active: Player): String = buildString {
        state.current?.mood?.note?.takeIf { it.isNotBlank() }?.let { append("Mood: $it. ") }
        val n = state.nation
        listOf("economy" to n.economy, "liberty" to n.liberty, "stability" to n.stability, "trust" to n.trust)
            .forEach { (name, v) ->
                if (v < 25) append("The $name is in CRISIS. ")
                if (v > 75) append("The $name basks in a golden age. ")
            }
        append("${active.name}'s approval stands at ${approvalOf(active.id)}%.")
    }

    /**
     * The answerer rests their case: the judge rules the topic and strength.
     * If a Champion holds standing on that cause, the challenge window opens;
     * otherwise the verdict awards immediately.
     */
    fun submitAnswer(argument: String) {
        val active = state.activePlayer ?: return
        val round = state.current ?: return

        if (settings.hasKey && argument.isNotBlank()) {
            state = state.copy(loading = true, loadingKind = "judge", error = null)
            viewModelScope.launch {
                runCatching {
                    judgeClient().judge(
                        round, argument, settings.language, settings.readLangs.toList(),
                        pastPositions = recordOf(active),
                        crisis = state.crisisLine,
                    )
                }.onSuccess { v ->
                    // The judge is pure: the card follows what the words ACTUALLY
                    // served. The whip settles its own account in award().
                    val challenger = challengerFor(primaryOf(v), active.id)
                    if (challenger == null) {
                        // The cause has no champion tonight — straight to the award.
                        award(active, primaryOf(v), secondaryOf(v), v.strength.coerceIn(1, 10), v)
                    } else {
                        state = state.copy(
                            loading = false,
                            loadingKind = null,
                            pendingVerdict = v,
                            pendingArgument = argument,
                            challengerId = challenger.id,
                        )
                    }
                }.onFailure { e ->
                    state = state.copy(loading = false, loadingKind = null, error = e.message ?: "Judging failed")
                }
            }
        } else {
            // Offline: the player self-tags primary (+2) and an optional secondary (+1).
            val primary = round.options.firstOrNull { it.id == state.championedOptionId }?.ideology ?: return
            val secondary = round.options.firstOrNull { it.id == state.secondaryOptionId }?.ideology
                ?.takeIf { it != primary }
                ?: Ideologies.NAMES.first { it != primary }
            award(active, primary, secondary, -1, null)
        }
    }

    /** The Champion lets the ruling stand — award the pending verdict unchanged. */
    fun waiveChallenge() {
        val v = state.pendingVerdict ?: return
        val active = state.activePlayer ?: return
        award(active, primaryOf(v), secondaryOf(v), v.strength.coerceIn(1, 10), v)
    }

    /**
     * The Champion spoke their cross-examination; the court QUALIFIES it into a
     * category whose wager is predefined — announced before anything settles.
     */
    fun submitRebuttal(rebuttal: String) {
        val v = state.pendingVerdict ?: return
        val active = state.activePlayer ?: return
        val round = state.current ?: return
        val challenger = state.players.firstOrNull { it.id == state.challengerId } ?: return
        if (rebuttal.isBlank()) {
            waiveChallenge(); return
        }
        state = state.copy(loading = true, loadingKind = "qualify", error = null, pendingRebuttal = rebuttal)
        viewModelScope.launch {
            runCatching {
                judgeClient().qualifyChallenge(
                    round, state.pendingArgument, v, rebuttal,
                    answererName = active.name,
                    challengerName = challenger.name,
                    pastPositions = recordOf(active),
                    nationReaction = nationReactionLine(active),
                    language = settings.language,
                )
            }.onSuccess { q ->
                state = state.copy(
                    loading = false,
                    loadingKind = null,
                    challengeCategory = q.category.takeIf { it in wagers.keys } ?: "SMEAR",
                    challengeConditions = q.conditions,
                    compromiseHeadline = q.compromiseHeadline,
                )
            }.onFailure { e ->
                state = state.copy(loading = false, loadingKind = null, error = e.message ?: "Qualification failed")
            }
        }
    }

    /**
     * The answerer refuses the wager: settled out of court. No wager is paid,
     * but the compromise DEFLECTS the card to the secondary — dodge the trial,
     * lose the line. The settlement makes the papers.
     */
    fun compromise() {
        val v = state.pendingVerdict ?: return
        val active = state.activePlayer ?: return
        award(
            active, primaryOf(v), secondaryOf(v), v.strength.coerceIn(1, 10), v,
            clashOutcome = "compromise",
        )
    }

    /** The answerer stands trial: closer spoken, the court rules the clash. */
    fun acceptWager(closer: String) {
        val v = state.pendingVerdict ?: return
        val active = state.activePlayer ?: return
        val round = state.current ?: return
        val challenger = state.players.firstOrNull { it.id == state.challengerId } ?: return
        val category = state.challengeCategory ?: return
        state = state.copy(loading = true, loadingKind = "clash", error = null)
        viewModelScope.launch {
            runCatching {
                judgeClient().ruleClash(
                    round, state.pendingArgument, v, category,
                    rebuttal = state.pendingRebuttal,
                    closer = closer,
                    answererName = active.name,
                    challengerName = challenger.name,
                    language = settings.language,
                )
            }.onSuccess { r ->
                award(
                    active, primaryOf(v), secondaryOf(v), r.finalStrength.coerceIn(1, 10), v,
                    clashOutcome = "ruled",
                    clashRuling = r,
                )
            }.onFailure { e ->
                state = state.copy(loading = false, loadingKind = null, error = e.message ?: "The ruling failed")
            }
        }
    }

    /**
     * Resources are always +2 [primary] / +1 [secondary], and exactly one CARD is ALWAYS
     * awarded — a judgement always lands. The baseline SHIFTS with standing (at the table
     * average for an ideology you need 5/10; each card you hold above/below the average
     * raises/lowers the bar) and only decides WHICH card: clear it and you keep stacking
     * [primary]; fall short and the card is redirected to [secondary], so a player already
     * accumulating an ideology needs a stronger argument to keep it. [strength] < 0 is
     * offline (a simple anti-hoard rule instead of a rating).
     */
    /** Which nation meter is [ideology]'s home turf. */
    private fun homeMeter(ideology: String): Int = when (ideology) {
        "Capitalist" -> state.nation.economy
        "Supremo" -> state.nation.stability
        "Showstopper" -> state.nation.liberty
        else -> state.nation.trust
    }

    /**
     * The bar for [player] arguing [ideology] RIGHT NOW — the exact math
     * [award] settles with (calibrated anchor + hoarding + mood), plus the
     * nation's strength modifier. One function for both the live Round-screen
     * display and the verdict, so they can never drift.
     */
    private fun barFor(player: Player, ideology: String, useMood: Boolean): LiveBar {
        val held = player.counts[ideology] ?: 0
        val tableAvg = state.players.map { it.counts[ideology] ?: 0 }.average()
        val mood = if (useMood) state.current?.mood else null
        val moodAdj = when (ideology) {
            mood?.favors -> -1
            mood?.suspects -> 1
            else -> 0
        }
        // CALIBRATED bar: the anchor follows this table's recent judged
        // strengths (rolling median of the last 8 verdicts, −1 for breathing
        // room), so a table of orators and a shy table live on the same drama
        // curve (~72% to close L1, ~59% L2, ~46% L3 — simulation-verified).
        val recent = state.dossier.takeLast(8).map { it.strength }.filter { it > 0 }
        val anchor = if (recent.size >= 4) recent.sorted()[recent.size / 2] - 1.0 else 5.0
        val required = (anchor + (held - tableAvg) + moodAdj).roundToInt().coerceIn(1, 10)
        val home = homeMeter(ideology)
        val meterAdj = if (home < 25) 1 else if (home > 75) -1 else 0
        return LiveBar(ideology, required, held, moodAdj, meterAdj, anchor, tableAvg)
    }

    /**
     * Live bars for the CURRENT answerer across all four ideologies — shown on
     * the Round screen so nobody argues blind. Everything here is public table
     * knowledge (cards, the record, the mood, the meters); the whip stays sealed.
     */
    fun liveBars(): List<LiveBar> {
        val p = state.activePlayer ?: return emptyList()
        return liveBarsFor(p)
    }

    /** The same live bars for ANY player — what tonight's question would demand of them. */
    fun liveBarsFor(player: Player): List<LiveBar> =
        Ideologies.NAMES.map { barFor(player, it, useMood = true) }

    /**
     * Bar-change acknowledgements (session-scoped): key -> the signature the
     * user last tapped. A tile whose current signature differs pulses until
     * tapped, so a moved bar or a fired crisis can't slip by unnoticed.
     */
    var ackedBars by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    fun acknowledgeBar(key: String, signature: Int) {
        ackedBars = ackedBars + (key to signature)
    }

    private fun award(
        active: Player,
        primary: String,
        secondary: String,
        strength: Int,
        verdict: Verdict?,
        clashOutcome: String? = null, // null | "compromise" | "ruled"
        clashRuling: ClashRuling? = null,
    ) {
        // The SAME live-bar math the Round screen displays — mood moves the
        // bar (the questioner set that weather); the calibrated anchor follows
        // the table; hoarding raises your own bar.
        val bar = barFor(active, primary, useMood = verdict != null)
        val held = bar.held
        val moodAdj = bar.moodAdj
        val required = bar.required
        val mood = if (verdict != null) state.current?.mood else null
        val moodNote = when {
            moodAdj < 0 -> "The mood favoured $primary — the bar dropped 1. ${mood?.note.orEmpty()}"
            moodAdj > 0 -> "The mood suspected $primary — the bar rose 1. ${mood?.note.orEmpty()}"
            else -> ""
        }

        // The nation feeds back into the cards: a meter in CRISIS empowers its
        // ideology (the hour demands it, +1 effective strength); a GOLDEN AGE
        // breeds complacency (nothing to rail against, -1).
        val meterAdj = if (strength < 0) 0 else bar.meterAdj
        val effStrength = if (strength < 0) strength else (strength + meterAdj).coerceIn(1, 10)

        // A card always lands; the baseline only decides whether it stays on [primary]
        // (keep stacking) or is redirected to [secondary] (you're hoarding [primary]).
        // A COMPROMISE forfeits the line outright: settle out of court, the card
        // is deflected to the secondary no matter how strong the answer was.
        val keepPrimary = when {
            clashOutcome == "compromise" -> false
            strength < 0 -> held < (state.players.minOf { it.counts[primary] ?: 0 }) + 2
            else -> effStrength >= required
        }
        val cardIdeology = if (keepPrimary) primary else secondary
        val diverted = !keepPrimary

        val explanation = buildString {
            if (clashOutcome == "compromise") {
                append("Settled out of court — no wager paid, but the card is DEFLECTED to $secondary. ")
            } else if (strength >= 0) {
                if (clashOutcome == "ruled") {
                    append("After the clash, the court re-ruled the case at $strength/10. ")
                    clashRuling?.reasoning?.takeIf { it.isNotBlank() }?.let { append("$it ") }
                }
                if (keepPrimary) {
                    append("Strength $effStrength/10 cleared the bar (needed $required). Earned 1 $primary card. ")
                } else {
                    append("Strength $effStrength/10 — needed $required to keep stacking $primary (you hold $held). Card goes to $secondary instead. ")
                }
                if (meterAdj > 0) append("(+1 crisis bonus — the nation demands $primary answers.) ")
                if (meterAdj < 0) append("(−1 golden-age malaise — $primary has nothing to rail against.) ")
            } else {
                append(if (keepPrimary) "Earned 1 $primary card. " else "You're hoarding $primary — card goes to $secondary instead. ")
            }
            append("Resources: $primary +2, $secondary +1.")
        }

        val updated = state.players.map { p ->
            if (p.id == active.id) {
                val c = p.counts.toMutableMap()
                c[cardIdeology] = (c[cardIdeology] ?: 0) + 1
                p.copy(counts = c)
            } else p
        }

        // Ideologue milestone: SHASN's board powers unlock as you stack one
        // ideology — the app celebrates and reminds the player to claim theirs.
        val newCount = (active.counts[cardIdeology] ?: 0) + 1
        val milestone = when (newCount) {
            2 -> "${newCount}× $cardIdeology — claim your LEVEL 1 ideologue power on the board!"
            4 -> "${newCount}× $cardIdeology — claim your LEVEL 2 ideologue power on the board!"
            6 -> "${newCount}× $cardIdeology — claim your LEVEL 3 ideologue power on the board!"
            else -> ""
        }

        // -- Political-realism effects (online verdicts only) ------------------

        // The whip settles its account: obey quietly, rebel gloriously, or pay.
        val whip = state.assignedIdeology.orEmpty()
        val whipOutcome = when {
            verdict == null || whip.isBlank() -> ""
            primary == whip -> "obeyed"
            strength >= 7 -> "rebel"
            else -> "punished"
        }
        val whipAdj = when (whipOutcome) {
            "obeyed" -> 3
            "rebel" -> 5
            "punished" -> -4
            else -> 0
        }

        // The tripwire crisis settles: hold the targeted line and clear the bar →
        // +1 bonus resource; argue it and fumble → the nation itself takes the hit.
        val crisisTarget = if (verdict != null) state.crisisTarget else ""
        val crisisOutcome = when {
            crisisTarget.isBlank() -> ""
            primary == crisisTarget && keepPrimary -> "weathered"
            primary == crisisTarget -> "claimed"
            else -> "swerved"
        }
        val crisisPollAdj = when (crisisOutcome) {
            "weathered" -> 2
            "claimed" -> -3
            else -> 0
        }
        val crisisMeterHit = if (crisisOutcome == "claimed") {
            when (crisisTarget) {
                "Capitalist" -> com.azadishashn.app.model.NationEffects(economy = -3)
                "Supremo" -> com.azadishashn.app.model.NationEffects(stability = -3)
                "Showstopper" -> com.azadishashn.app.model.NationEffects(liberty = -3)
                else -> com.azadishashn.app.model.NationEffects(trust = -3)
            }
        } else null

        val newNation = state.nation.applied(verdict?.nationEffects).applied(crisisMeterHit)
        val mergedBlocs = if (verdict != null && verdict.blocReactions.isNotEmpty()) {
            val mine = state.blocSupport[active.id].orEmpty().toMutableMap()
            verdict.blocReactions.forEach { r ->
                mine[r.bloc] = (mine[r.bloc] ?: 0) + r.delta.coerceIn(-2, 2)
            }
            state.blocSupport + (active.id to mine)
        } else state.blocSupport

        // Endorsements are EXCLUSIVE: each bloc backs ONE patron. You win it at
        // +3 support — or STEAL it by strictly out-courting the incumbent. You
        // lose it if your support with the bloc turns negative.
        val myBlocs = mergedBlocs[active.id].orEmpty()
        val had = state.endorsements[active.id].orEmpty()
        val kept = had.filter { (myBlocs[it] ?: 0) >= 0 }
        val defections = mutableMapOf<String, String>()   // bloc -> name it defected FROM
        val gained = mutableListOf<String>()
        if (verdict != null) {
            myBlocs.filterValues { it >= 3 }.keys.filterNot { it in kept }.forEach { bloc ->
                val holder = state.players.firstOrNull { pl ->
                    pl.id != active.id && bloc in state.endorsements[pl.id].orEmpty()
                }
                val holderSupport = holder?.let { state.blocSupport[it.id]?.get(bloc) } ?: Int.MIN_VALUE
                if (holder == null || (myBlocs[bloc] ?: 0) > holderSupport) {
                    gained += bloc
                    if (holder != null) defections[bloc] = holder.name
                }
            }
        }
        val myEndorsements = kept + gained
        val newEndorsementsMap = if (verdict != null) {
            val stripped = state.endorsements.mapValues { (pid, blocs) ->
                if (pid == active.id) blocs else blocs.filterNot { it in gained }
            }
            stripped + (active.id to myEndorsements)
        } else state.endorsements

        // -- The Champion's Court settles its wager ------------------------------
        // Predefined per category, announced before the trial: the loser pays in
        // approval, the winner may take a resource bounty of the disputed cause.
        val challenger = state.players.firstOrNull { it.id == state.challengerId }
        val trialCategory = if (clashOutcome != null) state.challengeCategory else null
        val trialOutcome = when {
            clashOutcome == "compromise" -> "compromise"
            clashOutcome == "ruled" && keepPrimary -> "held"
            clashOutcome == "ruled" -> "fell"
            else -> null
        }
        val wager = trialCategory?.let { wagers[it] }
        val res = Ideologies.resourceOf(primary)
        var answererWagerAdj = 0
        var challengerWagerAdj = 0
        var wagerLine: String? = null
        if (wager != null && challenger != null && trialOutcome == "fell") {
            answererWagerAdj = wager.fallAnswerer
            challengerWagerAdj = wager.fallChallenger
            wagerLine = "$trialCategory · the card FELL — ${active.name} ${wager.fallAnswerer} approval; " +
                "${challenger.name} +${wager.fallChallenger}" +
                (if (wager.fallResource) " and takes +1 $res" else "") + "."
        } else if (wager != null && challenger != null && trialOutcome == "held") {
            answererWagerAdj = wager.holdAnswerer
            challengerWagerAdj = wager.holdChallenger
            wagerLine = "$trialCategory · the card HELD — ${challenger.name} ${wager.holdChallenger} approval; " +
                "${active.name} +${wager.holdAnswerer}" +
                (if (wager.holdResource) " and takes +1 $res" else "") + "."
        } else if (trialOutcome == "compromise") {
            wagerLine = "Settled out of court — no wager paid; the card deflects to $secondary."
        }

        // Snap poll: the verdict's swing + the whip + the crisis + the trial's wager,
        // and every endorsed bloc adds +1 to a POSITIVE verdict swing (your machine turns out).
        val approvalBefore = approvalOf(active.id)
        val newApproval = if (verdict != null) {
            var delta = verdict.pollDelta.coerceIn(-10, 10)
            if (delta > 0) delta += kept.size
            delta += whipAdj + crisisPollAdj + answererWagerAdj
            (approvalBefore + delta).coerceIn(0, 100)
        } else approvalBefore
        // The challenger pays (or collects) their side of the wager on the same poll.
        val challengerApprovalAfter = if (challenger != null && challengerWagerAdj != 0) {
            (approvalOf(challenger.id) + challengerWagerAdj).coerceIn(0, 100)
        } else null

        // Everything cashes out into the game's REAL currencies: politics-earned
        // resource payouts the player physically takes (or returns) at the table.
        val grants = buildList {
            if (verdict != null) {
                if (whipOutcome == "obeyed") {
                    add(com.azadishashn.app.model.ResourceGrant("Whip patronage", whip, 1, "the party rewards loyalty"))
                }
                gained.forEach { bloc ->
                    add(com.azadishashn.app.model.ResourceGrant("Gift: $bloc", primary, 1, "the $bloc fund your machine"))
                }
                if (crisisOutcome == "weathered") {
                    add(com.azadishashn.app.model.ResourceGrant("Crisis bonus", crisisTarget, 1, "courage under fire"))
                }
                if (approvalBefore < 65 && newApproval >= 65) {
                    add(com.azadishashn.app.model.ResourceGrant("The mandate", primary, 1, "donors and volunteers pour in"))
                }
                if (approvalBefore > 35 && newApproval <= 35) {
                    add(com.azadishashn.app.model.ResourceGrant("Donors flee", primary, -1, "give one back — the ship is listing"))
                }
                // The trial's resource bounty — physically taken by whoever won.
                if (trialOutcome == "fell" && wager?.fallResource == true && challenger != null) {
                    add(com.azadishashn.app.model.ResourceGrant(
                        "Trial spoils → ${challenger.name}", primary, 1,
                        "the Champion defended the cause — ${challenger.name} takes it",
                    ))
                }
                if (trialOutcome == "held" && wager?.holdResource == true) {
                    add(com.azadishashn.app.model.ResourceGrant(
                        "Vindicated", primary, 1,
                        "survived the ${trialCategory?.lowercase()} — the cause rallies to you",
                    ))
                }
            }
        }
        val newDossier = if (verdict != null) {
            state.dossier + DossierEntry(
                playerId = active.id,
                playerName = active.name,
                roundTitle = state.current?.scenario?.title.orEmpty(),
                ideology = primary,
                stance = verdict.stanceSummary.ifBlank { verdict.reasoning },
                strength = strength.coerceIn(1, 10),
            )
        } else state.dossier

        // The papers go to the archive. A ruled clash rewrites tonight's front
        // pages (the fight IS the story); a compromise gets its settlement leak.
        val headlines = clashRuling?.headlines?.takeIf { it.isNotEmpty() }
            ?: verdict?.headlines.orEmpty()
        val newNews = if (verdict != null) {
            val base = state.news + headlines.map {
                NewsItem(state.round, active.name, it.outlet, it.slant, it.headline)
            }
            val settle = state.compromiseHeadline
            if (trialOutcome == "compromise" && !settle.isNullOrBlank()) {
                base + NewsItem(state.round, active.name, "The Court Circular", "smells a dodge", settle)
            } else base
        } else state.news

        val approvalUpdates = buildMap {
            if (verdict != null) put(active.id, newApproval)
            if (challenger != null && challengerApprovalAfter != null) {
                put(challenger.id, challengerApprovalAfter)
            }
        }
        state = state.copy(
            players = updated,
            nation = newNation,
            approval = state.approval + approvalUpdates,
            blocSupport = mergedBlocs,
            endorsements = newEndorsementsMap,
            dossier = newDossier,
            news = newNews,
            // The court adjourns: clear the turn's trial machinery.
            pendingVerdict = null,
            pendingArgument = "",
            challengerId = null,
            pendingRebuttal = "",
            challengeCategory = null,
            challengeConditions = null,
            compromiseHeadline = null,
            loading = false,
            loadingKind = null,
            lastResult = AwardResult(
                playerName = active.name,
                primary = primary,
                secondary = secondary,
                strength = if (strength < 0) strength else effStrength,
                required = required,
                cardAwarded = true,
                cardIdeology = cardIdeology,
                diverted = diverted,
                reasoning = verdict?.reasoning.orEmpty(),
                historicalNote = verdict?.historicalOutcome.orEmpty(),
                explanation = explanation,
                causalChain = verdict?.causalChain.orEmpty(),
                tradeoff = verdict?.tradeoff.orEmpty(),
                narration = verdict?.narration.orEmpty(),
                headlines = headlines,
                consistency = verdict?.consistency,
                blocReactions = verdict?.blocReactions.orEmpty(),
                pollDelta = verdict?.pollDelta ?: 0,
                approvalAfter = if (verdict != null) newApproval else -1,
                nationEffects = verdict?.nationEffects,
                whip = whip.takeIf { whipOutcome.isNotBlank() }.orEmpty(),
                whipOutcome = whipOutcome,
                whipPollAdj = whipAdj,
                crisisTarget = crisisTarget,
                crisisOutcome = crisisOutcome,
                newEndorsements = if (verdict != null) gained else emptyList(),
                defections = defections,
                bonusResources = grants,
                milestone = milestone,
                moodNote = moodNote,
                clashCategory = trialCategory,
                clashOutcome = trialOutcome,
                challengerName = challenger?.name.takeIf { trialOutcome != null },
                wagerLine = wagerLine,
                provisionalStrength = verdict?.strength.takeIf { clashOutcome == "ruled" },
                clashReasoning = clashRuling?.reasoning?.takeIf { it.isNotBlank() },
            ),
            cardsThisRound = state.cardsThisRound + cardIdeology,
            screen = Screen.Result,
        )
        // The table now debates the verdict — perfect cover to draft the next round.
        prefetchNext()
    }

    // -- Flow ----------------------------------------------------------------

    fun nextTurn() {
        val n = state.players.size
        val nextIndex = (state.activeIndex + 1) % n
        val starterIdx = state.players.indexOfFirst { it.id == state.starterId }.coerceAtLeast(0)
        // A round completes when the turn returns to whoever started.
        val roundDone = nextIndex == starterIdx
        val nextRound = if (roundDone) state.round + 1 else state.round

        // NEGLECT DECAY: when a round completes, every ideology that received NO
        // cards this round sees its home nation-meter rot (−3). Monoculture lets
        // the untended fronts slide into crisis — which then empowers exactly
        // those ideologies (+1 strength). The world pushes back on the farm.
        var nation = state.nation
        var cards = state.cardsThisRound
        if (roundDone && state.dossier.isNotEmpty()) {
            Ideologies.NAMES.filterNot { it in cards }.forEach { ideo ->
                nation = when (ideo) {
                    "Capitalist" -> nation.copy(economy = (nation.economy - 3).coerceAtLeast(0))
                    "Supremo" -> nation.copy(stability = (nation.stability - 3).coerceAtLeast(0))
                    "Showstopper" -> nation.copy(liberty = (nation.liberty - 3).coerceAtLeast(0))
                    else -> nation.copy(trust = (nation.trust - 3).coerceAtLeast(0))
                }
            }
            cards = emptyList()
        }

        state = state.copy(activeIndex = nextIndex, round = nextRound, nation = nation, cardsThisRound = cards)
        beginTurn()
    }

    /** Open Standings mid-game as a dashboard; [leaveDashboard] returns to where we were. */
    fun openDashboard() {
        if (state.screen == Screen.Standings) return
        state = state.copy(dashboardReturn = state.screen, screen = Screen.Standings)
    }

    fun leaveDashboard() {
        state = state.copy(screen = state.dashboardReturn ?: Screen.Round, dashboardReturn = null)
    }

    fun endGame() {
        state = state.copy(screen = Screen.Standings, dashboardReturn = null, finished = true)
    }

    /** Generate the closing chapter — where the nation landed, given every choice made. */
    fun generateEpilogue() {
        if (!settings.hasKey || state.epilogue != null || state.loading) return
        val n = state.nation
        val meters = "Economy ${n.economy}, Liberty ${n.liberty}, Stability ${n.stability}, Trust ${n.trust}"
        val story = state.dossier.map { "${it.roundTitle}: ${it.playerName} (${it.ideology}) — ${it.stance}" }
            .ifEmpty { listOf("A short, cautious government — few decisions of record.") }
        state = state.copy(loading = true, loadingKind = "judge", error = null)
        viewModelScope.launch {
            runCatching {
                ClaudeClient(settings.apiKey, settings.model)
                    .epilogue(settings.context, meters, story, settings.language)
            }.onSuccess { ep ->
                state = state.copy(loading = false, loadingKind = null, epilogue = ep)
            }.onFailure { e ->
                state = state.copy(loading = false, loadingKind = null, error = e.message ?: "Epilogue failed")
            }
        }
    }

    /**
     * Cross-game political fingerprints: for each player NAME, how often their
     * arguments actually served each ideology, aggregated over every saved game.
     */
    fun fingerprints(): Map<String, Map<String, Int>> {
        val all = store.listSummaries().mapNotNull { store.loadGame(it.id) } + listOf(state)
        return all.flatMap { it.dossier }
            .groupBy { it.playerName }
            .mapValues { (_, entries) -> entries.groupingBy { it.ideology }.eachCount() }
            .filterValues { it.isNotEmpty() }
    }

    // -- Manual repair (edit current game state) -----------------------------

    fun openEdit() {
        state = state.copy(screen = Screen.Edit)
    }

    fun cancelEdit() {
        state = state.copy(screen = Screen.Standings)
    }

    /**
     * Overwrite who started and each player's ideology-card counts, then DERIVE the
     * round and whose turn it is from the total cards (one card per completed turn):
     * round = totalCards / players + 1, and the next answerer = starter + totalCards.
     * Drops back into the current question with the edited state.
     */
    fun applyEdit(round: Int, turnInRound: Int, starterId: Int?, counts: Map<Int, Map<String, Int>>) {
        val players = state.players.map { p ->
            val c = counts[p.id]
            if (c != null) p.copy(counts = c.filterValues { it > 0 }) else p
        }
        val n = players.size.coerceAtLeast(1)
        val starterIdx = players.indexOfFirst { it.id == starterId }.coerceAtLeast(0)
        val turn = turnInRound.coerceIn(1, n)
        state = state.copy(
            players = players,
            starterId = players.getOrNull(starterIdx)?.id,
            round = round.coerceAtLeast(1),
            activeIndex = (starterIdx + (turn - 1)) % n,
            screen = Screen.Round,
            dashboardReturn = null,
        )
    }

    // -- Export / Import (take a copy, simulate, restore) --------------------

    fun openTransfer() {
        if (state.screen == Screen.Transfer) return
        state = state.copy(transferReturn = state.screen, screen = Screen.Transfer)
    }

    fun closeTransfer() {
        state = state.copy(screen = state.transferReturn ?: Screen.Setup, transferReturn = null)
    }

    /**
     * Serialize the current game to shareable JSON. The snapshot resumes at the
     * question (if one is loaded) or the dashboard, and drops the ephemeral
     * transfer/dashboard/loading bits so a re-import lands somewhere sensible.
     */
    fun exportState(): String {
        val snapshot = state.copy(
            screen = if (state.current != null) Screen.Round else Screen.Standings,
            transferReturn = null,
            dashboardReturn = null,
            playbookReturn = null,
            loading = false,
            error = null,
        )
        return store.export(snapshot)
    }

    /** Load a pasted/exported game as a NEW library entry. Returns false if it won't parse. */
    fun importState(raw: String): Boolean {
        val loaded = store.import(raw)?.takeIf { it.players.isNotEmpty() } ?: return false
        nextPlayerId = (loaded.players.maxOfOrNull { it.id } ?: -1) + 1
        val now = System.currentTimeMillis()
        // Mint a fresh identity so an imported copy doesn't overwrite its origin.
        val id = store.newId()
        store.setActive(id)
        state = loaded.copy(
            id = id,
            title = loaded.title.ifBlank { loaded.players.joinToString(" · ") { it.name } },
            createdAt = now,
            loading = false,
            loadingKind = null,
            error = null,
            transferReturn = null,
            dashboardReturn = null,
            playbookReturn = null,
        )
        return true
    }

    /**
     * Handle the system Back button — return to the previous screen instead of closing the app.
     * Back is a no-op on [Screen.Result] so it can't undo a resolved turn; not intercepted on
     * [Screen.Setup], so it exits there as usual.
     */
    fun onBack() {
        state = when (state.screen) {
            Screen.Settings -> state.copy(screen = if (state.players.isEmpty()) Screen.Setup else Screen.Round)
            Screen.Round -> state.copy(screen = Screen.Library)
            Screen.Result -> state
            Screen.Standings -> state.copy(screen = state.dashboardReturn ?: Screen.Library, dashboardReturn = null)
            Screen.Edit -> state.copy(screen = Screen.Standings)
            Screen.Transfer -> state.copy(screen = state.transferReturn ?: Screen.Library, transferReturn = null)
            Screen.Playbook -> state.copy(screen = state.playbookReturn ?: Screen.Library, playbookReturn = null)
            Screen.Setup -> state.copy(screen = Screen.Library)
            Screen.Library -> state
        }
    }
}
