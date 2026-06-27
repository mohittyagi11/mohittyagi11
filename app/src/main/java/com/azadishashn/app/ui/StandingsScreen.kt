package com.azadishashn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.game.GameViewModel
import com.azadishashn.app.model.Ideologies
import com.azadishashn.app.ui.components.AzadiScaffold
import com.azadishashn.app.ui.components.IdeologyBadge
import com.azadishashn.app.ui.components.IdeologyDistributionBar
import com.azadishashn.app.ui.components.PrimaryCta
import com.azadishashn.app.ui.components.RankMedallion
import com.azadishashn.app.ui.components.SectionCard
import com.azadishashn.app.ui.theme.Dim

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StandingsScreen(vm: GameViewModel) {
    val s = vm.state
    val midGame = s.dashboardReturn != null
    val ranked = s.players.sortedByDescending { it.total }

    AzadiScaffold(
        title = if (midGame) "Dashboard" else "Final standings",
        subtitle = if (midGame) "Round ${s.round} · next up: ${s.activePlayer?.name ?: ""}" else "Ideology cards per player",
        onBack = if (midGame) vm::leaveDashboard else null,
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = Dim.screenH),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dim.itemGap),
            ) {
                itemsIndexed(ranked) { index, player ->
                    SectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RankMedallion(index + 1)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                player.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${player.total}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                if (player.total == 1) " card" else " cards",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        IdeologyDistributionBar(player.counts)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Ideologies.NAMES.forEach { name ->
                                val c = player.counts[name] ?: 0
                                if (c > 0) IdeologyBadge(name, count = c, showLabel = false)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (midGame) {
                PrimaryCta(text = "Resume game", onClick = vm::leaveDashboard)
            }
            if (s.players.isNotEmpty()) {
                OutlinedButton(
                    onClick = vm::openEdit,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit game state")
                }
            }
            OutlinedButton(
                onClick = vm::openTransfer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.ImportExport, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export / Import game")
            }
            OutlinedButton(
                onClick = vm::newGame,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New game (keep players)")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
