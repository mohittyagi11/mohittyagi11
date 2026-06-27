package com.azadishashn.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.game.GameViewModel

/**
 * Export / Import the whole game as JSON: copy or share a snapshot to take a
 * backup, then paste it back later to restore and keep simulating changes.
 */
@Composable
fun TransferScreen(vm: GameViewModel) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // Snapshot taken once when the screen opens.
    val exported = remember { vm.exportState() }
    var importText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            "Export / Import",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Take a copy of the whole game (players, cards, round, current question) " +
                "so you can back it up or restore it later while we tune the rules.",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(16.dp))
        Text("This game", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            OutlinedTextField(
                value = exported,
                onValueChange = {},
                readOnly = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 260.dp)
                    .padding(8.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(exported))
                    status = "Copied to clipboard."
                },
                modifier = Modifier.weight(1f),
            ) { Text("📋  Copy") }
            Spacer(Modifier.height(0.dp))
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TITLE, "Azadi Shashn save")
                        putExtra(Intent.EXTRA_TEXT, exported)
                    }
                    context.startActivity(Intent.createChooser(send, "Share game"))
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            ) { Text("↗  Share") }
        }

        Spacer(Modifier.height(20.dp))
        Text("Restore a game", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Paste an exported game below, then load it. This replaces the current game.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = importText,
            onValueChange = { importText = it; status = null },
            label = { Text("Paste game JSON") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 220.dp),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    clipboard.getText()?.text?.let { importText = it }
                    status = null
                },
                modifier = Modifier.weight(1f),
            ) { Text("📥  Paste") }
            Spacer(Modifier.height(0.dp))
            Button(
                onClick = {
                    status = if (vm.importState(importText)) {
                        "Loaded." // state replaced — this screen will be swapped out
                    } else {
                        "Couldn't read that — check it's a complete exported game."
                    }
                },
                enabled = importText.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            ) { Text("Load game") }
        }

        status?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        }

        Spacer(Modifier.height(20.dp))
        TextButton(onClick = vm::closeTransfer, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}
