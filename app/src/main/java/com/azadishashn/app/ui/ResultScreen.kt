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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.game.GameViewModel

@Composable
fun ResultScreen(vm: GameViewModel) {
    val result = vm.state.lastResult ?: return
    val container = if (result.awarded) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(
            if (result.awarded) "Card won!" else "No card this time",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = container)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "${result.playerName} → ${result.ideology}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (result.bravery) {
                    Text("Brave minority stance", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Convinced ${result.convinced} of ${result.required} needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(result.explanation, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.weight(1f))

        Button(onClick = vm::nextTurn, modifier = Modifier.fillMaxWidth()) {
            Text("Next player's turn")
        }
        OutlinedButton(onClick = vm::endGame, modifier = Modifier.fillMaxWidth()) {
            Text("End game & see standings")
        }
    }
}
