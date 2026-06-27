package com.azadishashn.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
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
import com.azadishashn.app.ui.components.AzadiScaffold
import com.azadishashn.app.ui.components.PrimaryCta
import com.azadishashn.app.ui.components.SectionCard
import com.azadishashn.app.ui.theme.Dim

@Composable
fun SettingsScreen(vm: GameViewModel) {
    var key by remember { mutableStateOf(vm.apiKey) }
    var model by remember { mutableStateOf(vm.model) }
    var context by remember { mutableStateOf(vm.context) }
    var voice by remember { mutableStateOf(vm.voiceLang) }

    AzadiScaffold(title = "Settings", onBack = vm::closeSettings) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dim.screenH)
                .padding(bottom = Dim.sectionGap),
        ) {
            SectionCard {
                Text("Anthropic API key", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Stored only on this device. Leave blank to play offline with the bundled deck.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("sk-ant-...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text("Context / country", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Scenarios are framed here — the world's dilemmas, set in this context.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = context,
                    onValueChange = { context = it },
                    label = { Text("e.g. India") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text("Voice input language", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                SettingsStore.VOICE_LANGS.forEach { (label, tag) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = voice == tag, onClick = { voice = tag })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = voice == tag, onClick = { voice = tag })
                        Text(label, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text("Model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
            }

            Spacer(Modifier.height(20.dp))
            PrimaryCta(
                text = "Save",
                onClick = {
                    vm.saveSettings(key, model, context, voice)
                    vm.closeSettings()
                },
            )
            TextButton(onClick = vm::closeSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}
