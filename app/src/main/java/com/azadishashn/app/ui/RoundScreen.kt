package com.azadishashn.app.ui

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
import com.azadishashn.app.model.OptionCard

@Composable
fun RoundScreen(vm: GameViewModel) {
    val s = vm.state
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        TurnHeader(vm)
        Spacer(Modifier.height(12.dp))

        when {
            s.loading && s.current == null -> LoadingBlock("Summoning a government in crisis…")
            s.error != null && s.current == null -> ErrorBlock(vm)
            s.current != null -> RoundBody(vm)
        }
    }
}

@Composable
private fun TurnHeader(vm: GameViewModel) {
    val s = vm.state
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "Round ${s.round} · ${s.activePlayer?.name ?: ""}'s turn",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (s.usingOffline) "Offline deck" else "Live · ${vm.model}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        TextButton(onClick = vm::openSettings) { Text("⚙") }
    }
}

@Composable
private fun RoundBody(vm: GameViewModel) {
    val s = vm.state
    val round = s.current!!
    var argument by remember(round) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
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

        Spacer(Modifier.height(12.dp))
        Text("Champion one position, then argue it to the table:", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))

        round.options.forEach { option ->
            OptionRow(option, selected = s.championedOptionId == option.id) { vm.champion(option.id) }
            Spacer(Modifier.height(6.dp))
        }

        if (vm.hasKey) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = vm::twist,
                    enabled = s.twistsUsedThisTurn < 2 && !s.loading,
                    modifier = Modifier.weight(1f),
                ) { Text("Twist (${2 - s.twistsUsedThisTurn} left)") }
            }
            Spacer(Modifier.height(8.dp))

            Text("Optional — type the gist of your argument for a neutral read:", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = argument,
                onValueChange = { argument = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedButton(
                onClick = { vm.askClaude(argument) },
                enabled = argument.isNotBlank() && !s.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Ask Claude to weigh in") }
        }

        if (s.loading) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                Spacer(Modifier.height(0.dp))
                Text("  Thinking…", style = MaterialTheme.typography.bodySmall)
            }
        }

        s.adjudication?.let { adj ->
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Claude reads this as: ${adj.matchedIdeology}", fontWeight = FontWeight.Bold)
                    Text(adj.reasoning, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text("History: ${adj.historicalOutcome}", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                }
            }
        }

        s.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Real-world note: ${round.dilemma.realWorldNote}",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
        )

        Spacer(Modifier.height(16.dp))
        Button(onClick = vm::goToVote, modifier = Modifier.fillMaxWidth()) {
            Text("Debate done — go to the vote")
        }
        Spacer(Modifier.height(24.dp))
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
            Text(option.ideology, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            Text(option.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(option.summary, style = MaterialTheme.typography.bodySmall)
        }
    }
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
