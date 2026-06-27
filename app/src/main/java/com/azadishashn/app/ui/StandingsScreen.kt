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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.game.GameViewModel
import com.azadishashn.app.model.Ideologies

@Composable
fun StandingsScreen(vm: GameViewModel) {
    val s = vm.state
    val midGame = s.dashboardReturn != null
    val ranked = s.players.sortedByDescending { it.total }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(
            if (midGame) "Dashboard" else "Final standings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (midGame) {
            Text(
                "Round ${s.round} · next up: ${s.activePlayer?.name ?: ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Text(
            "Ideology points (political capital) per player",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(ranked) { index, player ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${index + 1}. ${player.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${player.total} pt",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        // Show every ideology so it's clear who holds what (and who holds none).
                        val line = Ideologies.NAMES.joinToString("   ") { name ->
                            "$name ${player.counts[name] ?: 0}"
                        }
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (midGame) {
            Button(onClick = vm::leaveDashboard, modifier = Modifier.fillMaxWidth()) {
                Text("Resume game")
            }
            OutlinedButton(onClick = vm::newGame, modifier = Modifier.fillMaxWidth()) {
                Text("New game (keep players)")
            }
        } else {
            Button(onClick = vm::newGame, modifier = Modifier.fillMaxWidth()) {
                Text("New game (keep players)")
            }
        }
    }
}
