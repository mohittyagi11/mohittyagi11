package com.azadishashn.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azadishashn.app.data.Lessons
import com.azadishashn.app.game.GameViewModel
import com.azadishashn.app.model.Ideologies
import kotlinx.coroutines.delay
import com.azadishashn.app.ui.components.AnimatedCounter
import com.azadishashn.app.ui.components.AzadiScaffold
import com.azadishashn.app.ui.components.CoachMark
import com.azadishashn.app.ui.components.FrontPage
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

    // The verdict ceremony: a held beat, a haptic thump, the seal stamps in,
    // the colour blooms, sparks fly — then the morning-after press pack lands.
    var shown by remember(r) { mutableStateOf(false) }
    var press by remember(r) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(r) {
        delay(420)                                        // the held breath
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        shown = true                                      // the stamp
        delay(650)
        press = true                                      // the papers land
    }
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
    // First-encounter coach cards: at most 2 per verdict, so the morning-after
    // stack doesn't become a rules wall — the rest teach on a later verdict.
    val coachBudget = remember(r) { mutableIntStateOf(0) }

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
                    if (r.moodNote.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            r.moodNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // The bar math is always inspectable — award() already wrote
                    // the full plain-words explanation; this just unfolds it.
                    if (r.explanation.isNotBlank()) {
                        var why by remember(r) { mutableStateOf(false) }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (why) "WHY THIS VERDICT ▾" else "WHY THIS VERDICT ▸",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .clickable { why = !why }
                                .padding(vertical = 4.dp),
                        )
                        AnimatedVisibility(visible = why) {
                            Text(
                                r.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Celebratory spark burst over the hero.
                Particles(color = glow, modifier = Modifier.matchParentSize())
            }
            // The bar decided who kept the card — teach it on the first verdict.
            if (r.strength >= 0) {
                CoachMark(
                    Lessons.BAR, vm::hasSeenLesson, vm::markLessonSeen, coachBudget,
                    highlight = if (r.diverted) 1 else 0,
                )
            }

            // Ideologue milestone — the board build pays off: claim the power.
            if (r.milestone.isNotBlank()) {
                Spacer(Modifier.height(Dim.itemGap))
                SectionCard(glow = IdeologyTheme.of(r.cardIdeology.ifBlank { r.primary }).brand) {
                    Text(
                        "🏛 IDEOLOGUE MILESTONE",
                        style = MaterialTheme.typography.labelMedium,
                        color = IdeologyTheme.of(r.cardIdeology.ifBlank { r.primary }).brand,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        r.milestone,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                CoachMark(Lessons.MILESTONE, vm::hasSeenLesson, vm::markLessonSeen, coachBudget)
            }

            // The morning after — spin, the record, the blocs, the snap poll.
            val hasPress = r.headlines.isNotEmpty() || r.blocReactions.isNotEmpty() ||
                r.approvalAfter >= 0 || (r.consistency?.note?.isNotBlank() == true) ||
                r.whipOutcome.isNotBlank() || r.crisisOutcome.isNotBlank() ||
                r.newEndorsements.isNotEmpty()
            if (hasPress) {
                AnimatedVisibility(
                    visible = press,
                    enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 4 },
                ) {
                    Column {
                        Spacer(Modifier.height(Dim.sectionGap))
                        Text(
                            "THE MORNING AFTER",
                            style = OverlineStyle,
                            color = MaterialTheme.colorScheme.tertiary,
                        )

                        // THE PAPERS — the same answer, two spins, tossed on the
                        // breakfast table one after the other. The star of the show.
                        r.headlines.take(2).forEachIndexed { i, h ->
                            var landed by remember(r) { mutableStateOf(false) }
                            LaunchedEffect(press) {
                                if (press) { delay(120L + i * 300L); landed = true }
                            }
                            Spacer(Modifier.height(10.dp))
                            AnimatedVisibility(
                                visible = landed,
                                enter = fadeIn(tween(340)) + slideInVertically(tween(340)) { it / 3 },
                            ) {
                                FrontPage(
                                    outlet = h.outlet,
                                    slant = h.slant,
                                    headline = h.headline,
                                    tilt = if (i % 2 == 0) -1.4f else 1.2f,
                                )
                            }
                        }

                        // The rest of the night, compact: a grid of outcome chips
                        // that cascade in — tap any chip for its full story.
                        val chips = buildList {
                            if (r.whipOutcome.isNotBlank()) {
                                add(
                                    OutcomeChipData(
                                        label = "THE WHIP",
                                        line = when (r.whipOutcome) {
                                            "obeyed" -> "Obeyed · +${r.whipPollAdj} poll"
                                            "rebel" -> "Rebelled! +${r.whipPollAdj} poll"
                                            else -> "Strayed · ${r.whipPollAdj} poll"
                                        },
                                        detail = when (r.whipOutcome) {
                                            "obeyed" -> "The party demanded the ${r.whip} line — and got it. " +
                                                "The whip is satisfied: patronage flows " +
                                                "(+1 ${Ideologies.resourceOf(r.whip)}, +${r.whipPollAdj} poll)."
                                            "rebel" -> "The party demanded ${r.whip}. ${r.playerName} defied it — " +
                                                "magnificently. The crowd loves a rebel. (+${r.whipPollAdj} poll)"
                                            else -> "The party demanded ${r.whip}. ${r.playerName} strayed — and " +
                                                "unconvincingly. The party remembers. (${r.whipPollAdj} poll)"
                                        },
                                        tint = IdeologyTheme.of(r.whip).brand,
                                    ),
                                )
                            }
                            if (r.crisisOutcome.isNotBlank()) {
                                add(
                                    OutcomeChipData(
                                        label = "THE CRISIS",
                                        line = when (r.crisisOutcome) {
                                            "weathered" -> "Weathered · +2 poll"
                                            "claimed" -> "Fumbled · −3 poll"
                                            else -> "Swerved away"
                                        },
                                        detail = when (r.crisisOutcome) {
                                            "weathered" -> "A crisis aimed at ${r.crisisTarget} broke mid-argument — " +
                                                "and ${r.playerName} held the line anyway. Courage under fire: " +
                                                "+1 bonus ${Ideologies.resourceOf(r.crisisTarget)} (+2 poll)."
                                            "claimed" -> "The mid-argument crisis claimed its target: ${r.playerName} " +
                                                "argued ${r.crisisTarget} and fumbled. The nation pays — " +
                                                "${r.crisisTarget}'s home meter −3 (−3 poll)."
                                            else -> "A crisis aimed at ${r.crisisTarget} broke mid-argument — " +
                                                "${r.playerName} swerved away from it. The record will remember."
                                        },
                                        tint = MaterialTheme.colorScheme.error,
                                    ),
                                )
                            }
                            r.newEndorsements.forEach { bloc ->
                                val stolenFrom = r.defections[bloc]
                                add(
                                    OutcomeChipData(
                                        label = if (stolenFrom != null) "🔥 DEFECTION" else "🤝 ENDORSED",
                                        line = bloc,
                                        detail = if (stolenFrom != null) {
                                            "The $bloc abandon $stolenFrom and endorse ${r.playerName}. " +
                                                "Their machine now amplifies every positive poll swing (+1)."
                                        } else {
                                            "The $bloc endorse ${r.playerName}! Their machine amplifies every " +
                                                "positive poll swing (+1), and endorsements break standings ties."
                                        },
                                        tint = MaterialTheme.colorScheme.tertiary,
                                    ),
                                )
                            }
                            val c = r.consistency
                            if (c != null && c.note.isNotBlank() && c.verdict != "first_stand") {
                                add(
                                    OutcomeChipData(
                                        label = "THE RECORD",
                                        line = when (c.verdict) {
                                            "flipflop" -> "U-turn spotted"
                                            "evolved" -> "A pivot, argued well"
                                            else -> "Consistent"
                                        },
                                        detail = c.note,
                                        tint = when (c.verdict) {
                                            "flipflop" -> MaterialTheme.colorScheme.error
                                            "evolved" -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.tertiary
                                        },
                                    ),
                                )
                            }
                            if (r.pressCreditReasons.isNotEmpty()) {
                                add(
                                    OutcomeChipData(
                                        label = "PRESS CREDIT",
                                        line = if (r.pressCreditsGained > 0) {
                                            "+${r.pressCreditsGained} · feat earned"
                                        } else {
                                            "Feat — but at the cap"
                                        },
                                        detail = r.pressCreditReasons.joinToString(". ") + ". " +
                                            "Credits buy cross-examinations of rivals (hold at most 2).",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                    ),
                                )
                            }
                            r.blocReactions.forEach { b ->
                                add(
                                    OutcomeChipData(
                                        label = b.bloc.uppercase(),
                                        line = if (b.delta >= 0) "+${b.delta} support" else "${b.delta} support",
                                        detail = b.reaction,
                                        tint = if (b.delta >= 0) Color(0xFF2AF08A) else MaterialTheme.colorScheme.error,
                                    ),
                                )
                            }
                            if (r.approvalAfter >= 0) {
                                add(
                                    OutcomeChipData(
                                        label = "SNAP POLL",
                                        line = "",
                                        detail = "The night's swing: the verdict's force, the whip's account, " +
                                            "the crisis — amplified by every bloc machine behind ${r.playerName}.",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        counter = r.approvalAfter,
                                        delta = r.pollDelta,
                                    ),
                                )
                            }
                            r.nationEffects?.let { fx ->
                                val short = listOf(
                                    "Eco" to fx.economy, "Lib" to fx.liberty,
                                    "Sta" to fx.stability, "Tru" to fx.trust,
                                ).joinToString(" · ") { (n, d) ->
                                    if (d == 0) n else "$n ${if (d > 0) "+$d" else "$d"}"
                                }
                                add(
                                    OutcomeChipData(
                                        label = "THE NATION",
                                        line = short,
                                        detail = "If enacted: economy ${fx.economy}, liberty ${fx.liberty}, " +
                                            "stability ${fx.stability}, trust ${fx.trust}. Meters below 25 put " +
                                            "their ideology in crisis (+1 strength); above 75, a golden age (−1).",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                    ),
                                )
                            }
                        }
                        if (chips.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            chips.chunked(2).forEachIndexed { rowIdx, pair ->
                                var rowIn by remember(r) { mutableStateOf(false) }
                                LaunchedEffect(press) {
                                    if (press) { delay(650L + rowIdx * 140L); rowIn = true }
                                }
                                AnimatedVisibility(
                                    visible = rowIn,
                                    enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 3 },
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    ) {
                                        pair.forEach { chip -> OutcomeChip(chip, Modifier.weight(1f)) }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                            Text(
                                "Tap a chip for the full story.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // The night's lessons, still taught where they land.
                        if (r.whipOutcome.isNotBlank()) {
                            CoachMark(
                                Lessons.WHIP, vm::hasSeenLesson, vm::markLessonSeen, coachBudget,
                                highlight = when (r.whipOutcome) {
                                    "obeyed" -> 0
                                    "rebel" -> 1
                                    else -> 2
                                },
                            )
                        }
                        if (r.crisisOutcome.isNotBlank()) {
                            CoachMark(
                                Lessons.TRIPWIRE_FIRE, vm::hasSeenLesson, vm::markLessonSeen, coachBudget,
                                highlight = when (r.crisisOutcome) {
                                    "weathered" -> 0
                                    "claimed" -> 1
                                    else -> 2
                                },
                            )
                        }
                        if (r.newEndorsements.isNotEmpty()) {
                            CoachMark(
                                Lessons.DEFECTION, vm::hasSeenLesson, vm::markLessonSeen, coachBudget,
                                highlight = if (r.defections.isNotEmpty()) 1 else 0,
                            )
                        }
                        if (r.blocReactions.isNotEmpty()) {
                            CoachMark(Lessons.BLOCS, vm::hasSeenLesson, vm::markLessonSeen, coachBudget)
                        }
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
            // Politics-earned payouts/forfeits — the whip's patronage, a bloc's
            // gift, the mandate, a weathered crisis. Physical takes, same as above.
            if (r.bonusResources.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    r.bonusResources.take(3).forEach { g ->
                        StatTile(
                            label = g.label,
                            value = "${if (g.delta >= 0) "+" else ""}${g.delta} · ${Ideologies.resourceOf(g.ideology)}",
                            tint = IdeologyTheme.container(g.ideology),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                CoachMark(Lessons.GRANTS, vm::hasSeenLesson, vm::markLessonSeen, coachBudget)
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

            // Discreet, for whoever wants to know: the judge's prose, the read-
            // aloud, and the analyst desk all fold away until asked for.
            if (translated || r.reasoning.isNotBlank() || r.historicalNote.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Collapsible(
                    "The judge's full reasoning",
                    Icons.AutoMirrored.Filled.VolumeUp,
                    initiallyExpanded = false,
                ) {
                    Column {
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
                        Spacer(Modifier.height(8.dp))
                        ReadAloudRow(
                            langs = listOf(displayLang),
                            isReading = vm.isReading,
                            textFor = speakText,
                            onPlay = { lang, text -> vm.readOut(text, lang) },
                            onStop = vm::stopReadOut,
                        )
                    }
                }
            }

            // FOR THE CURIOUS — the analyst desk, folded away until asked.
            val primaryBrand = IdeologyTheme.of(r.primary).brand
            val roundPaths = vm.state.current?.paths.orEmpty()
            if (r.causalChain.isNotEmpty() || roundPaths.isNotEmpty()) {
                Spacer(Modifier.height(Dim.itemGap))
                Text(
                    "FOR THE CURIOUS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (r.causalChain.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Collapsible("Consequence map", Icons.Filled.AccountTree, initiallyExpanded = false) {
                        ConsequenceFlow(
                            chain = r.causalChain,
                            historicalNote = r.historicalNote,
                            tradeoff = r.tradeoff,
                            accent = primaryBrand,
                        )
                    }
                }
                if (roundPaths.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Collapsible("Compare the four paths", Icons.Filled.AccountTree, initiallyExpanded = false) {
                        PathsFanOut(
                            paths = roundPaths,
                            question = vm.state.current?.dilemma?.question.orEmpty(),
                            chosen = r.primary,
                        )
                    }
                }
            }

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

/** One compact outcome of the night — a chip in the morning-after grid. */
private data class OutcomeChipData(
    val label: String,
    val line: String,
    val detail: String,
    val tint: Color,
    /** When set, the chip's number rolls in with [AnimatedCounter] (snap poll). */
    val counter: Int? = null,
    val delta: Int? = null,
)

/** Icon-less, two-line outcome tile; tapping unfolds the full story in place. */
@Composable
private fun OutcomeChip(data: OutcomeChipData, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(data.tint.copy(alpha = 0.10f))
            .border(0.5.dp, data.tint.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
            .clickable { open = !open }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .animateContentSize(),
    ) {
        Text(
            data.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = data.tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        if (data.counter != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedCounter(
                    value = data.counter,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    "%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                data.delta?.let { d ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (d >= 0) "+$d" else "$d",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (d >= 0) Color(0xFF2AF08A) else MaterialTheme.colorScheme.error,
                    )
                }
            }
        } else {
            Text(
                data.line,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (open) {
            Spacer(Modifier.height(4.dp))
            Text(
                data.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
