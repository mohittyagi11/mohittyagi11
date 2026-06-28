package com.azadishashn.app.ui.components.causal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.model.CausalStep
import com.azadishashn.app.ui.theme.OverlineStyle

/**
 * The verdict's "consequence map" — a numbered, top-down causal chain that draws
 * itself in. Connectors carry the mechanism ("→ because …"); the chain ends at a
 * historical echo and a tradeoff callout. Rendered in the chosen ideology's [accent].
 */
@Composable
fun ConsequenceFlow(
    chain: List<CausalStep>,
    historicalNote: String,
    tradeoff: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (chain.isEmpty()) return
    var shown by remember(chain) { mutableIntStateOf(0) }
    LaunchedEffect(chain) {
        shown = 0
        repeat(chain.size + 2) {
            kotlinx.coroutines.delay(if (it == 0) 100 else 230)
            shown = it + 1
        }
    }

    BlueprintSurface(accent = accent, modifier = modifier) {
        ExhibitHeader("Exhibit · causal", "Consequence map", accent)
        Spacer(Modifier.height(12.dp))

        chain.forEachIndexed { i, step ->
            if (i > 0 && step.mechanism.isNotBlank()) {
                Reveal(visible = shown > i) {
                    Connector(step.mechanism)
                }
            }
            Reveal(visible = shown > i) {
                NodeCard(index = i + 1, step = step, accent = accent)
            }
        }

        if (historicalNote.isNotBlank()) {
            Reveal(visible = shown > chain.size) {
                Connector("then, over time")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Text("HISTORICAL ECHO", style = OverlineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(historicalNote, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                }
            }
        }

        if (tradeoff.isNotBlank()) {
            Reveal(visible = shown > chain.size + 1) {
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(accent.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Text("THE COST OF POWER", style = OverlineStyle, color = accent)
                    Spacer(Modifier.height(4.dp))
                    Text(tradeoff, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun Connector(mechanism: String) {
    Row(Modifier.fillMaxWidth().padding(start = 9.dp, top = 4.dp, bottom = 4.dp)) {
        Text("↓", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Text(
            mechanism,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280)) + slideInVertically(
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        ) { it / 3 },
    ) {
        Column { content() }
    }
}
