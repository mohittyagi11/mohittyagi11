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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
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
import com.azadishashn.app.ui.components.AzadiScaffold
import com.azadishashn.app.ui.components.IdeologyDot
import com.azadishashn.app.ui.components.PrimaryCta
import com.azadishashn.app.ui.components.SectionCard
import com.azadishashn.app.ui.theme.Dim

@Composable
fun EditScreen(vm: GameViewModel) {
    val players = vm.state.players
    val n = players.size

    val starterIdxInit = players.indexOfFirst { it.id == vm.state.starterId }.coerceAtLeast(0)
    val turnInit = if (n > 0) ((vm.state.activeIndex - starterIdxInit + n) % n) + 1 else 1

    var starterId by remember {
        mutableStateOf(vm.state.starterId ?: vm.state.activePlayer?.id ?: players.firstOrNull()?.id)
    }
    var round by remember { mutableIntStateOf(vm.state.round) }
    var turnInRound by remember { mutableIntStateOf(turnInit) }
    val counts = remember {
        mutableStateMapOf<String, Int>().apply {
            players.forEach { p ->
                Ideologies.NAMES.forEach { ideo -> put("${p.id}#$ideo", p.counts[ideo] ?: 0) }
            }
        }
    }

    val starterIdx = players.indexOfFirst { it.id == starterId }.coerceAtLeast(0)
    val activeIdx = if (n > 0) (starterIdx + (turnInRound - 1).coerceIn(0, n - 1)) % n else 0
    val activeName = players.getOrNull(activeIdx)?.name ?: ""

    AzadiScaffold(title = "Edit game state", onBack = vm::cancelEdit) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dim.screenH)
                .padding(bottom = Dim.sectionGap),
        ) {
            Text(
                "Set the round, the turn, who started, and each player's cards. " +
                    "Whoever is up is worked out from the starter and the turn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    "Round $round · turn $turnInRound of ${n.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "$activeName answers next ★",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Round", fontWeight = FontWeight.Bold)
                    Stepper(round, onDec = { if (round > 1) round-- }, onInc = { round++ })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Turn in round", fontWeight = FontWeight.Bold)
                    Stepper(
                        turnInRound,
                        onDec = { if (turnInRound > 1) turnInRound-- },
                        onInc = { if (turnInRound < n.coerceAtLeast(1)) turnInRound++ },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard {
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
            }

            Spacer(Modifier.height(12.dp))
            Text("Ideology cards per player", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            players.forEachIndexed { index, p ->
                SectionCard {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IdeologyDot(ideo.name, size = 12.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${ideo.name} · ${ideo.resource}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Stepper(
                                value = value,
                                onDec = { if (value > 0) counts[keyName] = value - 1 },
                                onInc = { counts[keyName] = value + 1 },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            PrimaryCta(
                text = "Apply & resume",
                onClick = {
                    val byPlayer = players.associate { p ->
                        p.id to Ideologies.NAMES.associateWith { ideo -> counts["${p.id}#$ideo"] ?: 0 }
                    }
                    vm.applyEdit(round, turnInRound, starterId, byPlayer)
                },
            )
            TextButton(onClick = vm::cancelEdit, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, onDec: () -> Unit, onInc: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedIconButton(onClick = onDec) {
            Icon(Icons.Filled.Remove, contentDescription = "decrease")
        }
        Text(
            value.toString(),
            modifier = Modifier
                .width(40.dp)
                .padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        OutlinedIconButton(onClick = onInc) {
            Icon(Icons.Filled.Add, contentDescription = "increase")
        }
    }
}
