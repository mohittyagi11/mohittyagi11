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
import androidx.compose.material.icons.filled.Home
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
import com.azadishashn.app.ui.components.AnimatedCounter
import com.azadishashn.app.ui.components.Avatar
import com.azadishashn.app.ui.components.AzadiScaffold
import com.azadishashn.app.ui.components.IdeologyBadge
import com.azadishashn.app.ui.components.IdeologyDistributionBar
import com.azadishashn.app.ui.components.PrimaryCta
import com.azadishashn.app.ui.components.RankMedallion
import com.azadishashn.app.ui.components.SealMark
import com.azadishashn.app.ui.components.SectionCard
import com.azadishashn.app.ui.theme.Dim
import com.azadishashn.app.ui.theme.Gold
import com.azadishashn.app.ui.theme.NumberStyle

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
            // The living nation — where the table's choices have pushed the meters.
            if (s.dossier.isNotEmpty()) {
                SectionCard {
                    Text(
                        "THE NATION",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                    NationMeter("Economy", s.nation.economy)
                    NationMeter("Liberty", s.nation.liberty)
                    NationMeter("Stability", s.nation.stability)
                    NationMeter("Trust", s.nation.trust)
                }
                Spacer(Modifier.height(Dim.itemGap))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dim.itemGap),
            ) {
                // End of game: the honours list + the nation's closing chapter.
                if (!midGame && s.finished && s.dossier.isNotEmpty()) {
                    item {
                        SectionCard(glow = Gold) {
                            Text(
                                "HONOURS OF THE HOUSE",
                                style = MaterialTheme.typography.labelMedium,
                                color = Gold,
                            )
                            Spacer(Modifier.height(6.dp))
                            gameAwards(vm).forEach { (title, who) ->
                                Row(Modifier.padding(vertical = 3.dp)) {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        who,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        val ep = s.epilogue
                        if (ep != null) {
                            SectionCard {
                                Text(
                                    ep.title.ifBlank { "EPILOGUE" }.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(ep.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        } else if (vm.hasKey) {
                            OutlinedButton(
                                onClick = vm::generateEpilogue,
                                enabled = !s.loading,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (s.loading) "Writing the closing chapter…" else "Write the nation's epilogue")
                            }
                        }
                    }
                }

                itemsIndexed(ranked) { index, player ->
                    val leader = index == 0 && player.total > 0
                    SectionCard(
                        glow = if (leader) Gold else null,
                        accent = if (leader) Gold else null,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RankMedallion(index + 1)
                            Spacer(Modifier.width(10.dp))
                            Avatar(player.name, seed = player.id, size = 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        player.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (leader) {
                                        Spacer(Modifier.width(6.dp))
                                        SealMark(size = 18.dp)
                                    }
                                }
                            }
                            if (s.approval.containsKey(player.id)) {
                                Text(
                                    "${vm.approvalOf(player.id)}% ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            AnimatedCounter(
                                value = player.total,
                                style = NumberStyle,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                if (player.total == 1) " card" else " cards",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
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
                onClick = vm::openLibrary,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Back to library")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** One slim nation meter row: label, 0..100 bar, value. */
@Composable
private fun NationMeter(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        androidx.compose.material3.LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier.weight(1f).height(6.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("$value", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/** The end-of-game honours, computed from the public record and the polls. */
private fun gameAwards(vm: GameViewModel): List<Pair<String, String>> {
    val s = vm.state
    val byPlayer = s.dossier.groupBy { it.playerName }
    val awards = mutableListOf<Pair<String, String>>()
    byPlayer.maxByOrNull { (_, e) -> e.maxOf { it.strength } }?.let { (name, e) ->
        awards += "Sharpest Tongue (strength ${e.maxOf { it.strength }}/10)" to name
    }
    byPlayer.mapValues { (_, e) -> e.map { it.ideology }.distinct().size }
        .filterValues { it >= 3 }
        .maxByOrNull { it.value }?.let { (name, n) ->
            awards += "The Flip-Flopper ($n ideologies served)" to name
        }
    byPlayer.mapValues { (_, e) -> e.groupingBy { it.ideology }.eachCount().maxByOrNull { it.value } }
        .forEach { (name, top) ->
            if (top != null && top.value >= 3) awards += "Voice of the ${top.key}s (${top.value} verdicts)" to name
        }
    s.players.maxByOrNull { vm.approvalOf(it.id) }?.let {
        if (s.approval.isNotEmpty()) awards += "People's Favourite (${vm.approvalOf(it.id)}%)" to it.name
    }
    s.players.mapNotNull { p ->
        val support = s.blocSupport[p.id]?.values?.sum() ?: return@mapNotNull null
        p.name to support
    }.maxByOrNull { it.second }?.let { (name, n) ->
        if (n > 0) awards += "Coalition Builder (+$n bloc support)" to name
    }
    return awards
}
