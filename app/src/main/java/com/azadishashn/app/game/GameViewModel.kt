package com.azadishashn.app.game

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azadishashn.app.data.OfflineContent
import com.azadishashn.app.data.SettingsStore
import com.azadishashn.app.model.AwardResult
import com.azadishashn.app.model.Ideologies
import com.azadishashn.app.model.Player
import com.azadishashn.app.model.RoundData
import com.azadishashn.app.model.Themes
import com.azadishashn.app.model.Verdict
import com.azadishashn.app.net.ClaudeClient
import kotlinx.coroutines.launch

enum class Screen { Setup, Settings, Round, Result, Standings }

data class GameState(
    val screen: Screen = Screen.Setup,
    val players: List<Player> = emptyList(),
    val activeIndex: Int = 0,
    val round: Int = 1,
    val current: RoundData? = null,
    val championedOptionId: String? = null,
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

    var state by mutableStateOf(GameState())
        private set

    val apiKey: String get() = settings.apiKey
    val model: String get() = settings.model
    val hasKey: Boolean get() = settings.hasKey

    private val seenTitles = mutableListOf<String>()
    private var nextPlayerId = 0
    private val twistLimit = 2

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
        state = state.copy(screen = Screen.Round, activeIndex = idx, round = 1)
        beginTurn()
    }

    // -- Settings ------------------------------------------------------------

    fun openSettings() {
        state = state.copy(screen = Screen.Settings)
    }

    fun saveSettings(apiKey: String, model: String) {
        settings.apiKey = apiKey
        settings.model = model
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
                ClaudeClient(settings.apiKey, settings.model).generateRound(themes, seenTitles.takeLast(8))
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
        val championed = round.options.firstOrNull { it.id == state.championedOptionId }?.ideology ?: return

        if (settings.hasKey && argument.isNotBlank()) {
            state = state.copy(loading = true, error = null)
            viewModelScope.launch {
                runCatching {
                    ClaudeClient(settings.apiKey, settings.model).judge(round, championed, argument)
                }.onSuccess { verdict ->
                    applyJudgedAward(active, verdict)
                }.onFailure { e ->
                    state = state.copy(loading = false, error = e.message ?: "Judging failed")
                }
            }
        } else {
            applyOfflineAward(active, championed)
        }
    }

    /** Bar the argument must clear for [ideology]: rises with holdings, eased for a minority stance. */
    private fun barFor(player: Player, ideology: String): Pair<Int, Boolean> {
        val held = player.counts[ideology] ?: 0
        val tableAvg = state.players.map { it.counts[ideology] ?: 0 }.average()
        val bravery = held < tableAvg
        val bar = (1 + held - if (bravery) 1 else 0).coerceIn(1, 3)
        return bar to bravery
    }

    private fun applyJudgedAward(active: Player, verdict: Verdict) {
        val ideology = if (verdict.matchedIdeology in Ideologies.NAMES) {
            verdict.matchedIdeology
        } else {
            state.current?.options?.firstOrNull { it.id == state.championedOptionId }?.ideology
                ?: Ideologies.NAMES.first()
        }
        val score = verdict.score.coerceIn(0, 3)
        val (bar, bravery) = barFor(active, ideology)
        val awarded = score >= bar
        val explanation = buildString {
            append("Claude judged this a genuine $ideology case at strength $score/3. ")
            append(
                if (bravery) "A light/brave stance, so the bar eased to $bar. "
                else "Bar was $bar — it rises as you collect more $ideology. ",
            )
            append(if (awarded) "Point AWARDED." else "Below the bar — no point this time.")
        }
        commit(active, ideology, awarded, score, bar, verdict.reasoning, verdict.historicalOutcome, explanation)
    }

    private fun applyOfflineAward(active: Player, ideology: String) {
        val held = active.counts[ideology] ?: 0
        val tableMin = state.players.minOf { it.counts[ideology] ?: 0 }
        val awarded = held < tableMin + 2 // block obvious hoarding without a judge
        val explanation = if (awarded) {
            "Offline rule: you argued the $ideology line — point awarded."
        } else {
            "Offline rule: you're hoarding $ideology (you hold $held, table low is $tableMin). " +
                "Earn a different ideology first."
        }
        commit(active, ideology, awarded, -1, 0, "", "", explanation)
    }

    private fun commit(
        active: Player,
        ideology: String,
        awarded: Boolean,
        score: Int,
        bar: Int,
        reasoning: String,
        history: String,
        explanation: String,
    ) {
        val updated = if (awarded) {
            state.players.map { p ->
                if (p.id == active.id) {
                    p.copy(counts = p.counts + (ideology to (p.counts[ideology] ?: 0) + 1))
                } else p
            }
        } else state.players

        state = state.copy(
            players = updated,
            loading = false,
            lastResult = AwardResult(
                playerName = active.name,
                ideology = ideology,
                awarded = awarded,
                score = score,
                bar = bar,
                reasoning = reasoning,
                historicalNote = history,
                explanation = explanation,
            ),
            screen = Screen.Result,
        )
    }

    // -- Flow ----------------------------------------------------------------

    fun nextTurn() {
        val nextIndex = (state.activeIndex + 1) % state.players.size
        val nextRound = if (nextIndex == 0) state.round + 1 else state.round
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
            Screen.Setup -> state
        }
    }
}
