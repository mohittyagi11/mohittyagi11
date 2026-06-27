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
import androidx.compose.runtime.mutableIntStateOf
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

    var round by remember { mutableIntStateOf(vm.state.round) }
    var activeId by remember { mutableStateOf(vm.state.activePlayer?.id ?: players.firstOrNull()?.id) }
    // key = "$playerId#$ideology" -> count
    val counts = remember {
        mutableStateMapOf<String, Int>().apply {
            players.forEach { p ->
                Ideologies.NAMES.forEach { ideo -> put("${p.id}#$ideo", p.counts[ideo] ?: 0) }
            }
        }
    }

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
            "Repair a broken game, then drop back into the current question.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))

        // Round
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Round", fontWeight = FontWeight.Bold)
            Stepper(round, onDec = { if (round > 1) round-- }, onInc = { round++ })
        }
        Spacer(Modifier.height(12.dp))

        Text("Players — ★ is who's answering; set each ideology's card count.", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        players.forEach { p ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = activeId == p.id, onClick = { activeId = p.id })
                        Text(
                            p.name + if (activeId == p.id) "  ★" else "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
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
                vm.applyEdit(round, activeId, byPlayer)
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
