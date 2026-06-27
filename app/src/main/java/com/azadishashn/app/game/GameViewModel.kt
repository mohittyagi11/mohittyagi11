package com.azadishashn.app.game

import android.app.Application
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
enum class Screen { Setup, Settings, Round, Result, Standings, Edit }

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
    val error: String? = null,
    val usingOffline: Boolean = false,
    // Theme picking (questioner, before the question is generated).
    val availableThemes: List<String> = emptyList(),
    val selectedThemes: Set<String> = emptySet(),
    val lastThemes: List<String> = emptyList(),
    /** When set, Standings is being viewed mid-game as a dashboard; resume returns here. */
    val dashboardReturn: Screen? = null,
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
    private val speaker = Speaker(app)

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

    private val seenTitles = mutableListOf<String>()
    private var nextPlayerId = 0
    private val twistLimit = 2

    init {
        // Restore an in-progress game so app updates / restarts don't kill it.
        val saved = store.load()
        if (saved != null && saved.players.isNotEmpty()) {
            nextPlayerId = (saved.players.maxOfOrNull { it.id } ?: -1) + 1
            var restored = saved.copy(loading = false, error = null)
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

    private fun loadRound(themes: List<String>) {
        if (!settings.hasKey) {
            useOfflineRound()
            return
        }
        state = state.copy(loading = true, error = null, lastThemes = themes)
        viewModelScope.launch {
            runCatching {
                ClaudeClient(settings.apiKey, settings.model)
                    .generateRound(themes, settings.context, seenTitles.takeLast(8))
            }.onSuccess { round ->
                seenTitles += round.scenario.title
                state = state.copy(current = round, loading = false, usingOffline = false)
            }.onFailure { e ->
                state = state.copy(loading = false, error = e.message ?: "Generation failed")
            }
        }
    }

    fun useOfflineRound() {
        val round = OfflineContent.ROUNDS[(0 until OfflineContent.ROUNDS.size).random()]
        state = state.copy(current = round, loading = false, error = null, usingOffline = true)
    }

    fun retryRound() {
        loadRound(state.lastThemes.ifEmpty { listOf(Themes.random()) })
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
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching {
                ClaudeClient(settings.apiKey, settings.model).twistRound(round)
            }.onSuccess { twisted ->
                state = state.copy(
                    current = twisted,
                    championedOptionId = null,
                    twistsUsedThisTurn = state.twistsUsedThisTurn + 1,
                    loading = false,
                )
            }.onFailure { e ->
                state = state.copy(loading = false, error = e.message ?: "Twist failed")
            }
        }
    }

    // -- Resolve (Claude judges; rivals can't stall) -------------------------

    fun resolve(argument: String) {
        val active = state.activePlayer ?: return
        val round = state.current ?: return

        if (settings.hasKey && argument.isNotBlank()) {
            // Player argued; Claude names the dominant and secondary ideologies.
            state = state.copy(loading = true, error = null)
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
                    state = state.copy(loading = false, error = e.message ?: "Judging failed")
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
     * Resources are always +2 [primary] / +1 [secondary]. The dominant CARD is earned only
     * if [strength] (1..10) clears a baseline that SHIFTS with standing: at the table average
     * for that ideology you need 5/10, and each card you hold above (below) the average raises
     * (lowers) the bar. [strength] < 0 means offline (a simple anti-hoard rule instead).
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
        val cardAwarded = if (strength < 0) {
            held < (state.players.minOf { it.counts[primary] ?: 0 }) + 2
        } else {
            strength >= required
        }

        val explanation = buildString {
            if (strength >= 0) append("Strength $strength/10 vs needed $required. ")
            append(
                if (cardAwarded) "Earned 1 $primary card."
                else "No $primary card — not strong enough for your standing.",
            )
            append("  Resources: $primary +2, $secondary +1.")
        }

        val updated = state.players.map { p ->
            if (p.id == active.id) {
                val c = p.counts.toMutableMap()
                if (cardAwarded) c[primary] = (c[primary] ?: 0) + 1
                p.copy(counts = c)
            } else p
        }
        state = state.copy(
            players = updated,
            loading = false,
            lastResult = AwardResult(
                playerName = active.name,
                primary = primary,
                secondary = secondary,
                strength = strength,
                required = required,
                cardAwarded = cardAwarded,
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
        seenTitles.clear()
        state = GameState(players = state.players.map { it.copy(counts = emptyMap()) })
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
            Screen.Setup -> state
        }
    }
}
