package com.azadishashn.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.data.Lessons
import com.azadishashn.app.game.GameViewModel
import com.azadishashn.app.ui.components.AzadiScaffold
import com.azadishashn.app.ui.components.Collapsible
import com.azadishashn.app.ui.components.SectionCard
import com.azadishashn.app.ui.components.StrengthMeter
import com.azadishashn.app.ui.theme.Dim

/**
 * The full reference for every house rule the app layers on top of standard
 * SHASN. Each rule also teaches itself in play (a one-time coach card the first
 * time it fires) — this screen guarantees every branch is learnable even if it
 * never happens to occur at your table.
 */
@Composable
fun PlaybookScreen(vm: GameViewModel) {
    AzadiScaffold(
        title = "The Playbook",
        subtitle = "House rules, taught by scenario",
        onBack = vm::closePlaybook,
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dim.screenH)
                .padding(bottom = Dim.sectionGap),
        ) {
            Text(
                "None of this is in the SHASN box — it's the app's living-politics " +
                    "layer. Every rule below also introduces itself in play, the " +
                    "first time it fires. Cards are still the score; everything " +
                    "here feeds them, the resources you take, or the polls.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Dim.itemGap))

            // A worked verdict — the bar, live, on the real meter component.
            SectionCard {
                Text(
                    "A WORKED VERDICT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.height(8.dp))
                StrengthMeter(strength = 6, required = 7, ideology = "Capitalist")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Strength 6 against a bar of 7: the tick is the bar. The fill " +
                        "stopped short — the card diverts to the secondary ideology. " +
                        "Clear the tick and it stays on the line you argued.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Dim.itemGap))

            Lessons.ALL.forEach { lesson ->
                Collapsible(lesson.title, Icons.AutoMirrored.Filled.MenuBook, initiallyExpanded = false) {
                    SectionCard(modifier = Modifier.padding(bottom = 6.dp)) {
                        Text(
                            lesson.scenario,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                        )
                        Spacer(Modifier.height(6.dp))
                        lesson.branches.forEach { b ->
                            Column(Modifier.padding(vertical = 3.dp)) {
                                Text(
                                    b.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    b.outcome,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (lesson.why.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Why it's here: ${lesson.why}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Dim.sectionGap))
            var replayed by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = {
                    vm.resetLessons()
                    replayed = true
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (replayed) "Coach cards re-armed ✓" else "Replay the coach cards") }
            Text(
                "Each rule will introduce itself again, the first time it fires.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
