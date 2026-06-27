package com.azadishashn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.game.GameViewModel

@Composable
fun VoteScreen(vm: GameViewModel) {
    val s = vm.state
    val round = s.current ?: return
    val voterCount = s.voterCount

    // ideology -> vote tally
    val votes = remember(round) { mutableStateMapOf<String, Int>() }
    var convinced by remember(round) { mutableIntStateOf(0) }

    val totalVotes = votes.values.sum()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "The table votes",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "${s.activePlayer?.name} sits out. ${voterCount} player(s) vote on which ideology " +
                "the argument actually embodied.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))

        round.options.forEach { option ->
            val count = votes[option.ideology] ?: 0
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.ideology, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                        Text(option.label, style = MaterialTheme.typography.bodySmall)
                    }
                    Stepper(
                        value = count,
                        onDec = { if (count > 0) votes[option.ideology] = count - 1 },
                        onInc = { if (totalVotes < voterCount) votes[option.ideology] = count + 1 },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            "Votes cast: $totalVotes / $voterCount",
            style = MaterialTheme.typography.labelMedium,
        )

        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Persuaded?", fontWeight = FontWeight.Bold)
                    Text(
                        "How many voters were convinced enough to award the card.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Stepper(
                    value = convinced,
                    onDec = { if (convinced > 0) convinced-- },
                    onInc = { if (convinced < voterCount) convinced++ },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.submitVote(votes.toMap(), convinced) },
            enabled = totalVotes > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Resolve turn") }
    }
}

@Composable
private fun Stepper(value: Int, onDec: () -> Unit, onInc: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onDec) { Text("–") }
        Text(
            value.toString(),
            modifier = Modifier
                .width(36.dp)
                .padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        OutlinedButton(onClick = onInc) { Text("+") }
    }
}
