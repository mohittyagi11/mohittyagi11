package com.azadishashn.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
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
            s.current != null -> RoundBody(vm)
            s.loading -> LoadingBlock("Generating the scenario…")
            s.error != null -> ErrorBlock(vm)
            vm.hasKey -> ThemePicker(vm)
            else -> LoadingBlock("Loading…")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePicker(vm: GameViewModel) {
    val s = vm.state
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
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
                    "${s.questioner?.name}: set the theme for ${s.activePlayer?.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row {
                TextButton(onClick = vm::openDashboard) { Text("📊") }
                TextButton(onClick = vm::openSettings) { Text("⚙") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Pick a theme — or a few — then Generate. Skip the picks to roll a random one. " +
                "Refresh for new themes.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            s.availableThemes.forEach { theme ->
                FilterChip(
                    selected = theme in s.selectedThemes,
                    onClick = { vm.toggleTheme(theme) },
                    label = { Text(theme) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = vm::refreshThemes, modifier = Modifier.fillMaxWidth()) {
            Text("🔀  Refresh themes")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = vm::generate, modifier = Modifier.fillMaxWidth()) {
            Text(if (s.selectedThemes.isEmpty()) "🎲  Surprise me — generate" else "Generate question")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
            // Hindi / Hinglish by default (configurable in Settings); Claude understands it.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, vm.voiceLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, vm.voiceLang)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your answer")
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
                    "${s.activePlayer?.name}: answer the question in your own words. " +
                        "Claude awards +2 to the ideology your answer most embodies and +1 to the next.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))

                // Voice-first argument input — no options/categories shown.
                Button(onClick = { startVoice() }, modifier = Modifier.fillMaxWidth()) {
                    Text("🎤  Speak your answer")
                }
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

                if (!vm.hasKey) {
                    // Offline only: no AI to classify, so tag your ideologies yourself.
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Offline — tag your MAIN ideology (+2):",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    round.options.forEach { option ->
                        OptionRow(option, selected = s.championedOptionId == option.id) {
                            vm.champion(option.id)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        "…and a secondary lean (+1):",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        round.options.filter { it.id != s.championedOptionId }.forEach { option ->
                            FilterChip(
                                selected = s.secondaryOptionId == option.id,
                                onClick = { vm.championSecondary(option.id) },
                                label = { Text(option.ideology) },
                            )
                        }
                    }
                }

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
                    enabled = !s.loading &&
                        if (vm.hasKey) argument.isNotBlank() else s.championedOptionId != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (vm.hasKey) "Resolve — Claude decides" else "Resolve") }
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
            if (round.scenario.dimension.isNotBlank()) {
                Text(
                    round.scenario.dimension.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
            }
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
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                val dim = round.scenario.dimension.takeIf { it.isNotBlank() }?.let { "$it. " } ?: ""
                vm.readOut(
                    "$dim${round.scenario.title}. ${round.scenario.situation}  ${round.dilemma.question}",
                )
            }) { Text("🔊  Read aloud") }
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
