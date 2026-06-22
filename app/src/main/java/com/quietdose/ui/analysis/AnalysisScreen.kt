package com.quietdose.ui.analysis

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietdose.brain.analysis.AnalysisProgress
import com.quietdose.brain.analysis.AnalysisReport
import com.quietdose.brain.analysis.AspectState
import com.quietdose.brain.analysis.Mood
import com.quietdose.brain.analysis.IngredientCatalog
import com.quietdose.brain.analysis.ModelCapability
import com.quietdose.brain.analysis.ModelTier
import com.quietdose.brain.analysis.ProductSignals
import com.quietdose.brain.analysis.ReportBlock
import com.quietdose.brain.analysis.Severity
import com.quietdose.brain.analysis.SourceKind
import com.quietdose.brain.analysis.StackAnalyzer
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.IngredientCodec
import com.quietdose.di.ServiceLocator
import com.quietdose.ui.icons.ItemIcon
import com.quietdose.ui.stack.ChipGroup
import com.quietdose.ui.stack.ChoiceChip
import com.quietdose.ui.stack.FilledButton
import com.quietdose.ui.stack.FlagChoices
import com.quietdose.ui.stack.StackTextField
import com.quietdose.ui.stack.TextButtonGhost
import com.quietdose.ui.stack.androidxClickable
import com.quietdose.ui.stack.label
import com.quietdose.ui.theme.Accent
import com.quietdose.ui.theme.Done
import com.quietdose.ui.theme.Ink
import com.quietdose.ui.theme.Outline
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.Surface2
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

private enum class Phase { INTENT, ANALYZING, REPORT }

/**
 * Contextual analysis a candidate item passes through before it joins the
 * checklist: intent → analyze (visible progress + grounded reasoning) → a
 * recommendation you adjust (where it lands, the dose on a scale, timing tags,
 * pairings) → add. [onAdd] receives the *configured* item.
 */
@Composable
fun AnalysisScreen(
    item: ItemEntity,
    onAdd: (ItemEntity) -> Unit,
    onDismiss: () -> Unit,
    product: ProductSignals? = null,
) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.repository(context) }
    val stack by remember { repo.observeAllItems() }
        .collectAsStateWithLifecycle(initialValue = emptyList<ItemEntity>())
    val groups by remember { repo.observeGroups() }
        .collectAsStateWithLifecycle(initialValue = emptyList<GroupEntity>())

    var phase by remember { mutableStateOf(Phase.INTENT) }
    var source by remember { mutableStateOf<SourceKind?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var concern by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<AnalysisReport?>(null) }
    val thoughts = remember { mutableStateListOf<AnalysisProgress>() }
    val tier = remember { ModelCapability.tier(context) }

    LaunchedEffect(phase) {
        if (phase == Phase.ANALYZING) {
            thoughts.clear()
            report = runCatching {
                StackAnalyzer.analyze(
                    context, item.name, item.category, stack, groups,
                    item.doseAmount, item.doseUnit,
                    goal.ifBlank { null }, source, sourceName.ifBlank { null }, concern.ifBlank { null },
                    product,
                    onProgress = { thoughts.add(it) },
                )
            }.getOrNull()
            delay(300) // let the final thought breathe
            phase = Phase.REPORT
        }
    }

    Box(Modifier.fillMaxSize().background(Ink).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Header(tier = tier, onClose = onDismiss)
            when (phase) {
                Phase.INTENT -> IntentStep(
                    item = item,
                    source = source,
                    onSource = { source = it },
                    sourceName = sourceName,
                    onSourceName = { sourceName = it },
                    goal = goal,
                    onGoal = { goal = it },
                    concern = concern,
                    onConcern = { concern = it },
                    onContinue = { phase = Phase.ANALYZING },
                )
                Phase.ANALYZING -> AnalyzingStep(item = item, thoughts = thoughts)
                Phase.REPORT -> {
                    val r = report
                    if (r == null) ErrorStep(item, onAdd, onDismiss)
                    else ReportStep(item = item, report = r, groups = groups, onAdd = onAdd, onDismiss = onDismiss)
                }
            }
        }
    }
}

/* ------------------------------- Header ------------------------------- */

@Composable
private fun Header(tier: ModelTier, onClose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(CircleShape).androidxClickable(onClose),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextMid, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text("Analyze", style = MaterialTheme.typography.titleMedium, color = TextHigh)
            Text(brainLabel(tier), style = MaterialTheme.typography.labelSmall, color = TextLow)
        }
    }
}

private fun brainLabel(tier: ModelTier): String = when (tier) {
    ModelTier.CAPABLE -> "On-device model · full reasoning"
    ModelTier.LIGHT -> "On-device model · light"
    ModelTier.NONE -> "Knowledge base · offline"
}

/* ------------------------------- Intent ------------------------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntentStep(
    item: ItemEntity,
    source: SourceKind?,
    onSource: (SourceKind?) -> Unit,
    sourceName: String,
    onSourceName: (String) -> Unit,
    goal: String,
    onGoal: (String) -> Unit,
    concern: String,
    onConcern: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            ItemHero(item)

            Spacer(Modifier.height(22.dp))
            Text("Where did you come across it?", style = MaterialTheme.typography.headlineSmall, color = TextHigh)
            Text("This shapes how much to trust the claim.", style = MaterialTheme.typography.bodyMedium, color = TextMid)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceKind.entries.forEach { s ->
                    ChoiceChip(s.label, s == source, Accent) { onSource(if (s == source) null else s) }
                }
            }
            Spacer(Modifier.height(10.dp))
            StackTextField(sourceName, onSourceName, "Who, specifically? (optional)")

            Spacer(Modifier.height(22.dp))
            Text("What are you hoping it helps with?", style = MaterialTheme.typography.titleMedium, color = TextHigh)
            Spacer(Modifier.height(8.dp))
            StackTextField(goal, onGoal, "e.g. better sleep, low energy, advised for ferritin", singleLine = false)

            Spacer(Modifier.height(22.dp))
            Text("Anything you're unsure about?", style = MaterialTheme.typography.titleMedium, color = TextHigh)
            Spacer(Modifier.height(8.dp))
            StackTextField(concern, onConcern, "optional — a worry, a side effect, a question", singleLine = false)

            Spacer(Modifier.height(24.dp))
        }
        BottomBar {
            FilledButton(label = "Analyze", accent = Accent, modifier = Modifier.fillMaxWidth(), onClick = onContinue)
        }
    }
}

/* ------------------------------ Analyzing ------------------------------ */

@Composable
private fun AnalyzingStep(item: ItemEntity, thoughts: List<AnalysisProgress>) {
    val current = thoughts.lastOrNull()
    val mood = current?.mood ?: Mood.CALM
    val lightTarget = moodColor(mood)
    val light by animateColorAsState(lightTarget, tween(700), label = "light")
    val progress by animateFloatAsState(current?.fraction ?: 0.04f, tween(600), label = "prog")
    val t = rememberInfiniteTransition(label = "an")
    val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(950, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        ItemHero(item)
        Spacer(Modifier.height(28.dp))

        // The mind: a mood-lit orb that breathes while it thinks.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(120.dp).clip(CircleShape).background(light.copy(alpha = 0.10f * a)))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp).clip(CircleShape).background(light.copy(alpha = 0.18f)).graphicsLayer { alpha = a },
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = light, modifier = Modifier.size(34.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = light, trackColor = Outline)
        Spacer(Modifier.height(20.dp))

        // The live label + commentary (the brain talking).
        Text(current?.label ?: "Thinking", style = MaterialTheme.typography.titleMedium, color = TextHigh)
        Spacer(Modifier.height(4.dp))
        Text(current?.commentary ?: "Settling in…", style = MaterialTheme.typography.bodyLarge, color = TextMid)

        // The stream of consciousness — recent thoughts, freshest brightest.
        Spacer(Modifier.height(20.dp))
        val recent = thoughts.takeLast(6).dropLast(1).asReversed()
        recent.forEachIndexed { i, th ->
            val fade = (1f - i * 0.16f).coerceIn(0.25f, 0.8f)
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp).graphicsLayer { alpha = fade }) {
                Box(Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(moodColor(th.mood)))
                Spacer(Modifier.width(10.dp))
                Text("${th.label} · ${th.commentary}", style = MaterialTheme.typography.bodyMedium, color = TextMid, maxLines = 1)
            }
        }
    }
}

private fun moodColor(mood: Mood): Color = when (mood) {
    Mood.FAVORABLE -> Done
    Mood.CAUTIOUS -> Accent
    Mood.SKEPTICAL -> Accent
    Mood.CURIOUS -> Accent
    Mood.REFLECTIVE -> Accent
    Mood.CALM -> TextMid
}

/* ------------------------------- Report ------------------------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportStep(
    item: ItemEntity,
    report: AnalysisReport,
    groups: List<GroupEntity>,
    onAdd: (ItemEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val rec = report.recommendation
    // Editable config, pre-filled from the recommendation.
    var groupId by remember { mutableStateOf(rec.groupId ?: item.groupId) }
    var doseText by remember { mutableStateOf(trimDose(rec.doseAmount)) }
    var doseUnit by remember { mutableStateOf(rec.doseUnit) }
    var flags by remember { mutableIntStateOf(rec.flags) }

    val low = rec.typicalLow
    val high = rec.typicalHigh
    val sliderMax = (high?.times(2.0) ?: maxOf((doseText.toDoubleOrNull() ?: 1.0) * 3.0, 10.0)).toFloat()
    val doseValue = (doseText.toFloatOrNull() ?: 0f).coerceIn(0f, sliderMax)

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            ItemHero(item)

            Spacer(Modifier.height(16.dp))
            // Dynamic, brain-composed page: walk the template blocks in order.
            report.blocks.forEach { block ->
                BlockView(block)
                Spacer(Modifier.height(10.dp))
            }

            // --- Configure: where / dose / timing / pairings ---
            Spacer(Modifier.height(6.dp))
            Text("PLACE IT", style = MaterialTheme.typography.labelSmall, color = TextLow)
            Spacer(Modifier.height(8.dp))

            // Where
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1).padding(16.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Where", style = MaterialTheme.typography.titleMedium, color = TextHigh, modifier = Modifier.weight(1f))
                        rec.groupReason?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Accent) }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (groups.isEmpty()) {
                        Text("No groups yet — it'll be added to your stack.", style = MaterialTheme.typography.bodyMedium, color = TextMid)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            groups.forEach { g -> ChoiceChip(g.name, g.id == groupId, Accent) { groupId = g.id } }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Dose — a meaningful scale with the typical band noted
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1).padding(16.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dose", style = MaterialTheme.typography.titleMedium, color = TextHigh, modifier = Modifier.weight(1f))
                        if (low != null && high != null) {
                            Text("typical ${trimDose(low)}–${trimDose(high)} ${doseUnit.label()}", style = MaterialTheme.typography.labelSmall, color = TextLow)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StackTextField(
                            value = doseText,
                            onValueChange = { doseText = it.filter { c -> c.isDigit() || c == '.' } },
                            placeholder = "1",
                            keyboardType = KeyboardType.Decimal,
                            accent = Accent,
                            modifier = Modifier.width(110.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(doseUnit.label(), style = MaterialTheme.typography.titleMedium, color = TextMid)
                    }
                    if (sliderMax > 0f) {
                        Slider(
                            value = doseValue,
                            onValueChange = { doseText = trimDose(it.toDouble()) },
                            valueRange = 0f..sliderMax,
                            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Outline),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ChipGroup(options = DoseUnit.entries, selected = doseUnit, accent = Accent, label = { it.label() }, onSelect = { doseUnit = it })
                }
            }

            Spacer(Modifier.height(10.dp))

            // Timing tags
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1).padding(16.dp)) {
                Column {
                    Text("Timing", style = MaterialTheme.typography.titleMedium, color = TextHigh)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlagChoices.forEach { (bit, text) ->
                            ChoiceChip(text, flags and bit != 0, Accent) { flags = flags xor bit }
                        }
                    }
                }
            }

            if (rec.pairings.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1).padding(16.dp)) {
                    Column {
                        Text("Consider pairing", style = MaterialTheme.typography.titleMedium, color = TextHigh)
                        Spacer(Modifier.height(8.dp))
                        Text(rec.pairings.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = TextMid)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        BottomBar {
            FilledButton(label = "Add to checklist", accent = Accent, modifier = Modifier.fillMaxWidth()) {
                // Fill in what the catalog knows but the source omitted — category
                // (and the canonical name) so brand/category aren't lost on a link add.
                val matched = report.matchedIngredientKey?.let { IngredientCatalog.byKey(it) }
                onAdd(
                    item.copy(
                        groupId = groupId,
                        category = item.category?.ifBlank { null } ?: matched?.category,
                        doseAmount = doseText.toDoubleOrNull() ?: item.doseAmount,
                        doseUnit = doseUnit,
                        flags = flags,
                        ingredients = IngredientCodec.encode(report.ingredients) ?: item.ingredients,
                    ),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButtonGhost("Not now", color = TextLow, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun BottomBar(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Surface1).navigationBarsPadding().padding(16.dp),
        content = content,
    )
}

/* --------------------- Dynamic template block renderers --------------------- */

@Composable
private fun BlockView(block: ReportBlock) {
    when (block) {
        is ReportBlock.Verdict -> VerdictBlock(block)
        is ReportBlock.Facts -> FactsBlock(block)
        is ReportBlock.Meter -> MeterBlock(block)
        is ReportBlock.Aspect -> AspectBlock(block)
        is ReportBlock.Reasoning -> ReasoningBlock(block)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerdictBlock(b: ReportBlock.Verdict) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Accent.copy(alpha = 0.10f)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(b.verdict, style = MaterialTheme.typography.titleLarge, color = TextHigh, modifier = Modifier.weight(1f))
            b.score?.let { ScoreBadge(it) }
        }
        Spacer(Modifier.height(8.dp))
        Text(b.rationale, style = MaterialTheme.typography.bodyLarge, color = TextMid)
        if (b.tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                b.tags.forEach { TagChip(it) }
            }
        }
    }
}

@Composable
private fun ScoreBadge(score: Int) {
    val c = when { score >= 80 -> Done; score >= 60 -> Accent; else -> Accent }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text("$score", style = MaterialTheme.typography.titleMedium, color = c)
    }
}

@Composable
private fun TagChip(text: String) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Surface2).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = TextMid)
    }
}

@Composable
private fun AspectBlock(b: ReportBlock.Aspect) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.aspect.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextLow, modifier = Modifier.weight(1f))
            StateChip(b.state)
        }
        b.summary?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = TextHigh)
        }
        if (b.lines.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            b.lines.forEach { line ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                    Box(Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(severityColor(line.severity)))
                    Spacer(Modifier.width(10.dp))
                    Text(line.text, style = MaterialTheme.typography.bodyMedium, color = TextMid)
                }
            }
        }
    }
}

@Composable
private fun StateChip(state: AspectState) {
    val (label, color) = when (state) {
        AspectState.GOOD -> "good" to Done
        AspectState.MIXED -> "mixed" to Accent
        AspectState.CAUTION -> "mind it" to Accent
        AspectState.CLEAR -> "clear" to Done
        AspectState.NOT_ASSESSED -> "n/a" to TextLow
    }
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun MeterBlock(b: ReportBlock.Meter) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.label, style = MaterialTheme.typography.titleMedium, color = TextHigh, modifier = Modifier.weight(1f))
            Text(b.valueText, style = MaterialTheme.typography.titleMedium, color = Accent)
        }
        Spacer(Modifier.height(10.dp))
        // Track with the typical band shaded and the dose filled to its position.
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Outline)) {
            if (b.bandLow != null && b.bandHigh != null && b.bandHigh > b.bandLow) {
                Row(Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(b.bandLow.coerceIn(0.0001f, 1f)))
                    Box(Modifier.weight((b.bandHigh - b.bandLow).coerceIn(0.01f, 1f)).fillMaxHeight().background(Done.copy(alpha = 0.22f)))
                    Spacer(Modifier.weight((1f - b.bandHigh).coerceIn(0.0001f, 1f)))
                }
            }
            Box(Modifier.fillMaxWidth(b.fraction.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(Accent.copy(alpha = 0.85f)))
        }
        b.caption?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = TextLow)
        }
    }
}

@Composable
private fun FactsBlock(b: ReportBlock.Facts) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1).padding(16.dp)) {
        Text(b.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextLow)
        Spacer(Modifier.height(8.dp))
        b.rows.forEach { (k, v) ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                Text(k, style = MaterialTheme.typography.bodyMedium, color = TextLow, modifier = Modifier.width(96.dp))
                Text(v, style = MaterialTheme.typography.bodyMedium, color = TextHigh, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReasoningBlock(b: ReportBlock.Reasoning) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(16.dp)) {
        Text("REASONING", style = MaterialTheme.typography.labelSmall, color = TextLow)
        Spacer(Modifier.height(8.dp))
        b.items.forEach {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                Text("·  ", style = MaterialTheme.typography.bodyMedium, color = Accent)
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMid)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (b.byModel) "Reasoned on-device, grounded in references." else "Derived from the on-device knowledge base.",
            style = MaterialTheme.typography.labelSmall,
            color = TextLow,
        )
    }
}

@Composable
private fun ItemHero(item: ItemEntity) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface2).padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(56.dp).clip(CircleShape).background(Accent.copy(alpha = 0.14f)),
        ) {
            ItemIcon(type = item.type, tint = Accent, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name.ifBlank { "New item" }, style = MaterialTheme.typography.titleLarge, color = TextHigh)
            val sub = listOfNotNull(item.brand?.ifBlank { null }, item.category?.ifBlank { null }).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
    }
}

@Composable
private fun ErrorStep(item: ItemEntity, onAdd: (ItemEntity) -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            ItemHero(item)
            Spacer(Modifier.height(20.dp))
            Text("Couldn't analyze this one", style = MaterialTheme.typography.titleLarge, color = TextHigh)
            Text("You can still add it and refine later.", style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
        BottomBar {
            FilledButton(label = "Add anyway", accent = Accent, modifier = Modifier.fillMaxWidth()) { onAdd(item) }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButtonGhost("Not now", color = TextLow, onClick = onDismiss)
            }
        }
    }
}

private fun severityColor(s: Severity): Color = when (s) {
    Severity.GOOD -> Done
    Severity.NEUTRAL -> Outline
    Severity.CAUTION -> Accent
}

private fun trimDose(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else (Math.round(v * 10.0) / 10.0).toString()
