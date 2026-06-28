package com.azadishashn.app.ui.components.causal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.model.Ideologies
import com.azadishashn.app.model.PathForecast
import com.azadishashn.app.ui.theme.IdeologyTheme
import com.azadishashn.app.ui.theme.OverlineStyle

/**
 * The "compare the four paths" fan-out — the dilemma, then one path card per
 * ideology (stance → outcome → risk). When [chosen] is set, that path is lit and
 * the others recede: the chosen-one-on-each-logic view.
 */
@Composable
fun PathsFanOut(
    paths: List<PathForecast>,
    question: String,
    modifier: Modifier = Modifier,
    chosen: String? = null,
) {
    if (paths.isEmpty()) return
    val accent = chosen?.let { IdeologyTheme.of(it).brand } ?: MaterialTheme.colorScheme.primary
    val ordered = Ideologies.NAMES.mapNotNull { n -> paths.firstOrNull { it.ideology == n } }

    var shown by remember(paths, chosen) { mutableIntStateOf(0) }
    LaunchedEffect(paths, chosen) {
        shown = 0
        repeat(ordered.size + 1) {
            kotlinx.coroutines.delay(if (it == 0) 100 else 160)
            shown = it + 1
        }
    }

    BlueprintSurface(accent = accent, modifier = modifier) {
        ExhibitHeader("Exhibit · paths", "Compare the four paths", accent)
        Spacer(Modifier.height(12.dp))

        Reveal(visible = shown > 0) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Text("THE DILEMMA", style = OverlineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(question, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }

        ordered.forEachIndexed { i, p ->
            Reveal(visible = shown > i + 1) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("↳", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    PathCard(
                        name = p.ideology,
                        dot = IdeologyTheme.of(p.ideology).brand,
                        stance = p.stance,
                        outcome = p.outcome,
                        risk = p.risk,
                        chosen = chosen != null && p.ideology == chosen,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(260)) + slideInVertically(
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        ) { it / 3 },
    ) {
        Column { content() }
    }
}
