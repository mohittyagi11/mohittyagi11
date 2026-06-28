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

/** Numerals for the anonymous path labels (and the reveal tag). */
private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI")

/**
 * The "compare the four paths" fan-out — the dilemma, then one card per path
 * (stance → outcome → risk).
 *
 * At QUESTION time ([reveal] = false) the paths are anonymous: neutral numerals
 * ("Path I…IV") and grey dots, shuffled by a stable per-scenario seed so neither
 * the name, the colour, nor the position gives the ideology away — the player
 * has to read the dilemma and choose a framing themselves.
 *
 * On the VERDICT recap ([reveal] = true, [chosen] set) the same shuffled order
 * is revealed with ideology names + brand colours, the chosen path lit and the
 * rest receded, and each card keeps its numeral so the player can connect their
 * pre-decision read to the answer.
 */
@Composable
fun PathsFanOut(
    paths: List<PathForecast>,
    question: String,
    modifier: Modifier = Modifier,
    chosen: String? = null,
    reveal: Boolean = chosen != null,
) {
    if (paths.isEmpty()) return
    val accent = if (reveal) {
        chosen?.let { IdeologyTheme.of(it).brand } ?: MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary
    }

    // Deterministic shuffle by a per-scenario seed so card POSITION never leaks
    // the ideology. String.hashCode() is stable, so the order is reproducible —
    // and identical between the question view and the verdict reveal.
    val ordered = remember(paths, question) {
        val present = Ideologies.NAMES.mapNotNull { n -> paths.firstOrNull { it.ideology == n } }
        present.sortedBy { ("$question|${it.ideology}").hashCode() }
    }

    var shown by remember(paths, reveal) { mutableIntStateOf(0) }
    LaunchedEffect(paths, reveal) {
        shown = 0
        repeat(ordered.size + 1) {
            kotlinx.coroutines.delay(if (it == 0) 100 else 160)
            shown = it + 1
        }
    }

    val title = if (reveal) "Compare the four paths" else "Four paths — you decide"
    val grey = MaterialTheme.colorScheme.onSurfaceVariant

    BlueprintSurface(accent = accent, modifier = modifier) {
        ExhibitHeader("Exhibit · paths", title, accent)
        Spacer(Modifier.height(12.dp))

        Reveal(visible = shown > 0) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Text("THE DILEMMA", style = OverlineStyle, color = grey)
                Spacer(Modifier.height(4.dp))
                Text(question, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }

        ordered.forEachIndexed { i, p ->
            val numeral = "Path ${ROMAN.getOrElse(i) { "${i + 1}" }}"
            val isChosen = reveal && chosen != null && p.ideology == chosen
            Reveal(visible = shown > i + 1) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("↳", color = grey, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    PathCard(
                        name = if (reveal) p.ideology else numeral,
                        dot = if (reveal) IdeologyTheme.of(p.ideology).brand else grey,
                        stance = p.stance,
                        outcome = p.outcome,
                        risk = p.risk,
                        chosen = isChosen,
                        tag = if (reveal) numeral else "",
                        dimmed = reveal && chosen != null && !isChosen,
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
