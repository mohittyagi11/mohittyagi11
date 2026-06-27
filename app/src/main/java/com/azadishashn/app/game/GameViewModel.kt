package com.azadishashn.app.game

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azadishashn.app.data.OfflineContent
import com.azadishashn.app.data.SettingsStore
import com.azadishashn.app.model.Adjudication
import com.azadishashn.app.model.AwardResult
import com.azadishashn.app.model.Player
import com.azadishashn.app.model.RoundData
import com.azadishashn.app.net.ClaudeClient
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class Screen { Setup, Settings, Round, Vote, Result, Standings }

data class GameState(
    val screen: Screen = Screen.Setup,
    val players: List<Player> = emptyList(),
    val activeIndex: Int = 0,
    val round: Int = 1,
    val current: RoundData? = null,
    val championedOptionId: String? = null,
    val adjudication: Adjudication? = null,
    val lastResult: AwardResult? = null,
    val twistsUsedThisTurn: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val usingOffline: Boolean = false,
) {
    val activePlayer: Player? get() = players.getOrNull(activeIndex)
    val voterCount: Int get() = (players.size - 1).coerceAtLeast(1)
}

/**
 * Owns all game logic. The award rule combines three inputs:
 *  - group consensus (which ideology the table voted the argument embodied),
 *  - the group baseline (how loaded the table already is on that ideology),
 *  - how many cards of it the active player already holds (anti-farming).
 * Claude is a neutral adjudicator/generator; the table's vote decides the award.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)

    var state by mutableStateOf(GameState())
        private set

    // Settings surfaced to the UI ------------------------------------------
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

    fun startGame() {
        if (state.players.size < 2) return
        state = state.copy(screen = Screen.Round, activeIndex = 0, round = 1)
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
        // Return to setup if a game hasn't started, otherwise back to the round.
        val back = if (state.current == null) Screen.Setup else Screen.Round
        state = state.copy(screen = back)
    }

    // -- A turn --------------------------------------------------------------

    fun beginTurn() {
        state = state.copy(
            current = null,
            championedOptionId = null,
            adjudication = null,
            lastResult = null,
            twistsUsedThisTurn = 0,
            error = null,
            usingOffline = false,
            screen = Screen.Round,
        )
        loadRound()
    }

    /** Generate via Claude when a key is present; otherwise fall back to the bundled deck. */
    private fun loadRound() {
        if (!settings.hasKey) {
            useOfflineRound()
            return
        }
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = runCatching {
                ClaudeClient(settings.apiKey, settings.model).generateRound(seenTitles.takeLast(8))
            }
            result.onSuccess { round ->
                seenTitles += round.scenario.title
                state = state.copy(current = round, loading = false, usingOffline = false)
            }.onFailure { e ->
                state = state.copy(loading = false, error = e.message ?: "Generation failed")
            }
        }
    }

    fun useOfflineRound() {
        val round = OfflineContent.ROUNDS[Random.nextInt(OfflineContent.ROUNDS.size)]
        state = state.copy(current = round, loading = false, error = null, usingOffline = true)
    }

    fun retryRound() = loadRound()

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
                    adjudication = null,
                    twistsUsedThisTurn = state.twistsUsedThisTurn + 1,
                    loading = false,
                )
            }.onFailure { e ->
                state = state.copy(loading = false, error = e.message ?: "Twist failed")
            }
        }
    }

    fun askClaude(argument: String) {
        val round = state.current ?: return
        if (!settings.hasKey || argument.isBlank()) return
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching {
                ClaudeClient(settings.apiKey, settings.model).adjudicate(round, argument)
            }.onSuccess { adj ->
                state = state.copy(adjudication = adj, loading = false)
            }.onFailure { e ->
                state = state.copy(loading = false, error = e.message ?: "Adjudication failed")
            }
        }
    }

    fun goToVote() {
        if (state.current == null) return
        state = state.copy(screen = Screen.Vote)
    }

    /**
     * Resolve the turn. [votes] = ideology -> how many players thought the argument
     * embodied it. [convinced] = how many were persuaded enough to award the card.
     */
    fun submitVote(votes: Map<String, Int>, convinced: Int) {
        val active = state.activePlayer ?: return
        val consensus = votes.entries
            .filter { it.value > 0 }
            .maxByOrNull { it.value }?.key

        if (consensus == null) {
            state = state.copy(
                lastResult = AwardResult(
                    playerName = active.name,
                    ideology = "—",
                    awarded = false,
                    convinced = convinced,
                    required = 0,
                    bravery = false,
                    explanation = "No ideology got a vote — no card awarded.",
                ),
                screen = Screen.Result,
            )
            return
        }

        val held = active.counts[consensus] ?: 0
        // Group baseline: the table's average holding of the consensus ideology.
        val tableAvg = state.players.map { it.counts[consensus] ?: 0 }.average()
        // Defending an ideology you're light on (vs. the table) is "brave" — easier to earn.
        val bravery = held < tableAvg
        // Anti-farming: the more of this ideology you already hold, the more voters you
        // must convince. Bravery shaves one off. Bounded to [1, voterCount].
        val required = (1 + held - if (bravery) 1 else 0)
            .coerceIn(1, state.voterCount)
        val awarded = convinced >= required

        val explanation = buildString {
            append("Table voted this argument as $consensus. ")
            append("${active.name} holds $held $consensus card(s); table average is ")
            append("%.1f. ".format(tableAvg))
            if (bravery) append("Brave minority stance — threshold eased. ")
            append("Needed $required of ${state.voterCount} convinced; got $convinced. ")
            append(if (awarded) "Card AWARDED." else "Not enough — no card.")
        }

        val updatedPlayers = if (awarded) {
            state.players.map { p ->
                if (p.id == active.id) {
                    p.copy(counts = p.counts + (consensus to held + 1))
                } else p
            }
        } else state.players

        state = state.copy(
            players = updatedPlayers,
            lastResult = AwardResult(
                playerName = active.name,
                ideology = consensus,
                awarded = awarded,
                convinced = convinced,
                required = required,
                bravery = bravery,
                explanation = explanation,
            ),
            screen = Screen.Result,
        )
    }

    fun nextTurn() {
        val nextIndex = (state.activeIndex + 1) % state.players.size
        val nextRound = if (nextIndex == 0) state.round + 1 else state.round
        state = state.copy(activeIndex = nextIndex, round = nextRound)
        beginTurn()
    }

    fun endGame() {
        state = state.copy(screen = Screen.Standings)
    }

    fun newGame() {
        seenTitles.clear()
        state = GameState(players = state.players.map { it.copy(counts = emptyMap()) })
    }
}
