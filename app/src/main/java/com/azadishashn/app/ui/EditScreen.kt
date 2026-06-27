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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.game.GameViewModel
import com.azadishashn.app.model.Ideologies

@Composable
fun EditScreen(vm: GameViewModel) {
    val players = vm.state.players

    var starterId by remember {
        mutableStateOf(vm.state.starterId ?: vm.state.activePlayer?.id ?: players.firstOrNull()?.id)
    }
    // key = "$playerId#$ideology" -> count
    val counts = remember {
        mutableStateMapOf<String, Int>().apply {
            players.forEach { p ->
                Ideologies.NAMES.forEach { ideo -> put("${p.id}#$ideo", p.counts[ideo] ?: 0) }
            }
        }
    }

    // Derived from total cards (one card per completed turn) + who started.
    val n = players.size
    val totalCards = players.sumOf { p -> Ideologies.NAMES.sumOf { counts["${p.id}#$it"] ?: 0 } }
    val starterIdx = players.indexOfFirst { it.id == starterId }.coerceAtLeast(0)
    val round = if (n > 0) totalCards / n + 1 else 1
    val turnInRound = if (n > 0) totalCards % n + 1 else 1
    val activeIdx = if (n > 0) (starterIdx + totalCards) % n else 0
    val activeName = players.getOrNull(activeIdx)?.name ?: ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            "Edit game state",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Set who started and each player's ideology-card counts. The round and " +
                "whose turn it is are worked out from the totals.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))

        // Live derived summary
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "Round $round · turn $turnInRound of ${n.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("$activeName answers next  ★", style = MaterialTheme.typography.bodyMedium)
                Text("Total cards on the table: $totalCards", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Who started the game?", fontWeight = FontWeight.Bold)
        players.forEach { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = starterId == p.id, onClick = { starterId = p.id })
                Text(p.name, modifier = Modifier.padding(start = 4.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Ideology cards per player", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        players.forEachIndexed { index, p ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        p.name + if (index == activeIdx) "   ★ answering" else "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Ideologies.ALL.forEach { ideo ->
                        val keyName = "${p.id}#${ideo.name}"
                        val value = counts[keyName] ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${ideo.name} · ${ideo.resource}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Stepper(
                                value = value,
                                onDec = { if (value > 0) counts[keyName] = value - 1 },
                                onInc = { counts[keyName] = value + 1 },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val byPlayer = players.associate { p ->
                    p.id to Ideologies.NAMES.associateWith { ideo -> counts["${p.id}#$ideo"] ?: 0 }
                }
                vm.applyEdit(starterId, byPlayer)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apply & resume") }
        TextButton(onClick = vm::cancelEdit, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
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
