package com.azadishashn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.azadishashn.app.data.SettingsStore
import com.azadishashn.app.game.GameViewModel

@Composable
fun SettingsScreen(vm: GameViewModel) {
    var key by remember { mutableStateOf(vm.apiKey) }
    var model by remember { mutableStateOf(vm.model) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))

        Text("Anthropic API key", fontWeight = FontWeight.Bold)
        Text(
            "Stored only on this device. Leave blank to play offline with the bundled deck.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("sk-ant-...") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )

        Spacer(Modifier.height(20.dp))
        Text("Model", fontWeight = FontWeight.Bold)
        SettingsStore.MODELS.forEach { (label, id) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = model == id, onClick = { model = id })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = model == id, onClick = { model = id })
                Text(label, modifier = Modifier.padding(start = 4.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                vm.saveSettings(key, model)
                vm.closeSettings()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
        TextButton(onClick = vm::closeSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
