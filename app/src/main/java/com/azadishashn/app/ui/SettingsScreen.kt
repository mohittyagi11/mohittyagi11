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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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
    var language by remember { mutableStateOf(vm.language) }
    var readLangs by remember { mutableStateOf(vm.readLangs.toSet()) }
    var era by remember { mutableStateOf(vm.era) }
    var partyLines by remember { mutableStateOf(vm.partyLines) }
    var fastJudge by remember { mutableStateOf(vm.fastJudge) }
    var scandals by remember { mutableStateOf(vm.scandalsOn) }

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
                Text("Language", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "On-screen scenarios and your spoken answers use this language.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsStore.LANGUAGES.forEach { (label, code) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = language == code, onClick = { language = code })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == code, onClick = { language = code })
                        Text(label, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text("Read aloud in", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Pick one or more languages for the narrator — each turn's read-aloud offers a button per language.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsStore.LANGUAGES.forEach { (label, code) ->
                    val checked = code in readLangs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                onValueChange = { on ->
                                    readLangs = if (on) {
                                        readLangs + code
                                    } else {
                                        (readLangs - code).let { if (it.isEmpty()) setOf(code) else it }
                                    }
                                },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
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

            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text("Era", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Pin scenarios to a period — its institutions, technology, and what was politically thinkable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsStore.ERAS.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = era == value, onClick = { era = value })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = era == value, onClick = { era = value })
                        Text(label, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text("Real-politics modes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                SettingsToggle(
                    "Party whip",
                    "Some turns, the whip hands the answerer sealed instructions — obey quietly (+3 poll), " +
                        "rebel magnificently (+5), or rebel weakly and pay (−4).",
                    partyLines,
                ) { partyLines = it }
                SettingsToggle(
                    "Scandals",
                    "Skeletons occasionally surface from a player's own record; face the press.",
                    scandals,
                ) { scandals = it }
                SettingsToggle(
                    "Fast judging",
                    "Verdicts use the fastest model — snappier rounds; scenarios keep the model above.",
                    fastJudge,
                ) { fastJudge = it }
            }

            Spacer(Modifier.height(20.dp))
            PrimaryCta(
                text = "Save",
                onClick = {
                    vm.saveSettings(
                        key, model, context, language, readLangs,
                        era = era, partyLines = partyLines, fastJudge = fastJudge, scandals = scandals,
                    )
                    vm.closeSettings()
                },
            )
            TextButton(onClick = vm::closeSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}

/** A labelled switch row with a one-line description. */
@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.padding(start = 8.dp))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = null)
    }
}
