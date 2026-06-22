package com.quietdose.ui.analysis

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietdose.brain.analysis.AnalysisLine
import com.quietdose.brain.analysis.AnalysisProgress
import com.quietdose.brain.analysis.AnalysisReport
import com.quietdose.brain.analysis.AspectState
import com.quietdose.brain.analysis.ClaimLine
import com.quietdose.brain.analysis.ClaimStatus
import com.quietdose.brain.analysis.IngredientCatalog
import com.quietdose.brain.analysis.IngredientLine
import com.quietdose.brain.analysis.RatingDim
import com.quietdose.brain.analysis.KindDetector
import com.quietdose.brain.analysis.ModelCapability
import com.quietdose.brain.analysis.ModelTier
import com.quietdose.brain.analysis.Mood
import com.quietdose.brain.analysis.ProductLook
import com.quietdose.brain.analysis.ProductLookCodec
import com.quietdose.brain.analysis.ProductSignals
import com.quietdose.brain.analysis.ReportBlock
import com.quietdose.brain.analysis.Severity
import com.quietdose.brain.analysis.SourceKind
import com.quietdose.brain.analysis.StackAnalyzer
import com.quietdose.brain.enrich.ImagePalette
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.IngredientCodec
import com.quietdose.di.ServiceLocator
import com.quietdose.ui.stack.ChipGroup
import com.quietdose.ui.stack.ChoiceChip
import com.quietdose.ui.stack.FilledButton
import com.quietdose.ui.stack.FlagChoices
import com.quietdose.ui.stack.StackTextField
import com.quietdose.ui.stack.TextButtonGhost
import com.quietdose.ui.stack.androidxClickable
import com.quietdose.ui.stack.label
import com.quietdose.ui.theme.Accent
import com.quietdose.ui.theme.AccentTint
import com.quietdose.ui.theme.Caution
import com.quietdose.ui.theme.CautionTint
import com.quietdose.ui.theme.DimensionFigure
import com.quietdose.ui.theme.Done
import com.quietdose.ui.theme.GoodTint
import com.quietdose.ui.theme.Ink
import com.quietdose.ui.theme.MoodCalm
import com.quietdose.ui.theme.MoodCautious
import com.quietdose.ui.theme.MoodCurious
import com.quietdose.ui.theme.MoodFavorable
import com.quietdose.ui.theme.MoodReflective
import com.quietdose.ui.theme.MoodSkeptical
import com.quietdose.ui.theme.Outline
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.Surface2
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid
import com.quietdose.ui.theme.VerdictSurface
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

    // Sample the glyph palette from the REAL packaging photo once, off the main
    // thread. Null while loading or on any failure — the glyph then falls back to
    // its category palette, so this never blocks or breaks the screen.
    val sampled by produceState<ImagePalette.PaletteResult?>(initialValue = null, product?.imageUrl) {
        value = ImagePalette.dominant(product?.imageUrl)
    }

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
                Phase.ANALYZING -> AnalyzingStep(item = item, thoughts = thoughts, sampled = sampled)
                Phase.REPORT -> {
                    val r = report
                    if (r == null) ErrorStep(item, onAdd, onDismiss)
                    else ReportStep(item = item, report = r, groups = groups, sampled = sampled, onAdd = onAdd, onDismiss = onDismiss)
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

/** Map the model's mood to a calm ambient tint behind the glyph. */
private fun moodColor(mood: Mood?): Color = when (mood) {
    Mood.CALM, null -> MoodCalm
    Mood.CURIOUS -> MoodCurious
    Mood.FAVORABLE -> MoodFavorable
    Mood.CAUTIOUS -> MoodCautious
    Mood.SKEPTICAL -> MoodSkeptical
    Mood.REFLECTIVE -> MoodReflective
}

/**
 * Working-it-out, not a mechanical blob: the product's own [ProductGlyph] sits in
 * a soft, slowly-breathing halo whose colour eases toward the model's current
 * mood — so the screen visibly "leans" favourable / cautious as the read resolves.
 */
@Composable
private fun AnalyzingStep(
    item: ItemEntity,
    thoughts: List<AnalysisProgress>,
    sampled: ImagePalette.PaletteResult? = null,
) {
    val current = thoughts.lastOrNull()
    val progress by animateFloatAsState(current?.fraction ?: 0.05f, tween(500), label = "prog")
    val mood by animateColorAsState(moodColor(current?.mood), tween(900), label = "mood")
    val t = rememberInfiniteTransition(label = "an")
    // A gentle, slow breath — the halo grows and fades, never strobes.
    val breath by t.animateFloat(0.85f, 1.06f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "breath")
    val glow by t.animateFloat(0.10f, 0.20f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "glow")

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        ItemHero(item, sampled = sampled)
        Spacer(Modifier.height(48.dp))
        // The breathing mood halo + the product lookalike resolving inside it.
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
            Box(
                Modifier.size(180.dp).graphicsLayer { scaleX = breath; scaleY = breath }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(mood.copy(alpha = glow), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
            ProductGlyph(
                name = item.name,
                brand = item.brand,
                category = item.category,
                type = item.type,
                modifier = Modifier.size(96.dp),
                sampled = sampled,
                showBacking = true,
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            current?.label ?: "Analyzing",
            style = MaterialTheme.typography.titleMedium,
            color = TextHigh,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            current?.commentary ?: "Reading the details…",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMid,
        )
        Spacer(Modifier.height(28.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = mood,
            trackColor = Outline,
        )
    }
}

/* ------------------------------- Report ------------------------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportStep(
    item: ItemEntity,
    report: AnalysisReport,
    groups: List<GroupEntity>,
    sampled: ImagePalette.PaletteResult? = null,
    onAdd: (ItemEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val rec = report.recommendation
    // The product's primary benefits live on the Verdict block — they legend the
    // glyph's orbit dots. Empty list when the brain supplied none (then no chips/dots).
    val benefits = remember(report) {
        report.blocks.firstNotNullOfOrNull { (it as? ReportBlock.Verdict)?.benefits }.orEmpty()
    }
    // Editable config, pre-filled from the recommendation.
    var groupId by remember { mutableStateOf(rec.groupId ?: item.groupId) }
    var doseText by remember { mutableStateOf(trimDose(rec.doseAmount)) }
    var doseUnit by remember { mutableStateOf(rec.doseUnit) }
    var flags by remember { mutableIntStateOf(rec.flags) }

    // A toner/serum/device must not be judged like a pill: applied items get
    // routine placement (AM / PM / both), not fasted/with-food supplement flags.
    val ingested = remember(item.name, item.category) {
        KindDetector.detect(item.name, item.category).isIngested
    }
    var routine by remember { mutableStateOf(RoutineSlot.AM) }
    // The brain's own routine guidance, if a chapter surfaced it (e.g. "after cleansing").
    val routineHint = remember(report) { routineHintFrom(report) }

    val low = rec.typicalLow
    val high = rec.typicalHigh
    val sliderMax = (high?.times(2.0) ?: maxOf((doseText.toDoubleOrNull() ?: 1.0) * 3.0, 10.0)).toFloat()
    val doseValue = (doseText.toFloatOrNull() ?: 0f).coerceIn(0f, sliderMax)

    // One piece of sheet state, hoisted here. A tapped score / claim / ingredient
    // sets it (title + body) to open the reusable DetailSheet; null = closed.
    var detail by remember { mutableStateOf<DetailContent?>(null) }
    val onDetail: (String, String) -> Unit = { title, body -> detail = DetailContent(title, body) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            ItemHero(item, sampled = sampled, benefits = benefits)

            Spacer(Modifier.height(20.dp))
            // Dynamic, brain-composed page: walk the template blocks in order.
            // The Verdict leads; quieter chapters follow with generous rhythm.
            report.blocks.forEachIndexed { i, block ->
                BlockView(block, item = item, sampled = sampled, onDetail = onDetail)
                if (i != report.blocks.lastIndex) Spacer(Modifier.height(18.dp))
            }

            // --- Configure: where / dose / timing / pairings ---
            Spacer(Modifier.height(28.dp))
            Eyebrow("Place it")
            Spacer(Modifier.height(10.dp))

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

            // Timing — supplements carry intake flags (fasted / with food …); applied
            // items (skincare, devices) instead land in the AM / PM routine.
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1).padding(16.dp)) {
                Column {
                    if (ingested) {
                        Text("Timing", style = MaterialTheme.typography.titleMedium, color = TextHigh)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FlagChoices.forEach { (bit, text) ->
                                ChoiceChip(text, flags and bit != 0, Accent) { flags = flags xor bit }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Routine", style = MaterialTheme.typography.titleMedium, color = TextHigh, modifier = Modifier.weight(1f))
                            Text("when in the day", style = MaterialTheme.typography.labelSmall, color = TextLow)
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoutineSlot.entries.forEach { slot ->
                                ChoiceChip(slot.label, slot == routine, Accent) { routine = slot }
                            }
                        }
                        routineHint?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), color = TextMid)
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
                // Applied items have no timing column — fold the chosen routine slot
                // into the note in plain words; supplements keep their intake flags.
                val finalNote = if (ingested) {
                    item.note
                } else {
                    listOf("Use: ${routine.label.lowercase()}", item.note?.trim().orEmpty())
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { null }
                }
                // Capture the drawn look (form + sampled packaging palette) and the primary
                // benefits so the saved item keeps its glyph + orbs in the stack — redrawn
                // offline from these, no re-sampling needed.
                val storedLook = ProductLookCodec.encode(
                    ProductLook(
                        form = inferContainerForm(item.name, item.category, item.type).name,
                        bodyArgb = sampled?.primary?.toArgb()?.toLong(),
                        accentArgb = sampled?.accent?.toArgb()?.toLong(),
                    ),
                )
                onAdd(
                    item.copy(
                        groupId = groupId,
                        category = item.category?.ifBlank { null } ?: matched?.category,
                        doseAmount = doseText.toDoubleOrNull() ?: item.doseAmount,
                        doseUnit = doseUnit,
                        flags = if (ingested) flags else item.flags,
                        note = finalNote,
                        ingredients = IngredientCodec.encode(report.ingredients) ?: item.ingredients,
                        look = storedLook,
                        benefits = benefits.joinToString("\n").ifBlank { null },
                    ),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButtonGhost("Not now", color = TextLow, onClick = onDismiss)
            }
        }
    }

    // The single explainer sheet for the whole report — opened by any tapped
    // score bar, ingredient or claim, dismissed back to null.
    detail?.let { DetailSheet(it.title, it.body, onDismiss = { detail = null }) }
}

/** A tapped drill-down's payload: a short title and the grounded body it explains. */
private data class DetailContent(val title: String, val body: String)

/**
 * Reusable explainer — a calm [ModalBottomSheet] (Surface1) holding a title + body.
 * Mirrors the sheet idiom in [com.quietdose.ui.stack.ItemEditorSheet]. Every
 * tappable report element (a score bar, an ingredient, a claim) opens this same
 * sheet via the hoisted [onDetail] setter, so explanations stay one consistent surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailSheet(title: String, body: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface1,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(Surface2))
            }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 28.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = TextHigh)
            Spacer(Modifier.height(12.dp))
            Text(
                body.ifBlank { "No further detail available." },
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
                color = TextMid,
            )
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
private fun BlockView(
    block: ReportBlock,
    item: ItemEntity,
    sampled: ImagePalette.PaletteResult?,
    onDetail: (String, String) -> Unit,
) {
    when (block) {
        is ReportBlock.Verdict -> VerdictBlock(block, item = item, sampled = sampled, onDetail = onDetail)
        is ReportBlock.Facts -> FactsBlock(block)
        is ReportBlock.Meter -> MeterBlock(block)
        is ReportBlock.Aspect -> ChapterView(block.aspect.title, block.summary, block.lines, block.state)
        is ReportBlock.Chapter -> ChapterView(block.title, block.summary, block.lines, block.state)
        is ReportBlock.Reviews -> ReviewsView(block)
        is ReportBlock.Ingredients -> IngredientsView(block, onDetail)
        is ReportBlock.Claims -> ClaimsView(block, onDetail)
        is ReportBlock.Reasoning -> ReasoningBlock(block)
    }
}

/* --- shared bits --- */

/** A quiet, spaced-out eyebrow that introduces a section without a heavy card. */
@Composable
private fun Eyebrow(text: String, color: Color = TextLow) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
        color = color,
    )
}

/** A grounded point: a small severity dot followed by comfortable-measure text. */
@Composable
private fun GroundedLine(text: String, severity: Severity) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 5.dp)) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(severityColor(severity)))
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
            color = TextMid,
        )
    }
}

/* --- Verdict: the confident, calm header --- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerdictBlock(
    b: ReportBlock.Verdict,
    item: ItemEntity,
    sampled: ImagePalette.PaletteResult?,
    onDetail: (String, String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(VerdictSurface)
            .border(1.dp, Outline.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
            .padding(22.dp),
    ) {
        Eyebrow("The verdict", color = Accent)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A small product thumbnail anchors the verdict to the thing being judged.
            ProductGlyph(
                name = item.name,
                brand = item.brand,
                category = item.category,
                type = item.type,
                modifier = Modifier.size(40.dp),
                sampled = sampled,
                showBacking = true,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                b.verdict,
                style = MaterialTheme.typography.headlineLarge,
                color = TextHigh,
                modifier = Modifier.weight(1f),
            )
            b.score?.let {
                Spacer(Modifier.width(16.dp))
                ScoreRing(it)
            }
        }
        Spacer(Modifier.height(14.dp))
        // The rationale collapses to ~3 lines with a quiet Read more / Show less toggle,
        // so the verdict stays scannable but the full reasoning is one tap away.
        var expanded by remember { mutableStateOf(false) }
        Text(
            b.rationale,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
            color = TextMid,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (expanded) "Show less" else "Read more",
            style = MaterialTheme.typography.labelMedium,
            color = Accent,
            modifier = Modifier.androidxClickable { expanded = !expanded },
        )
        // Per-parameter ratings the overall score is built from — calm labelled
        // bars (label · slim 0..100 track · number), only when the brain supplies them.
        // Each is tappable for "how this was scored".
        if (b.dimensions.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                b.dimensions.forEach { DimensionBar(it, onDetail) }
            }
        }
        // A gentle one-line legend so the dot/state colours read consistently.
        Spacer(Modifier.height(16.dp))
        SeverityLegend()
        if (b.tags.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                b.tags.forEach { TagChip(it) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = TextLow, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (b.byModel) "Reasoned on-device" else "From the on-device knowledge base",
                style = MaterialTheme.typography.labelSmall,
                color = TextLow,
            )
        }
    }
}

/** A quiet score ring — a thin arc and a number, not a loud meter. */
@Composable
private fun ScoreRing(score: Int) {
    val color = when { score >= 80 -> Done; score >= 55 -> Accent; else -> Caution }
    val fraction = (score.coerceIn(0, 100)) / 100f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = Outline,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            "$score",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextHigh,
        )
    }
}

/** The fill colour for a 0..100 rating — Done high, Accent mid, Caution low. */
private fun bandColor(score: Int): Color = when {
    score >= 75 -> Done
    score >= 50 -> Accent
    else -> Caution
}

/**
 * One labelled rating row: the parameter on the left, a slim 0..100 track in the
 * middle (the MeterBlock idiom — Outline track, banded fill), the number on the
 * right. Tight and scannable; many of these stack under the overall ring.
 */
@Composable
private fun DimensionBar(dim: RatingDim, onDetail: (String, String) -> Unit) {
    val v = dim.score.coerceIn(0, 100)
    val color = bandColor(v)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Tap any rating to learn how it was scored — opens the explainer sheet.
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .androidxClickable { onDetail("How ${dim.label} was scored", dim.why) }
            .padding(vertical = 2.dp),
    ) {
        Text(
            dim.label,
            style = MaterialTheme.typography.labelLarge,
            color = TextMid,
            modifier = Modifier.width(74.dp),
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Outline),
        ) {
            Box(
                Modifier.fillMaxWidth((v / 100f).coerceIn(0.02f, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp)).background(color),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text("$v", style = DimensionFigure, color = TextHigh, modifier = Modifier.width(24.dp))
    }
}

/** A faint, single-line key so the severity dots/states read the same everywhere. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeverityLegend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LegendDot("Good", severityColor(Severity.GOOD))
        LegendDot("Mind it", severityColor(Severity.CAUTION))
        LegendDot("Neutral", severityColor(Severity.NEUTRAL))
    }
}

@Composable
private fun LegendDot(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = TextLow)
    }
}

@Composable
private fun TagChip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(99.dp))
            .background(Surface2)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = TextMid)
    }
}

/* --- Chapter: a clean section, eyebrow + standout summary + grounded lines --- */

@Composable
private fun ChapterView(title: String, summary: String?, lines: List<AnalysisLine>, state: AspectState) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(title, color = TextMid)
            Spacer(Modifier.weight(1f))
            StateChip(state)
        }
        summary?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it,
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 24.sp),
                color = TextHigh,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
        if (lines.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            lines.forEach { GroundedLine(it.text, it.severity) }
        }
    }
}

/** A restrained state cue — a faint tint and one calm word, never a loud badge. */
@Composable
private fun StateChip(state: AspectState) {
    val (label, color, tint) = when (state) {
        AspectState.GOOD -> Triple("Good", Done, GoodTint)
        AspectState.MIXED -> Triple("Mixed", Accent, AccentTint)
        AspectState.CAUTION -> Triple("Mind it", Caution, CautionTint)
        AspectState.CLEAR -> Triple("Clear", Done, GoodTint)
        AspectState.NOT_ASSESSED -> Triple("Not assessed", TextLow, Color.Transparent)
    }
    Box(Modifier.clip(RoundedCornerShape(99.dp)).background(tint).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/* --- Ingredients: "What's in it" — a tappable row per active --- */

/**
 * Each ingredient is a calm row: a severity dot, its name, a small role chip. Tapping
 * the row opens the explainer sheet with that ingredient's grounded [IngredientLine.note].
 */
@Composable
private fun IngredientsView(b: ReportBlock.Ingredients, onDetail: (String, String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Eyebrow("What's in it", color = TextMid)
        if (b.items.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            b.items.forEach { IngredientRow(it, onDetail) }
        }
    }
}

@Composable
private fun IngredientRow(line: IngredientLine, onDetail: (String, String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .androidxClickable { onDetail(line.name, line.note) }
            .padding(vertical = 8.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(severityColor(line.severity)))
        Spacer(Modifier.width(12.dp))
        Text(
            line.name,
            style = MaterialTheme.typography.bodyLarge,
            color = TextHigh,
            modifier = Modifier.weight(1f),
        )
        if (line.role.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            RoleChip(line.role)
        }
    }
}

/** A faint role pill — "Humectant", "Active" … — quiet next to the ingredient name. */
@Composable
private fun RoleChip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(99.dp)).background(Surface2).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = TextMid)
    }
}

/* --- Claims: "Claims, checked" — a tappable row per claim with a status chip --- */

/**
 * Each marketing claim is a row: a status chip (its honesty), then the claim text.
 * Tapping opens the explainer sheet with the [ClaimLine.basis] — why it earned that status.
 */
@Composable
private fun ClaimsView(b: ReportBlock.Claims, onDetail: (String, String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Eyebrow("Claims, checked", color = TextMid)
        if (b.items.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            b.items.forEach { ClaimRow(it, onDetail) }
        }
    }
}

@Composable
private fun ClaimRow(line: ClaimLine, onDetail: (String, String) -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .androidxClickable { onDetail("“${line.claim}”", line.basis) }
            .padding(vertical = 8.dp),
    ) {
        ClaimStatusChip(line.status)
        Spacer(Modifier.width(12.dp))
        Text(
            line.claim,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
            color = TextMid,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A calm status pill keyed to how the claim held up. */
@Composable
private fun ClaimStatusChip(status: ClaimStatus) {
    val (label, color, tint) = when (status) {
        ClaimStatus.SUPPORTED -> Triple("Supported", Done, GoodTint)
        ClaimStatus.PLAUSIBLE -> Triple("Plausible", Accent, AccentTint)
        ClaimStatus.UNVERIFIED -> Triple("Unverified", TextMid, Surface2)
        ClaimStatus.OVERREACH -> Triple("Overreach", Caution, CautionTint)
    }
    Box(Modifier.clip(RoundedCornerShape(99.dp)).background(tint).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/* --- Reviews: distilled keyword chips, not a wall of URLs --- */

/**
 * What people actually say, made calm: a one-line takeaway, then recurring praise
 * as soft positive chips and watch-outs as gentle amber chips, with the domains
 * the read drew from reduced to a quiet footnote. Mirrors [ReportBlock.Reviews]:
 * takeaway · loved · watch · sources · state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewsView(b: ReportBlock.Reviews) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("What people say", color = TextMid)
            Spacer(Modifier.weight(1f))
            StateChip(b.state)
        }
        b.takeaway.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it,
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 24.sp),
                color = TextHigh,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
        if (b.loved.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Eyebrow("Loved", color = Done)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                b.loved.forEach { ReviewChip(it, Done, GoodTint) }
            }
        }
        if (b.watch.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Eyebrow("Watch", color = Caution)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                b.watch.forEach { ReviewChip(it, Caution, CautionTint) }
            }
        }
        if (b.sources.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "from ${b.sources.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                color = TextLow,
            )
        }
    }
}

/** A soft sentiment chip — a faint tint behind a single keyword. */
@Composable
private fun ReviewChip(text: String, color: Color, tint: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(99.dp)).background(tint).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/* --- Meter: a refined slim track --- */

@Composable
private fun MeterBlock(b: ReportBlock.Meter) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Eyebrow(b.label, color = TextMid)
            Spacer(Modifier.weight(1f))
            Text(b.valueText, style = MaterialTheme.typography.titleMedium, color = TextHigh)
        }
        Spacer(Modifier.height(12.dp))
        // A thin track: the typical band gently shaded, the dose as a quiet fill.
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Outline)) {
            if (b.bandLow != null && b.bandHigh != null && b.bandHigh > b.bandLow) {
                Row(Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(b.bandLow.coerceIn(0.0001f, 1f)))
                    Box(Modifier.weight((b.bandHigh - b.bandLow).coerceIn(0.01f, 1f)).fillMaxHeight().background(Done.copy(alpha = 0.28f)))
                    Spacer(Modifier.weight((1f - b.bandHigh).coerceIn(0.0001f, 1f)))
                }
            }
            Box(Modifier.fillMaxWidth(b.fraction.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Accent))
        }
        b.caption?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp), color = TextLow)
        }
    }
}

/* --- Facts: a tidy two-column block --- */

@Composable
private fun FactsBlock(b: ReportBlock.Facts) {
    Column(Modifier.fillMaxWidth()) {
        Eyebrow(b.title, color = TextMid)
        Spacer(Modifier.height(12.dp))
        b.rows.forEachIndexed { i, (k, v) ->
            if (i != 0) Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 5.dp)) {
                Text(k, style = MaterialTheme.typography.bodyMedium, color = TextLow, modifier = Modifier.width(108.dp))
                Spacer(Modifier.width(12.dp))
                Text(v, style = MaterialTheme.typography.bodyMedium, color = TextHigh, modifier = Modifier.weight(1f))
            }
        }
    }
}

/* --- Reasoning: a quiet trailer, "how I worked this out" --- */

@Composable
private fun ReasoningBlock(b: ReportBlock.Reasoning) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1.copy(alpha = 0.6f))
            .padding(18.dp),
    ) {
        Eyebrow("How I worked this out", color = TextLow)
        Spacer(Modifier.height(12.dp))
        b.items.forEach {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(TextLow))
                Spacer(Modifier.width(12.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp), color = TextMid)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (b.byModel) "Reasoned on-device, grounded in references." else "Derived from the on-device knowledge base.",
            style = MaterialTheme.typography.labelSmall,
            color = TextLow,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemHero(
    item: ItemEntity,
    sampled: ImagePalette.PaletteResult? = null,
    benefits: List<String> = emptyList(),
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface2).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)).background(Surface1),
            ) {
                // The dynamic, on-device lookalike — a stylised stand-in for the real
                // product. Palette sampled from the packaging photo when available;
                // primary benefits ring it as tinted orbit dots. A backing ring +
                // guaranteed-contrast body keep the monogram legible on dark packaging.
                ProductGlyph(
                    name = item.name,
                    brand = item.brand,
                    category = item.category,
                    type = item.type,
                    modifier = Modifier.size(64.dp),
                    sampled = sampled,
                    benefits = benefits,
                    showBacking = true,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                // Brand + product name + type, read clearly so the hero is identifiable.
                item.brand?.ifBlank { null }?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = Accent)
                }
                Text(item.name.ifBlank { "New item" }, style = MaterialTheme.typography.titleLarge, color = TextHigh)
                val sub = listOfNotNull(item.category?.ifBlank { null }, item.type.label()).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = TextMid)
            }
        }
        // Benefit chips act as the legend for the glyph's orbit dots — chip N's
        // tint matches orbit dot N, so colour ↔ benefit reads at a glance.
        if (benefits.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                benefits.take(6).forEachIndexed { i, b -> BenefitChip(b, benefitColor(i)) }
            }
        }
    }
}

/** A calm benefit chip — the benefit's derived symbol (matching the glyph's orb) + the word. */
@Composable
private fun BenefitChip(text: String, color: Color) {
    val symbol = remember(text) { benefitSymbolFor(text) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(99.dp))
            .background(Surface1)
            .padding(start = 9.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Canvas(Modifier.size(13.dp)) {
            drawBenefitSymbol(symbol, Offset(size.width / 2f, size.height / 2f), size.minDimension * 0.42f, color)
        }
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = TextMid)
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
    Severity.NEUTRAL -> TextLow.copy(alpha = 0.55f) // a whisper — present, never loud
    Severity.CAUTION -> Caution
}

private fun trimDose(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else (Math.round(v * 10.0) / 10.0).toString()

/** Where an applied item lands in the day — the calm alternative to intake flags. */
private enum class RoutineSlot(val label: String) {
    AM("AM"),
    PM("PM"),
    BOTH("AM & PM"),
}

/**
 * The brain's own "how & when to use" prose, if a chapter surfaced it — shown as a
 * quiet read under the routine picker. We don't invent it; we just relay the
 * summary of a use/routine chapter when the composed page contains one.
 */
private fun routineHintFrom(report: AnalysisReport): String? {
    val cue = listOf("use", "when", "routine", "apply", "step")
    return report.blocks.firstNotNullOfOrNull { block ->
        when (block) {
            is ReportBlock.Chapter ->
                block.summary?.takeIf { s -> s.isNotBlank() && cue.any { block.title.contains(it, true) } }
            is ReportBlock.Aspect ->
                block.summary?.takeIf { s -> s.isNotBlank() && cue.any { block.aspect.title.contains(it, true) } }
            else -> null
        }
    }
}
