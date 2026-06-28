package com.azadishashn.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.game.GameViewModel
import com.azadishashn.app.model.Ideologies
import com.azadishashn.app.ui.components.AzadiScaffold
import com.azadishashn.app.ui.components.Collapsible
import com.azadishashn.app.ui.components.IconActionButton
import com.azadishashn.app.ui.components.IdeologyBadge
import com.azadishashn.app.ui.components.LangChips
import com.azadishashn.app.ui.components.IdeologyDot
import com.azadishashn.app.ui.components.Particles
import com.azadishashn.app.ui.components.PrimaryCta
import com.azadishashn.app.ui.components.ReadAloudRow
import com.azadishashn.app.ui.components.SealMark
import com.azadishashn.app.ui.components.SectionCard
import com.azadishashn.app.ui.components.StatTile
import com.azadishashn.app.ui.components.StrengthMeter
import com.azadishashn.app.ui.components.causal.ConsequenceFlow
import com.azadishashn.app.ui.components.causal.PathsFanOut
import com.azadishashn.app.ui.components.poster.PosterBurst
import com.azadishashn.app.ui.theme.Dim
import com.azadishashn.app.ui.theme.Elev
import com.azadishashn.app.ui.theme.IdeologyTheme
import com.azadishashn.app.ui.theme.OverlineStyle

@Composable
fun ResultScreen(vm: GameViewModel) {
    val r = vm.state.lastResult ?: return
    val cardIdeo = r.cardIdeology.ifBlank { r.primary }

    // The verdict ceremony: the seal stamps in, the colour blooms, sparks fly.
    var shown by remember(r) { mutableStateOf(false) }
    LaunchedEffect(r) { shown = true }
    val pop by animateFloatAsState(
        targetValue = if (shown) 1f else 0.55f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pop",
    )
    val stamp by animateFloatAsState(
        targetValue = if (shown) 1f else 1.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "stamp",
    )
    val reveal = ((pop - 0.55f) / 0.45f).coerceIn(0f, 1f)
    val glow = IdeologyTheme.of(cardIdeo).brand

    AzadiScaffold(title = "Verdict") { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dim.screenH)
                .padding(bottom = Dim.sectionGap),
        ) {
            // Hero — the awarded card, lit by its ideology, with the seal stamp.
            Box {
                // Ceremony sunburst burst in the winning ideology's colour, behind the card.
                PosterBurst(
                    accent = glow,
                    intensity = 0.4f,
                    modifier = Modifier.matchParentSize().clip(RoundedCornerShape(24.dp)),
                )
                SectionCard(glow = glow, elevation = Elev.hero) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                r.playerName.uppercase(),
                                style = OverlineStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (r.diverted) "Card redirected" else "Card won!",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        SealMark(
                            size = 48.dp,
                            modifier = Modifier.graphicsLayer {
                                scaleX = stamp; scaleY = stamp
                                alpha = reveal
                                rotationZ = (1f - reveal) * -12f
                            },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(contentAlignment = Alignment.CenterStart) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = reveal }
                                .background(
                                    Brush.radialGradient(listOf(glow.copy(alpha = 0.45f), Color.Transparent)),
                                    MaterialTheme.shapes.large,
                                ),
                        )
                        IdeologyBadge(
                            cardIdeo,
                            count = 1,
                            modifier = Modifier.graphicsLayer {
                                scaleX = pop; scaleY = pop
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            },
                        )
                    }

                    if (r.diverted) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IdeologyDot(r.primary, size = 14.dp)
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "redirected to",
                                modifier = Modifier.padding(horizontal = 8.dp).height(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IdeologyDot(r.secondary, size = 14.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "didn't clear the bar for ${r.primary} — moved to ${r.secondary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    StrengthMeter(strength = r.strength, required = r.required, ideology = r.primary)
                }
                // Celebratory spark burst over the hero.
                Particles(color = glow, modifier = Modifier.matchParentSize())
            }

            // Analyst exhibits — the consequence map of the chosen stance, and
            // the four-paths recap with the chosen path lit.
            val primaryBrand = IdeologyTheme.of(r.primary).brand
            if (r.causalChain.isNotEmpty()) {
                Spacer(Modifier.height(Dim.sectionGap))
                Collapsible("Consequence map", Icons.Filled.AccountTree) {
                    ConsequenceFlow(
                        chain = r.causalChain,
                        historicalNote = r.historicalNote,
                        tradeoff = r.tradeoff,
                        accent = primaryBrand,
                    )
                }
            }
            vm.state.current?.let { round ->
                if (round.paths.isNotEmpty()) {
                    Spacer(Modifier.height(Dim.itemGap))
                    Collapsible("Compare the four paths", Icons.Filled.AccountTree) {
                        PathsFanOut(
                            paths = round.paths,
                            question = round.dilemma.question,
                            chosen = r.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Resources to take", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = r.primary,
                    value = "+2 · ${Ideologies.resourceOf(r.primary)}",
                    tint = IdeologyTheme.container(r.primary),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = r.secondary,
                    value = "+1 · ${Ideologies.resourceOf(r.secondary)}",
                    tint = IdeologyTheme.container(r.secondary),
                    modifier = Modifier.weight(1f),
                )
            }

            var displayLang by remember(r) { mutableStateOf(vm.language) }
            val entry = r.narration.firstOrNull { it.lang == displayLang }
            val translated = entry != null && displayLang != vm.language

            // The verdict spoken in English (built from the fields) as a fallback.
            val builtVerdict = run {
                val card = if (r.diverted) {
                    "earns one $cardIdeo card, redirected from ${r.primary} because they are already accumulating it"
                } else {
                    "earns one $cardIdeo card"
                }
                val strengthLine = if (r.strength >= 0) "Strength ${r.strength} of 10, needed ${r.required}. " else ""
                "${r.playerName} $card. $strengthLine" +
                    "Resources: ${r.primary} plus two ${Ideologies.resourceOf(r.primary)}, " +
                    "and ${r.secondary} plus one ${Ideologies.resourceOf(r.secondary)}. " +
                    "${r.reasoning}. ${r.historicalNote}"
            }
            val speakText: (String) -> String = { lang ->
                val e = r.narration.firstOrNull { it.lang == lang }
                val chosen = if (vm.prefersDevanagari(lang)) e?.speak else e?.text
                chosen?.takeIf { it.isNotBlank() } ?: e?.text?.takeIf { it.isNotBlank() } ?: builtVerdict
            }

            if (translated || r.reasoning.isNotBlank() || r.historicalNote.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                LangChips(vm.readLangs, displayLang, { displayLang = it }, Modifier.padding(bottom = Dim.tight))
                SectionCard {
                    val e = entry
                    if (translated && e != null) {
                        if (e.title.isNotBlank()) {
                            Text("Verdict", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                            Text(e.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(e.text, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        if (r.reasoning.isNotBlank()) {
                            Text("Why", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                            Text(r.reasoning, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (r.historicalNote.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("History", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                            Text(
                                r.historicalNote,
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            ReadAloudRow(
                langs = listOf(displayLang),
                isReading = vm.isReading,
                textFor = speakText,
                onPlay = { lang, text -> vm.readOut(text, lang) },
                onStop = vm::stopReadOut,
            )

            Spacer(Modifier.height(16.dp))
            PrimaryCta(text = "Next player's turn", onClick = vm::nextTurn)
            OutlinedButton(
                onClick = vm::openDashboard,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.BarChart, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dashboard")
            }
            OutlinedButton(
                onClick = vm::endGame,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("End game & see standings")
            }
        }
    }
}
