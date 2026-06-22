package com.quietdose.ui.analysis

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietdose.brain.analysis.AnalysisReport
import com.quietdose.brain.analysis.AnalysisSection
import com.quietdose.brain.analysis.ModelCapability
import com.quietdose.brain.analysis.ModelTier
import com.quietdose.brain.analysis.Severity
import com.quietdose.brain.analysis.StackAnalyzer
import com.quietdose.data.entity.ItemEntity
import com.quietdose.di.ServiceLocator
import com.quietdose.ui.icons.ItemIcon
import com.quietdose.ui.stack.FilledButton
import com.quietdose.ui.stack.StackTextField
import com.quietdose.ui.stack.TextButtonGhost
import com.quietdose.ui.stack.androidxClickable
import com.quietdose.ui.theme.Accent
import com.quietdose.ui.theme.Done
import com.quietdose.ui.theme.Ink
import com.quietdose.ui.theme.Outline
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.Surface2
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid

private enum class Phase { INTENT, ANALYZING, REPORT }

/**
 * The contextual analysis a new item passes through before it joins the stack:
 * capture intent → analyze (grounded in the knowledge base, reasoned by the
 * on-device model when present) → read the synthesis → add (or save for review).
 * Calm, restrained "gamification": soft reveals, one accent, no confetti.
 *
 * Full-screen overlay; host it like ScanScreen. [item] is the candidate; [onAdd]
 * persists it, [onDismiss] discards.
 */
@Composable
fun AnalysisScreen(item: ItemEntity, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.repository(context) }
    val stack by remember { repo.observeAllItems() }
        .collectAsStateWithLifecycle(initialValue = emptyList<ItemEntity>())

    var phase by remember { mutableStateOf(Phase.INTENT) }
    var intent by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<AnalysisReport?>(null) }
    val tier = remember { ModelCapability.tier(context) }

    LaunchedEffect(phase) {
        if (phase == Phase.ANALYZING) {
            report = runCatching {
                StackAnalyzer.analyze(context, item.name, item.category, stack, intent.ifBlank { null })
            }.getOrNull()
            phase = Phase.REPORT
        }
    }

    Box(Modifier.fillMaxSize().background(Ink).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Header(item = item, tier = tier, onClose = onDismiss)
            when (phase) {
                Phase.INTENT -> IntentStep(
                    item = item,
                    intent = intent,
                    onIntent = { intent = it },
                    onContinue = { phase = Phase.ANALYZING },
                    onSkip = { phase = Phase.ANALYZING },
                )
                Phase.ANALYZING -> AnalyzingStep(item)
                Phase.REPORT -> {
                    val r = report
                    if (r == null) {
                        ErrorStep(onAdd = onAdd, onDismiss = onDismiss)
                    } else {
                        ReportStep(item = item, report = r, onAdd = onAdd, onDismiss = onDismiss)
                    }
                }
            }
        }
    }
}

/* ------------------------------- Header ------------------------------- */

@Composable
private fun Header(item: ItemEntity, tier: ModelTier, onClose: () -> Unit) {
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
    intent: String,
    onIntent: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val quick = listOf("Longevity", "Sleep", "Energy", "Recovery", "Deficiency", "Doctor advised", "Skin & hair", "Focus")
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        ItemHero(item)
        Spacer(Modifier.height(24.dp))
        Text("Why are you adding this?", style = MaterialTheme.typography.headlineSmall, color = TextHigh)
        Text(
            "A line of context sharpens the analysis. Optional.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMid,
        )
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.forEach { q ->
                val on = intent.contains(q, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (on) Accent.copy(alpha = 0.20f) else Surface2)
                        .androidxClickable { onIntent(if (on) intent else listOf(intent, q).filter { it.isNotBlank() }.joinToString(", ")) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(q, style = MaterialTheme.typography.labelLarge, color = if (on) Accent else TextMid)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        StackTextField(value = intent, onValueChange = onIntent, placeholder = "e.g. low ferritin, advised by my doctor", singleLine = false)
        Spacer(Modifier.height(24.dp))
        FilledButton(label = "Analyze", accent = Accent, modifier = Modifier.fillMaxWidth(), onClick = onContinue)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButtonGhost("Skip", color = TextLow, onClick = onSkip)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/* ------------------------------ Analyzing ------------------------------ */

@Composable
private fun AnalyzingStep(item: ItemEntity) {
    val steps = listOf("Grounding in references…", "Checking your stack…", "Weighing the evidence…", "Synthesizing…")
    val t = rememberInfiniteTransition(label = "an")
    val a by t.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(72.dp).clip(CircleShape).background(Accent.copy(alpha = 0.12f)).graphicsLayer { alpha = a },
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Accent, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Analyzing ${item.name}", style = MaterialTheme.typography.titleLarge, color = TextHigh)
        Spacer(Modifier.height(16.dp))
        steps.forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMid)
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(20.dp))
        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

/* ------------------------------- Report ------------------------------- */

@Composable
private fun ReportStep(item: ItemEntity, report: AnalysisReport, onAdd: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            ItemHero(item)
            Spacer(Modifier.height(16.dp))
            report.sections.forEach { section ->
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    SectionCard(section)
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(6.dp))
            SynthesisCard(report)
            Spacer(Modifier.height(20.dp))
        }
        Column(
            Modifier.fillMaxWidth().background(Surface1).navigationBarsPadding().padding(16.dp),
        ) {
            FilledButton(label = "Add to routine", accent = Accent, modifier = Modifier.fillMaxWidth(), onClick = onAdd)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButtonGhost("Not now", color = TextLow, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun SectionCard(section: AnalysisSection) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1).padding(16.dp),
    ) {
        Text(section.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextLow)
        Spacer(Modifier.height(8.dp))
        section.lines.forEach { line ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(severityColor(line.severity)))
                Spacer(Modifier.width(10.dp))
                Text(line.text, style = MaterialTheme.typography.bodyMedium, color = TextMid)
            }
        }
    }
}

@Composable
private fun SynthesisCard(report: AnalysisReport) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Accent.copy(alpha = 0.10f)).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(report.synthesis.verdict, style = MaterialTheme.typography.titleLarge, color = TextHigh)
        }
        Spacer(Modifier.height(8.dp))
        Text(report.synthesis.rationale, style = MaterialTheme.typography.bodyLarge, color = TextMid)
        report.synthesis.placement?.let {
            Spacer(Modifier.height(10.dp))
            Labeled("Place it", it)
        }
        if (report.synthesis.dependencies.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Labeled("Pair with", report.synthesis.dependencies.joinToString(", "))
        }
        if (report.synthesis.cautions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Labeled("Mind", report.synthesis.cautions.joinToString("; "))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (report.byModel) "Reasoned on-device, grounded in references." else "From the on-device knowledge base.",
            style = MaterialTheme.typography.labelSmall,
            color = TextLow,
        )
    }
}

@Composable
private fun Labeled(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("$label  ", style = MaterialTheme.typography.labelMedium, color = Accent, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextHigh)
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
private fun ErrorStep(onAdd: () -> Unit, onDismiss: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Text("Couldn't analyze this one", style = MaterialTheme.typography.titleLarge, color = TextHigh)
        Spacer(Modifier.height(8.dp))
        Text("You can still add it and refine later.", style = MaterialTheme.typography.bodyMedium, color = TextMid)
        Spacer(Modifier.height(24.dp))
        FilledButton(label = "Add anyway", accent = Accent, modifier = Modifier.fillMaxWidth(), onClick = onAdd)
        Spacer(Modifier.height(8.dp))
        TextButtonGhost("Not now", color = TextLow, onClick = onDismiss)
    }
}

private fun severityColor(s: Severity): Color = when (s) {
    Severity.GOOD -> Done
    Severity.NEUTRAL -> Outline
    Severity.CAUTION -> Accent
}
