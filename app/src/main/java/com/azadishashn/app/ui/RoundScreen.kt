package com.azadishashn.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.azadishashn.app.game.GameViewModel
import com.azadishashn.app.model.OptionCard
import com.azadishashn.app.ui.components.Avatar
import com.azadishashn.app.ui.components.AzadiScaffold
import com.azadishashn.app.ui.components.GeneratingView
import com.azadishashn.app.ui.components.IconActionButton
import com.azadishashn.app.ui.components.IdeologyBadge
import com.azadishashn.app.ui.components.IdeologyChip
import com.azadishashn.app.ui.components.PrimaryCta
import com.azadishashn.app.ui.components.ScenarioStory
import com.azadishashn.app.ui.components.TurnProgress
import com.azadishashn.app.ui.theme.Dim
import com.azadishashn.app.ui.theme.IdeologyTheme

private enum class RoundPhase { QUESTION, ANSWER }

@Composable
fun RoundScreen(vm: GameViewModel) {
    val s = vm.state
    val subtitle = when {
        s.current != null -> "${s.questioner?.name} asks → ${s.activePlayer?.name} answers"
        else -> "${s.questioner?.name} → ${s.activePlayer?.name}"
    }
    AzadiScaffold(
        title = "Round ${s.round}",
        subtitle = subtitle,
        actions = {
            IconActionButton(Icons.Filled.BarChart, "Dashboard", vm::openDashboard)
            IconActionButton(Icons.Filled.Settings, "Settings", vm::openSettings)
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = Dim.screenH),
        ) {
            when {
                // RoundBody overlays the loader itself while twisting/judging, so
                // the player's typed answer and phase survive a failed judge.
                s.current != null -> RoundBody(vm)
                s.loading -> GeneratingView(s.loadingKind, vm.context)
                s.error != null -> ErrorBlock(vm)
                vm.hasKey -> ThemePicker(vm)
                else -> GeneratingView(s.loadingKind, vm.context)
            }
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
        Text(
            "Pick a theme — or a few — then Generate. Skip the picks to roll a random one.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dim.sectionGap))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dim.tight),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            s.availableThemes.forEach { theme ->
                FilterChip(
                    selected = theme in s.selectedThemes,
                    onClick = { vm.toggleTheme(theme) },
                    label = { Text(theme) },
                )
            }
        }
        Spacer(Modifier.height(Dim.sectionGap))
        OutlinedButton(
            onClick = vm::refreshThemes,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.height(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Refresh themes")
        }
        Spacer(Modifier.height(Dim.itemGap))
        PrimaryCta(
            text = if (s.selectedThemes.isEmpty()) "Surprise me — generate" else "Generate question",
            onClick = vm::generate,
        )
        Spacer(Modifier.height(Dim.sectionGap))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoundBody(vm: GameViewModel) {
    val s = vm.state
    val round = s.current!!
    var phase by remember(round) { mutableStateOf(RoundPhase.QUESTION) }
    var argument by remember(round) { mutableStateOf("") }
    var voiceHint by remember(round) { mutableStateOf<String?>(null) }

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

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Roles + turn progress: who asks whom, and where we are in the round.
        run {
            val n = s.players.size
            val starterIdx = s.players.indexOfFirst { it.id == s.starterId }.coerceAtLeast(0)
            val turnInRound = if (n > 0) ((s.activeIndex - starterIdx + n) % n) + 1 else 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                s.questioner?.let { Avatar(it.name, seed = it.id, size = 28.dp) }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "asks",
                    modifier = Modifier.padding(horizontal = 6.dp).height(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.activePlayer?.let { Avatar(it.name, seed = it.id, size = 28.dp) }
                Spacer(Modifier.weight(1f))
                TurnProgress(total = n, current = turnInRound)
            }
        }
        Spacer(Modifier.height(Dim.tight))
        Text(
            if (s.usingOffline) "Offline deck" else "Live · ${vm.model}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dim.tight))

        ScenarioStory(
            round = round,
            isReading = vm.isReading,
            onNarrate = {
                val dim = round.scenario.dimension.takeIf { it.isNotBlank() }?.let { "$it. " } ?: ""
                vm.readOut("$dim${round.scenario.title}. ${round.scenario.situation}  ${round.dilemma.question}")
            },
            onStop = vm::stopReadOut,
        )

        when (phase) {
            RoundPhase.QUESTION -> {
                Spacer(Modifier.height(Dim.sectionGap))
                Text(
                    "${s.questioner?.name}: read this out to ${s.activePlayer?.name}. " +
                        "Twist it to make the easy answer costly, or change it for a fresh scenario.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Dim.itemGap))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dim.tight)) {
                    if (vm.hasKey) {
                        OutlinedButton(
                            onClick = vm::twist,
                            enabled = s.twistsUsedThisTurn < 2,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Twist (${2 - s.twistsUsedThisTurn})")
                        }
                    }
                    OutlinedButton(
                        onClick = vm::changeQuestion,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Change")
                    }
                }
                s.error?.let { ErrorLine(it) }

                Spacer(Modifier.height(Dim.sectionGap))
                PrimaryCta(
                    text = "Pass to ${s.activePlayer?.name} to answer",
                    onClick = { phase = RoundPhase.ANSWER },
                )
                Spacer(Modifier.height(Dim.sectionGap))
            }

            RoundPhase.ANSWER -> {
                Spacer(Modifier.height(Dim.sectionGap))
                Text(
                    "${s.activePlayer?.name}: answer in your own words. " +
                        "Claude awards +2 to the ideology your answer most embodies and +1 to the next.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Dim.itemGap))

                Button(
                    onClick = { startVoice() },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Speak your answer")
                }
                voiceHint?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = argument,
                    onValueChange = { argument = it },
                    label = { Text("…or type / edit") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    minLines = 2,
                )

                if (!vm.hasKey) {
                    Spacer(Modifier.height(Dim.itemGap))
                    Text(
                        "Offline — tag your MAIN ideology (+2):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Dim.tight))
                    round.options.forEach { option ->
                        OptionRow(option, selected = s.championedOptionId == option.id) {
                            vm.champion(option.id)
                        }
                        Spacer(Modifier.height(Dim.tight))
                    }
                    Text(
                        "…and a secondary lean (+1):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Dim.tight))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dim.tight)) {
                        round.options.filter { it.id != s.championedOptionId }.forEach { option ->
                            IdeologyChip(
                                name = option.ideology,
                                selected = s.secondaryOptionId == option.id,
                                onClick = { vm.championSecondary(option.id) },
                            )
                        }
                    }
                }

                s.error?.let { ErrorLine(it) }

                Spacer(Modifier.height(Dim.itemGap))
                Text(
                    "Real-world note: ${round.dilemma.realWorldNote}",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(Dim.sectionGap))
                PrimaryCta(
                    text = if (vm.hasKey) "Resolve — Claude decides" else "Resolve",
                    onClick = { vm.resolve(argument) },
                    enabled = if (vm.hasKey) argument.isNotBlank() else s.championedOptionId != null,
                )
                TextButton(
                    onClick = { phase = RoundPhase.QUESTION },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Re-read the question") }
                Spacer(Modifier.height(Dim.sectionGap))
            }
        }
    }

        if (s.loading) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                GeneratingView(s.loadingKind, vm.context)
            }
        }
    }
}

@Composable
private fun OptionRow(option: OptionCard, selected: Boolean, onClick: () -> Unit) {
    val v = IdeologyTheme.of(option.ideology)
    val container = if (selected) IdeologyTheme.container(option.ideology) else MaterialTheme.colorScheme.surfaceContainer
    val border = if (selected) BorderStroke(2.dp, v.brand) else null
    Card(
        onClick = onClick,
        border = border,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dim.cardPad)) {
            IdeologyBadge(option.ideology)
            Spacer(Modifier.height(Dim.tight))
            Text(option.label, style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}

@Composable
private fun ErrorLine(message: String) {
    Spacer(Modifier.height(Dim.tight))
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ErrorBlock(vm: GameViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Couldn't reach Claude", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(vm.state.error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Dim.itemGap))
        PrimaryCta(text = "Retry", onClick = vm::retryRound)
        OutlinedButton(
            onClick = vm::useOfflineRound,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Use a bundled round instead") }
        TextButton(onClick = vm::openSettings, modifier = Modifier.fillMaxWidth()) { Text("Check API key") }
    }
}
