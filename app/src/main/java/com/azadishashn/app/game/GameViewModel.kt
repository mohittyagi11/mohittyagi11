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
import com.azadishashn.app.data.GameStore
import com.azadishashn.app.data.OfflineContent
import com.azadishashn.app.data.SettingsStore
import com.azadishashn.app.model.AwardResult
import com.azadishashn.app.model.Ideologies
import com.azadishashn.app.model.Player
import com.azadishashn.app.model.RoundData
import com.azadishashn.app.model.Themes
import com.azadishashn.app.model.Verdict
import com.azadishashn.app.net.ClaudeClient
import com.azadishashn.app.tts.Speaker
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class Screen { Setup, Settings, Round, Result, Standings, Edit, Transfer }

@Serializable
data class GameState(
    val screen: Screen = Screen.Setup,
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

    fun readOut(text: String) = speaker.speak(text)
    fun stopReadOut() = speaker.stop()

    override fun onCleared() {
        speaker.shutdown()
        super.onCleared()
    }

    var state by mutableStateOf(GameState())
        private set

    val apiKey: String get() = settings.apiKey
    val model: String get() = settings.model
    val context: String get() = settings.context
    val voiceLang: String get() = settings.voiceLang
    val hasKey: Boolean get() = settings.hasKey

    private var nextPlayerId = 0
    private val twistLimit = 2

    init {
        // Restore an in-progress game so app updates / restarts don't kill it.
        val saved = store.load()
        if (saved != null && saved.players.isNotEmpty()) {
            nextPlayerId = (saved.players.maxOfOrNull { it.id } ?: -1) + 1
            var restored = saved.copy(loading = false, loadingKind = null, error = null)
            // If we were killed at the theme-pick stage, repopulate the chips.
            if (restored.screen == Screen.Round && restored.current == null &&
                settings.hasKey && restored.availableThemes.isEmpty()
            ) {
                restored = restored.copy(availableThemes = Themes.sample(12))
            }
            state = restored
        }
        // Persist on every state change.
        viewModelScope.launch {
            snapshotFlow { state }.collect { store.save(it) }
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
        state = state.copy(screen = Screen.Round, activeIndex = idx, round = 1, starterId = firstPlayerId)
        beginTurn()
    }

    // -- Settings ------------------------------------------------------------

    fun openSettings() {
        state = state.copy(screen = Screen.Settings)
    }

    fun saveSettings(apiKey: String, model: String, context: String, voiceLang: String) {
        settings.apiKey = apiKey
        settings.model = model
        settings.context = context
        settings.voiceLang = voiceLang
    }

    fun closeSettings() {
        val back = if (state.players.isEmpty()) Screen.Setup else Screen.Round
        state = state.copy(screen = back)
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
        )
        if (settings.hasKey) {
            // Show the questioner a theme picker; generation waits for [generate].
            state = base.copy(availableThemes = Themes.sample(12))
        } else {
            // Offline deck ignores themes — go straight to a bundled round.
            state = base.copy(availableThemes = emptyList())
            useOfflineRound()
        }
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
        state = state.copy(loading = true, loadingKind = "generate", error = null, lastThemes = themes)
        val avoid = state.seenTitles.takeLast(20)
        viewModelScope.launch {
            runCatching {
                ClaudeClient(settings.apiKey, settings.model)
                    .generateRound(themes, settings.context, avoid)
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
                    )
                }
            }.onFailure { e ->
                state = state.copy(loading = false, loadingKind = null, error = e.message ?: "Generation failed")
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
                ClaudeClient(settings.apiKey, settings.model).twistRound(round)
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

    // -- Resolve (Claude judges; rivals can't stall) -------------------------

    fun resolve(argument: String) {
        val active = state.activePlayer ?: return
        val round = state.current ?: return

        if (settings.hasKey && argument.isNotBlank()) {
            // Player argued; Claude names the dominant and secondary ideologies.
            state = state.copy(loading = true, loadingKind = "judge", error = null)
            viewModelScope.launch {
                runCatching {
                    ClaudeClient(settings.apiKey, settings.model).judge(round, argument)
                }.onSuccess { v ->
                    val primary = v.primaryIdeology.takeIf { it in Ideologies.NAMES }
                        ?: Ideologies.NAMES.first()
                    val secondary = v.secondaryIdeology.takeIf { it in Ideologies.NAMES && it != primary }
                        ?: Ideologies.NAMES.first { it != primary }
                    award(active, primary, secondary, v.strength.coerceIn(1, 10), v.reasoning, v.historicalOutcome)
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
            award(active, primary, secondary, -1, "", "")
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
    private fun award(
        active: Player,
        primary: String,
        secondary: String,
        strength: Int,
        reasoning: String,
        history: String,
    ) {
        val held = active.counts[primary] ?: 0
        val tableAvg = state.players.map { it.counts[primary] ?: 0 }.average()
        val required = (5.0 + (held - tableAvg)).roundToInt().coerceIn(1, 10)
        // A card always lands; the baseline only decides whether it stays on [primary]
        // (keep stacking) or is redirected to [secondary] (you're hoarding [primary]).
        val keepPrimary = if (strength < 0) {
            held < (state.players.minOf { it.counts[primary] ?: 0 }) + 2
        } else {
            strength >= required
        }
        val cardIdeology = if (keepPrimary) primary else secondary
        val diverted = !keepPrimary

        val explanation = buildString {
            if (strength >= 0) {
                if (keepPrimary) {
                    append("Strength $strength/10 cleared the bar (needed $required). Earned 1 $primary card. ")
                } else {
                    append("Strength $strength/10 — needed $required to keep stacking $primary (you hold $held). Card goes to $secondary instead. ")
                }
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
        state = state.copy(
            players = updated,
            loading = false,
            loadingKind = null,
            lastResult = AwardResult(
                playerName = active.name,
                primary = primary,
                secondary = secondary,
                strength = strength,
                required = required,
                cardAwarded = true,
                cardIdeology = cardIdeology,
                diverted = diverted,
                reasoning = reasoning,
                historicalNote = history,
                explanation = explanation,
            ),
            screen = Screen.Result,
        )
    }

    // -- Flow ----------------------------------------------------------------

    fun nextTurn() {
        val n = state.players.size
        val nextIndex = (state.activeIndex + 1) % n
        val starterIdx = state.players.indexOfFirst { it.id == state.starterId }.coerceAtLeast(0)
        // A round completes when the turn returns to whoever started.
        val nextRound = if (nextIndex == starterIdx) state.round + 1 else state.round
        state = state.copy(activeIndex = nextIndex, round = nextRound)
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
        state = state.copy(screen = Screen.Standings, dashboardReturn = null)
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

    fun newGame() {
        state = GameState(players = state.players.map { it.copy(counts = emptyMap()) })
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
            loading = false,
            error = null,
        )
        return store.export(snapshot)
    }

    /** Load a pasted/exported game, replacing the current one. Returns false if it won't parse. */
    fun importState(raw: String): Boolean {
        val loaded = store.import(raw)?.takeIf { it.players.isNotEmpty() } ?: return false
        nextPlayerId = (loaded.players.maxOfOrNull { it.id } ?: -1) + 1
        state = loaded.copy(loading = false, loadingKind = null, error = null, transferReturn = null, dashboardReturn = null)
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
            Screen.Round -> state.copy(screen = Screen.Setup)
            Screen.Result -> state
            Screen.Standings -> state.copy(screen = state.dashboardReturn ?: Screen.Setup, dashboardReturn = null)
            Screen.Edit -> state.copy(screen = Screen.Standings)
            Screen.Transfer -> state.copy(screen = state.transferReturn ?: Screen.Setup, transferReturn = null)
            Screen.Setup -> state
        }
    }
}
