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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.azadishashn.app.data.GameSummary
import com.azadishashn.app.game.GameViewModel
import com.azadishashn.app.ui.components.Avatar
import com.azadishashn.app.ui.components.FlowBackground
import com.azadishashn.app.ui.components.IconActionButton
import com.azadishashn.app.ui.components.PrimaryCta
import com.azadishashn.app.ui.components.SectionCard
import com.azadishashn.app.ui.components.poster.PosterHero
import com.azadishashn.app.ui.theme.Dim

/** The games home: resume a saved session, or start a new one. */
@Composable
fun LibraryScreen(vm: GameViewModel) {
    var summaries by remember { mutableStateOf(vm.librarySummaries()) }
    fun reload() { summaries = vm.librarySummaries() }

    Box(Modifier.fillMaxSize()) {
        FlowBackground(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dim.screenH, vertical = Dim.screenV),
        ) {
            PosterHero(
                title = "Azadi Shashn",
                kicker = "The games library",
                subtitle = "Resume a session, or start a new one.",
            )
            Spacer(Modifier.height(Dim.sectionGap))

            PrimaryCta(text = "Start a new game", onClick = vm::newGame, gold = true)
            Spacer(Modifier.height(Dim.sectionGap))

            if (summaries.isEmpty()) {
                EmptyLibrary(Modifier.weight(1f))
            } else {
                Text(
                    "Your games",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Dim.tight))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dim.itemGap),
                ) {
                    items(summaries, key = { it.id }) { g ->
                        GameCard(
                            g = g,
                            onResume = { vm.resumeGame(g.id) },
                            onRename = { vm.renameGame(g.id, it); reload() },
                            onDelete = { vm.deleteGame(g.id); reload() },
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = vm::openSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Settings")
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "No games yet.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Start a new game to gather the table and play your first round.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GameCard(
    g: GameSummary,
    onResume: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    SectionCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    g.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (g.finished) {
                        Icon(
                            Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.height(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (g.leader.isNotBlank()) "Finished · ${g.leader} led" else "Finished",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Round ${g.round}" + if (g.leader.isNotBlank()) " · ${g.leader} ahead" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Box {
                IconActionButton(
                    Icons.Filled.MoreVert,
                    contentDescription = "Game options",
                    onClick = { menuOpen = true },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                        onClick = { menuOpen = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; confirmDelete = true },
                    )
                }
            }
        }

        if (g.playerNames.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                g.playerNames.take(6).forEachIndexed { i, n ->
                    Avatar(n, seed = i, size = 28.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onResume, shape = MaterialTheme.shapes.large) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Resume")
                }
            }
        }
    }

    if (renaming) {
        RenameDialog(initial = g.title, onConfirm = { onRename(it); renaming = false }, onDismiss = { renaming = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this game?") },
            text = { Text("“${g.title}” will be permanently removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename game") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
