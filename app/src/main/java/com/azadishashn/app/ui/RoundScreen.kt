package com.azadishashn.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.game.GameViewModel
import com.azadishashn.app.model.Ideologies
import com.azadishashn.app.model.OptionCard

private enum class RoundPhase { QUESTION, ANSWER }

@Composable
fun RoundScreen(vm: GameViewModel) {
    val s = vm.state
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        when {
            s.loading && s.current == null -> LoadingBlock("Drawing an ideology card…")
            s.error != null && s.current == null -> ErrorBlock(vm)
            s.current != null -> RoundBody(vm)
        }
    }
}

@Composable
private fun RoundBody(vm: GameViewModel) {
    val s = vm.state
    val round = s.current!!
    // A twist swaps the round object, which resets the flow back to QUESTION.
    var phase by remember(round) { mutableStateOf(RoundPhase.QUESTION) }
    var argument by remember(round) { mutableStateOf("") }
    var voiceHint by remember(round) { mutableStateOf<String?>(null) }

    // Voice input via the system speech recognizer (no RECORD_AUDIO permission
    // needed — the system speech UI captures the mic). Spoken text fills the
    // argument field, which the player can still edit or type into directly.
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                argument = if (argument.isBlank()) spoken else "$argument $spoken"
            }
        }
    }
    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your argument")
        }
        try {
            voiceHint = null
            speechLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            voiceHint = "No voice-input app found — type your argument instead."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Roles header — who is asking whom.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Round ${s.round}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    "${s.questioner?.name} asks → ${s.activePlayer?.name} answers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (s.usingOffline) "Offline deck" else "Live · ${vm.model}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row {
                TextButton(onClick = vm::openDashboard) { Text("📊") }
                TextButton(onClick = vm::openSettings) { Text("⚙") }
            }
        }
        Spacer(Modifier.height(12.dp))

        ScenarioCard(vm)

        when (phase) {
            RoundPhase.QUESTION -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    "${s.questioner?.name}: read this out to ${s.activePlayer?.name}. " +
                        "You may twist the card to make the easy answer costly.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (vm.hasKey) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = vm::twist,
                        enabled = s.twistsUsedThisTurn < 2 && !s.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Twist the card (${2 - s.twistsUsedThisTurn} left)") }
                }
                if (s.loading) LoadingInline()
                s.error?.let { ErrorLine(it) }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { phase = RoundPhase.ANSWER },
                    enabled = !s.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Pass to ${s.activePlayer?.name} to answer") }
                Spacer(Modifier.height(24.dp))
            }

            RoundPhase.ANSWER -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    "${s.activePlayer?.name}: champion one position and make your case.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                round.options.forEach { option ->
                    OptionRow(option, selected = s.championedOptionId == option.id) {
                        vm.champion(option.id)
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Text(
                    if (vm.hasKey) {
                        "Make your case — speak it (or type) and Claude judges it:"
                    } else {
                        "Notes — speak or type (offline: you earn the position you championed unless hoarding):"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = { startVoice() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("🎤  Speak your argument") }
                voiceHint?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = argument,
                    onValueChange = { argument = it },
                    label = { Text("…or type / edit") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                if (s.loading) LoadingInline()
                s.error?.let { ErrorLine(it) }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Real-world note: ${round.dilemma.realWorldNote}",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                )

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.resolve(argument) },
                    enabled = s.championedOptionId != null &&
                        (!vm.hasKey || argument.isNotBlank()) && !s.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (vm.hasKey) "Resolve — Claude judges" else "Resolve") }
                TextButton(
                    onClick = { phase = RoundPhase.QUESTION },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Re-read the question") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ScenarioCard(vm: GameViewModel) {
    val round = vm.state.current!!
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(round.scenario.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "${round.scenario.setting} · ${round.scenario.era}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(8.dp))
            Text(round.scenario.situation, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text(round.dilemma.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OptionRow(option: OptionCard, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Card(
        onClick = onClick,
        border = border,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "${option.ideology} · ${Ideologies.resourceOf(option.ideology)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
            )
            // Only the stance — NOT the reasoning. The player must make the case
            // themselves; the option's rationale stays private to the judge.
            Text(option.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoadingInline() {
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.height(20.dp))
        Text("  Thinking…", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ErrorLine(message: String) {
    Spacer(Modifier.height(8.dp))
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun LoadingBlock(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorBlock(vm: GameViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Couldn't reach Claude", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(vm.state.error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Button(onClick = vm::retryRound, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
        OutlinedButton(onClick = vm::useOfflineRound, modifier = Modifier.fillMaxWidth()) {
            Text("Use a bundled round instead")
        }
        TextButton(onClick = vm::openSettings, modifier = Modifier.fillMaxWidth()) { Text("Check API key") }
    }
}
