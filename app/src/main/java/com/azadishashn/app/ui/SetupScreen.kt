package com.azadishashn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.game.GameViewModel

@Composable
fun SetupScreen(vm: GameViewModel) {
    var name by remember { mutableStateOf("") }
    var firstId by remember { mutableStateOf<Int?>(null) }
    val players = vm.state.players
    // Default to the first added player; stays valid if players are removed.
    val effectiveFirst = firstId?.takeIf { id -> players.any { it.id == id } }
        ?: players.firstOrNull()?.id

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(
            "Azadi Shashn",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Freedom & governance — argue your ideology, win the table.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))

        SourceBanner(vm)
        Spacer(Modifier.height(16.dp))

        Text("Players", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (players.size >= 2) {
            Text(
                "Select who plays first (◉).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Add a player") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(0.dp))
            Button(
                onClick = { vm.addPlayer(name); name = "" },
                enabled = name.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Add") }
        }

        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(players, key = { it.id }) { p ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = effectiveFirst == p.id,
                                onClick = { firstId = p.id },
                            )
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = { vm.removePlayer(p.id) }) {
                            Text("✕", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }

        Button(
            onClick = { effectiveFirst?.let { vm.startGame(it) } },
            enabled = players.size >= 2,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val starter = players.firstOrNull { it.id == effectiveFirst }?.name
            Text(
                if (players.size < 2) "Add at least 2 players"
                else "Start — $starter plays first",
            )
        }

        TextButton(
            onClick = vm::openSettings,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Settings (API key & model)") }

        TextButton(
            onClick = vm::openTransfer,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("⇪  Export / Import a saved game") }
    }
}

@Composable
private fun SourceBanner(vm: GameViewModel) {
    val color = if (vm.hasKey) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
    Card(colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.padding(14.dp)) {
            if (vm.hasKey) {
                Text(
                    "Live mode — Claude is generating scenarios",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiary,
                )
                Text(
                    "Model: ${vm.model}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiary,
                )
            } else {
                Text("Offline mode — using the bundled deck", fontWeight = FontWeight.Bold)
                Text(
                    "Add an Anthropic API key in Settings for endless AI-generated rounds.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = vm::openSettings, modifier = Modifier.padding(top = 6.dp)) {
                    Text("Open Settings")
                }
            }
        }
    }
}
