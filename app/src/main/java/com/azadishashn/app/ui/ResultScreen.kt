package com.azadishashn.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.game.GameViewModel
import com.azadishashn.app.model.Ideologies

@Composable
fun ResultScreen(vm: GameViewModel) {
    val r = vm.state.lastResult ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(
            "Resources awarded",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    r.playerName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${r.primary}  +2   (${Ideologies.resourceOf(r.primary)})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${r.secondary}  +1   (${Ideologies.resourceOf(r.secondary)})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (r.explanation.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(r.explanation, style = MaterialTheme.typography.bodyMedium)
                }
                if (r.reasoning.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Why: ${r.reasoning}", style = MaterialTheme.typography.bodySmall)
                }
                if (r.historicalNote.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "History: ${r.historicalNote}",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(onClick = vm::nextTurn, modifier = Modifier.fillMaxWidth()) {
            Text("Next player's turn")
        }
        OutlinedButton(onClick = vm::openDashboard, modifier = Modifier.fillMaxWidth()) {
            Text("📊  Dashboard")
        }
        OutlinedButton(onClick = vm::endGame, modifier = Modifier.fillMaxWidth()) {
            Text("End game & see standings")
        }
    }
}
