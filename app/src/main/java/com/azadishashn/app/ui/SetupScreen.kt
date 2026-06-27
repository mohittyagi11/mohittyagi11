package com.azadishashn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.azadishashn.app.ui.components.AppLogo
import com.azadishashn.app.ui.components.FlowBackground
import com.azadishashn.app.ui.components.IconActionButton
import com.azadishashn.app.ui.components.PrimaryCta
import com.azadishashn.app.ui.components.SectionCard
import com.azadishashn.app.ui.theme.Dim

@Composable
fun SetupScreen(vm: GameViewModel) {
    var name by remember { mutableStateOf("") }
    var firstId by remember { mutableStateOf<Int?>(null) }
    val players = vm.state.players
    val effectiveFirst = firstId?.takeIf { id -> players.any { it.id == id } }
        ?: players.firstOrNull()?.id

    Box(Modifier.fillMaxSize()) {
        FlowBackground(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dim.screenH, vertical = Dim.screenV),
        ) {
            // Hero
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(size = 60.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "Azadi Shashn",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "Argue your ideology, win the table.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            SourceBanner(vm)
            Spacer(Modifier.height(16.dp))

            Text("Players", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (players.size >= 2) {
                Text(
                    "Select who plays first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Add a player") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { vm.addPlayer(name); name = "" },
                    enabled = name.isNotBlank(),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Add") }
            }

            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dim.itemGap),
            ) {
                items(players, key = { it.id }) { p ->
                    SectionCard {
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
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                            }
                            IconActionButton(
                                Icons.Filled.Close,
                                contentDescription = "Remove ${p.name}",
                                onClick = { vm.removePlayer(p.id) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            PrimaryCta(
                text = if (players.size < 2) "Add at least 2 players"
                else "Start — ${players.firstOrNull { it.id == effectiveFirst }?.name} plays first",
                onClick = { effectiveFirst?.let { vm.startGame(it) } },
                enabled = players.size >= 2,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = vm::openSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Settings")
                }
                TextButton(onClick = vm::openTransfer) {
                    Icon(Icons.Filled.ImportExport, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export / Import")
                }
            }
        }
    }
}

@Composable
private fun SourceBanner(vm: GameViewModel) {
    val live = vm.hasKey
    val accent = if (live) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (live) Icons.Filled.Bolt else Icons.Filled.WifiOff,
                contentDescription = null,
                tint = accent,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (live) "Live mode" else "Offline mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    if (live) "Claude is generating scenarios · ${vm.model}"
                    else "Using the bundled deck — add an API key for endless AI rounds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!live) {
                IconActionButton(
                    Icons.Filled.Settings,
                    contentDescription = "Open settings",
                    onClick = vm::openSettings,
                )
            }
        }
    }
}
