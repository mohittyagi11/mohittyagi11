package com.azadishashn.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.azadishashn.app.data.Lessons
import com.azadishashn.app.game.GameViewModel
import com.azadishashn.app.model.Ideologies
import com.azadishashn.app.model.OptionCard
import com.azadishashn.app.ui.components.Avatar
import com.azadishashn.app.ui.components.AzadiScaffold
import com.azadishashn.app.ui.components.CoachMark
import com.azadishashn.app.ui.components.GeneratingView
import com.azadishashn.app.ui.components.drawDiffractionBase
import com.azadishashn.app.ui.components.IconActionButton
import com.azadishashn.app.ui.components.IdeologyBadge
import com.azadishashn.app.ui.components.GlassChip
import com.azadishashn.app.ui.components.IdeologyChip
import com.azadishashn.app.ui.components.IdeologyDot
import com.azadishashn.app.ui.components.SectionCard
import com.azadishashn.app.ui.components.LiveBarRow
import com.azadishashn.app.ui.components.TableEntry
import com.azadishashn.app.ui.components.PrimaryCta
import com.azadishashn.app.ui.components.ScenarioStory
import com.azadishashn.app.ui.components.TurnProgress
import com.azadishashn.app.ui.components.Collapsible
import com.azadishashn.app.ui.components.LangChips
import com.azadishashn.app.ui.components.causal.PathsFanOut
import com.azadishashn.app.ui.theme.Dim
import com.azadishashn.app.ui.theme.IdeologyTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay

/**
 * The Champion's Court turn: QUESTION → ANSWER → (judge rules the topic) →
 * CHALLENGE window (the cause's Champion rises or waives) → REBUTTAL →
 * (judge qualifies + posts the wager) → WAGER (accept or compromise) → CLOSER.
 */
private enum class RoundPhase { QUESTION, ANSWER, CHALLENGE, REBUTTAL, WAGER, CLOSER }

/**
 * A small countdown ring — debate pressure, not enforcement: it turns red and
 * holds at zero, and nothing is cut off. Resets whenever [seconds] or the
 * composition key changes (each phase gets its own).
 */
@Composable
private fun DebateTimer(seconds: Int, running: Boolean) {
    var left by remember(seconds) { mutableIntStateOf(seconds) }
    LaunchedEffect(seconds, running) {
        while (running && left > 0) {
            delay(1000)
            left--
        }
    }
    val frac = left / seconds.toFloat()
    val tint = when {
        frac > 0.5f -> MaterialTheme.colorScheme.tertiary
        frac > 0.2f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(44.dp)) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawArc(track, -90f, 360f, false, style = stroke, size = Size(size.width, size.height))
            drawArc(tint, -90f, 360f * frac, false, style = stroke, size = Size(size.width, size.height))
        }
        Text("$left", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
fun RoundScreen(vm: GameViewModel) {
    val s = vm.state
    val subtitle = when {
        s.current != null -> "${s.questioner?.name} asks → ${s.activePlayer?.name} answers"
        else -> "${s.questioner?.name} → ${s.activePlayer?.name}"
    }
    AzadiScaffold(
        title = "Round ${s.round}",
        subtitle = subtitle,
        actions = {
            IconActionButton(Icons.AutoMirrored.Filled.HelpOutline, "Playbook", vm::openPlaybook)
            IconActionButton(Icons.Filled.BarChart, "Dashboard", vm::openDashboard)
            IconActionButton(Icons.Filled.Settings, "Settings", vm::openSettings)
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = Dim.screenH),
        ) {
            when {
                // RoundBody overlays the loader itself while twisting/judging, so
                // the player's typed answer and phase survive a failed judge.
                s.current != null -> RoundBody(vm)
                s.loading -> GeneratingView(s.loadingKind, vm.context, s.streamHint)
                s.error != null -> ErrorBlock(vm)
                s.scandal != null -> ScandalCard(vm)
                vm.hasKey -> ThemePicker(vm)
                else -> GeneratingView(s.loadingKind, vm.context, s.streamHint)
            }
        }
    }
}

/** A skeleton from the player's own record — the press pack wants an answer NOW. */
@Composable
private fun ScandalCard(vm: GameViewModel) {
    val sc = vm.state.scandal ?: return
    var response by remember(sc) { mutableStateOf("") }
    val coachBudget = remember(sc) { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "BREAKING — SCANDAL",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
        CoachMark(
            Lessons.SCANDAL, vm::hasSeenLesson, vm::markLessonSeen, coachBudget,
            force = true,
        )
        Spacer(Modifier.height(Dim.tight))
        Text(
            "“${sc.headline}”",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        Spacer(Modifier.height(Dim.tight))
        Text(sc.story, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Dim.itemGap))
        Text(
            "The press demands: ${sc.question}",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dim.itemGap))
        OutlinedTextField(
            value = response,
            onValueChange = { response = it },
            label = { Text("${vm.state.activePlayer?.name}: your public response") },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Spacer(Modifier.height(Dim.itemGap))
        PrimaryCta(
            text = "Face the press",
            onClick = { vm.respondScandal(response) },
            enabled = response.isNotBlank(),
        )
        TextButton(onClick = vm::dismissScandal, modifier = Modifier.fillMaxWidth()) {
            Text("“No comment.” (the press smells blood: −3)")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePicker(vm: GameViewModel) {
    val s = vm.state
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // The fallout from a scandal response, still smouldering above the fold.
        s.scandalResult?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Dim.itemGap))
        }
        Text(
            "Pick a theme — or a few — then Generate. Skip the picks to roll a random one.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dim.sectionGap))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dim.tight),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val darkThemePicker = isSystemInDarkTheme()
            s.availableThemes.forEach { theme ->
                if (darkThemePicker) {
                    GlassChip(
                        label = theme,
                        selected = theme in s.selectedThemes,
                        onClick = { vm.toggleTheme(theme) },
                    )
                } else {
                    FilterChip(
                        selected = theme in s.selectedThemes,
                        onClick = { vm.toggleTheme(theme) },
                        label = { Text(theme) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Dim.sectionGap))
        OutlinedButton(
            onClick = vm::refreshThemes,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.height(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Refresh themes")
        }
        Spacer(Modifier.height(Dim.itemGap))
        PrimaryCta(
            text = when {
                s.selectedThemes.isEmpty() && s.prefetched != null -> "Surprise me — ready ⚡"
                s.selectedThemes.isEmpty() -> "Surprise me — generate"
                else -> "Generate question"
            },
            onClick = vm::generate,
        )
        Spacer(Modifier.height(Dim.sectionGap))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoundBody(vm: GameViewModel) {
    val s = vm.state
    val round = s.current!!
    // Keyed on the TITLE, not the round object: a mid-argument leak (twist keeps
    // the title) must not reset the phase or wipe the half-spoken argument.
    val roundKey = round.scenario.title
    var phase by remember(roundKey) { mutableStateOf(RoundPhase.QUESTION) }
    var argument by remember(roundKey) { mutableStateOf("") }
    var rebuttal by remember(roundKey) { mutableStateOf("") }
    var closer by remember(roundKey) { mutableStateOf("") }
    var voiceHint by remember(roundKey) { mutableStateOf<String?>(null) }
    // Which field the mic feeds: 0 = argument, 1 = rebuttal, 2 = closer.
    var voiceTarget by remember(roundKey) { mutableStateOf(0) }
    // First-encounter coach cards: at most 2 per question so a rule-dense turn
    // doesn't become a wall — the rest teach on their next occurrence.
    val coachBudget = remember(roundKey) { mutableIntStateOf(0) }

    // The Champion's Court drives the phase across VM roundtrips: a provisional
    // verdict with a standing challenger opens the CHALLENGE window; the court's
    // qualification arriving moves the trial to the WAGER decision.
    LaunchedEffect(s.pendingVerdict != null, s.challengeCategory != null) {
        if (s.pendingVerdict != null) {
            phase = if (s.challengeCategory == null) RoundPhase.CHALLENGE else RoundPhase.WAGER
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                when (voiceTarget) {
                    1 -> rebuttal = if (rebuttal.isBlank()) spoken else "$rebuttal $spoken"
                    2 -> closer = if (closer.isBlank()) spoken else "$closer $spoken"
                    else -> argument = if (argument.isBlank()) spoken else "$argument $spoken"
                }
            }
        }
    }
    fun startVoice(target: Int = 0) {
        voiceTarget = target
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, vm.speechTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, vm.speechTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your answer")
        }
        try {
            voiceHint = null
            speechLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            voiceHint = "No voice-input app found — type your argument instead."
        }
    }

    // -- The tripwire watchers, alive through the WHOLE argument --------------
    // Hoisted out of the ANSWER branch: moving to the rebuttal or closer must
    // not defuse an armed trap (timebombs used to fizzle for fast answerers).
    // The word watcher hears the answerer's closer too; resolve() (loading)
    // finally stands the watchers down.
    val arguing = phase != RoundPhase.QUESTION && !s.loading
    val haptics = LocalHapticFeedback.current
    if (s.tripwireType == "word" && !s.tripwireFired && arguing) {
        LaunchedEffect(argument, closer) {
            // Any significant word of the armed text detonates — arming a
            // whole sentence watches each of its key words.
            if (com.azadishashn.app.data.Tripwire.matches(s.tripwireWord, "$argument $closer")) {
                vm.fireTripwire()
            }
        }
    }
    if (s.tripwireType == "time" && !s.tripwireFired && arguing) {
        val fuse = remember(roundKey) { (15..50).random() }
        LaunchedEffect(roundKey) {
            delay(fuse * 1000L)
            vm.fireTripwire()
        }
    }
    if (s.tripwireFired) {
        LaunchedEffect(roundKey) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Roles + turn progress: who asks whom, and where we are in the round.
        run {
            val n = s.players.size
            val starterIdx = s.players.indexOfFirst { it.id == s.starterId }.coerceAtLeast(0)
            val turnInRound = if (n > 0) ((s.activeIndex - starterIdx + n) % n) + 1 else 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 📣 = championed causes, worn next to each face: the table
                // always sees who holds standing to cross-examine on what.
                s.questioner?.let {
                    Avatar(it.name, seed = it.id, size = 28.dp)
                    if (vm.hasKey) ChampionBadge(vm.championsOf(it.id))
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "asks",
                    modifier = Modifier.padding(horizontal = 6.dp).height(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.activePlayer?.let {
                    Avatar(it.name, seed = it.id, size = 28.dp)
                    if (vm.hasKey) ChampionBadge(vm.championsOf(it.id))
                }
                Spacer(Modifier.weight(1f))
                TurnProgress(total = n, current = turnInRound)
            }
        }
        Spacer(Modifier.height(Dim.tight))
        Text(
            if (s.usingOffline) "Offline deck" else "Live · ${vm.model}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Nation crisis fronts — the ideology that owns each gets +1 strength.
        run {
            val n = s.nation
            val fronts = listOf(
                "ECONOMY" to n.economy, "LIBERTY" to n.liberty,
                "STABILITY" to n.stability, "TRUST" to n.trust,
            ).filter { it.second < 25 }
            if (fronts.isNotEmpty()) {
                Text(
                    "⚠ ${fronts.joinToString(" · ") { it.first }} CRISIS — the nation demands answers " +
                        "(its ideology argues at +1 strength)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                CoachMark(Lessons.NATION, vm::hasSeenLesson, vm::markLessonSeen, coachBudget, highlight = 0)
            }
        }
        Spacer(Modifier.height(Dim.tight))

        var displayLang by remember(round) { mutableStateOf(vm.language) }
        val entry = round.narration.firstOrNull { it.lang == displayLang }
        val translated = entry != null && displayLang != vm.language
        val speakText: (String) -> String = { lang ->
            val e = round.narration.firstOrNull { it.lang == lang }
            val chosen = if (vm.prefersDevanagari(lang)) e?.speak else e?.text
            chosen?.takeIf { it.isNotBlank() }
                ?: e?.text?.takeIf { it.isNotBlank() }
                ?: run {
                    val dim = round.scenario.dimension.takeIf { it.isNotBlank() }?.let { "$it. " } ?: ""
                    "$dim${round.scenario.title}. ${round.scenario.situation}  ${round.dilemma.question}"
                }
        }
        LangChips(vm.readLangs, displayLang, { displayLang = it }, Modifier.padding(bottom = Dim.tight))
        ScenarioStory(
            round = round,
            isReading = vm.isReading,
            langs = listOf(displayLang),
            textFor = speakText,
            onPlay = { lang, text -> vm.readOut(text, lang) },
            onStop = vm::stopReadOut,
            titleText = if (translated) entry!!.title.ifBlank { round.scenario.title } else round.scenario.title,
            situationText = if (translated) entry!!.text else round.scenario.situation,
            questionText = if (translated) "" else round.dilemma.question,
        )

        // The turn's weather, one wrapping chip row: who's watching, the match
        // point, the mood's push and pull. Same facts, a third of the height —
        // the WHY behind each number lives in TONIGHT'S BAR below.
        run {
            val counts = s.activePlayer?.counts.orEmpty()
            val mp = counts.entries.firstOrNull { (_, c) -> c == 1 || c == 3 || c == 5 }
            val m = round.mood?.takeIf { it.favors.isNotBlank() }
            if (round.blocs.isNotEmpty() || mp != null || m != null) {
                Spacer(Modifier.height(Dim.tight))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    round.blocs.forEach { bloc ->
                        InfoChip("👁 $bloc", MaterialTheme.colorScheme.tertiary)
                    }
                    if (mp != null) {
                        InfoChip(
                            "⚡ MATCH POINT · L${listOf(2, 4, 6).indexOf(mp.value + 1) + 1} ${mp.key}",
                            IdeologyTheme.of(mp.key).brand,
                        )
                    }
                    if (m != null) {
                        InfoChip("▼ ${m.favors} rides the wind", IdeologyTheme.of(m.favors).brand)
                        InfoChip("▲ ${m.suspects} fights it", IdeologyTheme.of(m.suspects).brand)
                    }
                }
                if (mp != null) {
                    CoachMark(Lessons.MATCHPOINT, vm::hasSeenLesson, vm::markLessonSeen, coachBudget)
                }
                if (m != null) {
                    CoachMark(Lessons.MOOD, vm::hasSeenLesson, vm::markLessonSeen, coachBudget)
                }
            }
        }
        // TONIGHT'S BAR — the live number each line demands of the answerer,
        // the exact math the verdict will use, visible through every phase.
        // Public info; nobody argues blind. Tiles pulse until their news
        // (first look, a moved bar, a crisis landing) is tapped-acknowledged.
        if (vm.hasKey) {
            Spacer(Modifier.height(Dim.tight))
            LiveBarRow(
                bars = vm.liveBars(),
                forName = s.activePlayer?.name.orEmpty(),
                playerId = s.activePlayer?.id ?: -1,
                crisisTarget = s.crisisTarget.takeIf { s.tripwireFired && it.isNotBlank() },
                acked = vm.ackedBars,
                onAck = vm::acknowledgeBar,
                table = s.players.map {
                    TableEntry(it.id, it.name, vm.championsOf(it.id), vm.liveBarsFor(it))
                },
            )
        }

        when (phase) {
            RoundPhase.QUESTION -> {
                Spacer(Modifier.height(Dim.sectionGap))
                // Yesterday's front page keeps the story rolling into tonight.
                s.news.lastOrNull()?.let { n ->
                    Text(
                        "LAST NIGHT'S PAPERS — ${n.outlet.uppercase()}: “${n.headline}”",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.height(Dim.tight))
                }
                Text(
                    "${s.questioner?.name}: read this out to ${s.activePlayer?.name}. " +
                        "Twist it to make the easy answer costly, or change it for a fresh scenario.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Dim.itemGap))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dim.tight)) {
                    if (vm.hasKey) {
                        OutlinedButton(
                            onClick = vm::twist,
                            enabled = s.twistsUsedThisTurn < 2,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Twist (${2 - s.twistsUsedThisTurn})")
                        }
                    }
                    OutlinedButton(
                        onClick = vm::changeQuestion,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Change")
                    }
                }
                s.error?.let { ErrorLine(it) }

                // The questioner's ambush: secretly arm a mid-argument crisis.
                if (vm.hasKey) {
                    Spacer(Modifier.height(Dim.itemGap))
                    if (s.tripwireType == null) {
                        CoachMark(Lessons.TRIPWIRE_ARM, vm::hasSeenLesson, vm::markLessonSeen, coachBudget)
                        Collapsible(
                            "Arm a tripwire (${s.questioner?.name} only — secret)",
                            Icons.Filled.AutoAwesome,
                            initiallyExpanded = false,
                        ) {
                            Column {
                                Text(
                                    "A crisis will ambush ${s.activePlayer?.name} MID-ARGUMENT, aimed at " +
                                        "their strongest ideology. Hold the line through it and clear the " +
                                        "bar → +1 bonus resource. Argue it and fumble → the nation takes the hit.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(Dim.tight))
                                var landmine by remember(roundKey) { mutableStateOf("") }
                                OutlinedTextField(
                                    value = landmine,
                                    onValueChange = { landmine = it },
                                    label = { Text("Landmine — a word or sentence; ANY key word fires it") },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dim.tight)) {
                                    OutlinedButton(
                                        onClick = { vm.armTripwire("word", landmine) },
                                        enabled = landmine.isNotBlank(),
                                        shape = MaterialTheme.shapes.large,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Arm landmine") }
                                    OutlinedButton(
                                        onClick = { vm.armTripwire("time") },
                                        shape = MaterialTheme.shapes.large,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Arm timebomb") }
                                }
                            }
                        }
                    } else {
                        val watching = if (s.tripwireType == "word") {
                            com.azadishashn.app.data.Tripwire.watchedWords(s.tripwireWord).size
                        } else 0
                        Text(
                            if (watching > 0) {
                                "Tripwire armed ✓ — watching $watching word${if (watching == 1) "" else "s"} (keep it secret)"
                            } else {
                                "Tripwire armed ✓ (keep it secret)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                if (round.paths.isNotEmpty()) {
                    Spacer(Modifier.height(Dim.itemGap))
                    Collapsible("Compare the four paths", Icons.Filled.AccountTree, initiallyExpanded = false) {
                        PathsFanOut(paths = round.paths, question = round.dilemma.question, reveal = false)
                    }
                }

                Spacer(Modifier.height(Dim.sectionGap))
                PrimaryCta(
                    text = "Pass to ${s.activePlayer?.name} to answer",
                    onClick = { phase = RoundPhase.ANSWER },
                )
                Spacer(Modifier.height(Dim.sectionGap))
            }

            RoundPhase.ANSWER -> {
                Spacer(Modifier.height(Dim.sectionGap))

                // The whip's sealed envelope — press & HOLD so no neighbour can peek.
                if (s.assignedIdeology != null && vm.hasKey) {
                    var peek by remember(roundKey) { mutableStateOf(false) }
                    val brand = IdeologyTheme.of(s.assignedIdeology).brand
                    Text(
                        if (peek) "📜 THE WHIP DEMANDS THE ${s.assignedIdeology.uppercase()} LINE. " +
                            "Obey: +3 poll, +1 ${Ideologies.resourceOf(s.assignedIdeology)}. " +
                            "Rebel at strength 7+: +5 poll, glory. Rebel under 7: −4 poll — the party remembers."
                        else "📜 ${s.activePlayer?.name} only: the party whip has instructions — press & hold to read",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (peek) brand else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(roundKey) {
                                detectTapGestures(
                                    onPress = {
                                        peek = true
                                        tryAwaitRelease()
                                        peek = false
                                    },
                                )
                            }
                            .padding(vertical = 6.dp),
                    )
                    // The choice is NOW — teach the whip before they argue (forced
                    // past the budget; the moment won't come back).
                    CoachMark(
                        Lessons.WHIP, vm::hasSeenLesson, vm::markLessonSeen, coachBudget,
                        force = true,
                    )
                    Spacer(Modifier.height(Dim.tight))
                }

                // BREAKING — the tripwire crisis, targeted at the answerer's base.
                if (s.tripwireFired && s.crisisLine.isNotBlank()) {
                    Text(
                        "⚡ ${s.crisisLine}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "TARGETS ${s.crisisTarget.uppercase()} — hold that line AND clear the bar → " +
                            "+1 ${Ideologies.resourceOf(s.crisisTarget)}, +2 poll. Argue it and fumble → " +
                            "its meter −3, poll −3. Swerve away → no hit, but the record remembers.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CoachMark(
                        Lessons.TRIPWIRE_FIRE, vm::hasSeenLesson, vm::markLessonSeen, coachBudget,
                        force = true,
                    )
                    Spacer(Modifier.height(Dim.tight))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${s.activePlayer?.name}: answer in your own words. " +
                            "Claude awards +2 to the ideology your answer most embodies and +1 to the next.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    DebateTimer(seconds = 90, running = true)
                }
                Spacer(Modifier.height(Dim.itemGap))

                Button(
                    onClick = { startVoice(0) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Speak your answer")
                }
                voiceHint?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = argument,
                    onValueChange = { argument = it },
                    label = { Text("…or type / edit") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    minLines = 2,
                )


                if (!vm.hasKey) {
                    Spacer(Modifier.height(Dim.itemGap))
                    Text(
                        "Offline — tag your MAIN ideology (+2):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Dim.tight))
                    round.options.forEach { option ->
                        OptionRow(option, selected = s.championedOptionId == option.id) {
                            vm.champion(option.id)
                        }
                        Spacer(Modifier.height(Dim.tight))
                    }
                    Text(
                        "…and a secondary lean (+1):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Dim.tight))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dim.tight)) {
                        round.options.filter { it.id != s.championedOptionId }.forEach { option ->
                            IdeologyChip(
                                name = option.ideology,
                                selected = s.secondaryOptionId == option.id,
                                onClick = { vm.championSecondary(option.id) },
                            )
                        }
                    }
                }

                s.error?.let { ErrorLine(it) }

                Spacer(Modifier.height(Dim.itemGap))
                Text(
                    "Real-world note: ${round.dilemma.realWorldNote}",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(Dim.sectionGap))
                // Resting the case sends the answer to the judge, who names the
                // topic — and with it, who (if anyone) holds standing to
                // cross-examine: the cause's Champion, not the questioner.
                PrimaryCta(
                    text = if (vm.hasKey) "Rest my case — to the judge" else "Resolve",
                    onClick = {
                        when {
                            // The backstop: an unexploded timebomb detonates the
                            // moment they stand up — the phone rings mid-motion.
                            // Resting is interrupted once; the next tap proceeds.
                            vm.hasKey && s.tripwireType == "time" && !s.tripwireFired -> vm.fireTripwire()
                            else -> vm.submitAnswer(argument)
                        }
                    },
                    enabled = if (vm.hasKey) argument.isNotBlank() else s.championedOptionId != null,
                )
                TextButton(
                    onClick = { phase = RoundPhase.QUESTION },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Re-read the question") }
                Spacer(Modifier.height(Dim.sectionGap))
            }

            RoundPhase.CHALLENGE -> {
                CoachMark(
                    Lessons.CROSSEXAM, vm::hasSeenLesson, vm::markLessonSeen, coachBudget,
                    force = true,
                )
                s.error?.let { ErrorLine(it) }
                val v = s.pendingVerdict
                val challenger = s.players.firstOrNull { it.id == s.challengerId }
                if (v != null && challenger != null) {
                    val cause = v.primaryIdeology
                    // The topic reveal — everyone learns whose turf tonight's answer was on.
                    Spacer(Modifier.height(Dim.itemGap))
                    Text(
                        "THE JUDGE RULES: this answer served the ${cause.uppercase()} cause — " +
                            "strength ${v.strength}/10.",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = IdeologyTheme.of(cause).brand,
                    )
                    HandoffCard(
                        toName = challenger.name,
                        role = "📣 CHAMPION OF THE ${cause.uppercase()} CAUSE",
                        brief = "You hold the most $cause cards at this table — on this topic, " +
                            "YOU are the press. Rise and cross-examine ${s.activePlayer?.name} " +
                            "(20 seconds), or waive and let the ruling stand.",
                        cta = "Rise — cross-examine",
                        onTake = { phase = RoundPhase.REBUTTAL },
                        onSkip = { vm.waiveChallenge() },
                        skipLabel = "Waive — let the ruling stand",
                        ctaNote = "The court will QUALIFY your attack and post its wager " +
                            "before anything settles. Cheap shots get priced as cheap shots.",
                    )
                }
            }

            RoundPhase.REBUTTAL -> {
                val challenger = s.players.firstOrNull { it.id == s.challengerId }
                Spacer(Modifier.height(Dim.sectionGap))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${challenger?.name}: 20 seconds to tear the answer apart. Cite their record, " +
                            "or their betrayal of the cause — substance survives, smears backfire.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    DebateTimer(seconds = 20, running = true)
                }
                Spacer(Modifier.height(Dim.itemGap))
                Button(
                    onClick = { startVoice(1) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Speak the cross-examination")
                }
                OutlinedTextField(
                    value = rebuttal,
                    onValueChange = { rebuttal = it },
                    label = { Text("…or type it") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                s.error?.let { ErrorLine(it) }
                Spacer(Modifier.height(Dim.sectionGap))
                PrimaryCta(
                    text = "Submit — the court qualifies the challenge",
                    onClick = { vm.submitRebuttal(rebuttal) },
                    enabled = rebuttal.isNotBlank(),
                )
                TextButton(
                    onClick = { vm.waiveChallenge() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Withdraw — let the ruling stand") }
                Spacer(Modifier.height(Dim.sectionGap))
            }

            RoundPhase.WAGER -> {
                s.error?.let { ErrorLine(it) }
                val v = s.pendingVerdict
                val challenger = s.players.firstOrNull { it.id == s.challengerId }
                val category = s.challengeCategory
                if (v != null && challenger != null && category != null) {
                    WagerCard(
                        category = category,
                        conditions = s.challengeConditions.orEmpty(),
                        answererName = s.activePlayer?.name.orEmpty(),
                        challengerName = challenger.name,
                        cause = v.primaryIdeology,
                        secondary = v.secondaryIdeology,
                        terms = vm.wagerTerms(category, v.primaryIdeology),
                        onAccept = { phase = RoundPhase.CLOSER },
                        onCompromise = { vm.compromise() },
                    )
                }
            }

            RoundPhase.CLOSER -> {
                val challenger = s.players.firstOrNull { it.id == s.challengerId }
                Spacer(Modifier.height(Dim.sectionGap))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${s.activePlayer?.name}: the wager is accepted. 15 seconds to answer " +
                            "${challenger?.name}'s challenge — the court rules the whole exchange.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    DebateTimer(seconds = 15, running = true)
                }
                Spacer(Modifier.height(Dim.itemGap))
                Button(
                    onClick = { startVoice(2) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Speak the closer")
                }
                OutlinedTextField(
                    value = closer,
                    onValueChange = { closer = it },
                    label = { Text("…or type the closer") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                s.error?.let { ErrorLine(it) }
                Spacer(Modifier.height(Dim.sectionGap))
                PrimaryCta(
                    text = "To the ruling — the court weighs the clash",
                    onClick = { vm.acceptWager(closer) },
                )
                Spacer(Modifier.height(Dim.sectionGap))
            }
        }
    }

        if (s.loading) {
            val darkLoad = isSystemInDarkTheme()
            Box(Modifier.matchParentSize()) {
                // Cover the live round with the diffraction field (dark) instead of a
                // flat opaque block, so the loader sits on the same glassy light field.
                if (darkLoad) {
                    Canvas(Modifier.matchParentSize()) { drawDiffractionBase() }
                } else {
                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background))
                }
                GeneratingView(s.loadingKind, vm.context, s.streamHint)
            }
        }
    }
}

@Composable
private fun OptionRow(option: OptionCard, selected: Boolean, onClick: () -> Unit) {
    val v = IdeologyTheme.of(option.ideology)
    val container = if (selected) IdeologyTheme.container(option.ideology) else MaterialTheme.colorScheme.surfaceContainer
    val border = if (selected) BorderStroke(2.dp, v.brand) else null
    Card(
        onClick = onClick,
        border = border,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dim.cardPad)) {
            IdeologyBadge(option.ideology)
            Spacer(Modifier.height(Dim.tight))
            Text(option.label, style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}

/**
 * A pass-the-phone interstitial: who takes the floor, in what role, with what
 * brief — the previous speaker's words stay hidden behind it.
 */
@Composable
private fun HandoffCard(
    toName: String,
    role: String,
    brief: String,
    cta: String,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    skipLabel: String,
    ctaEnabled: Boolean = true,
    ctaNote: String = "",
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(role, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pass the phone to",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            toName,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            brief,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dim.sectionGap))
        PrimaryCta(text = "$cta — $toName", onClick = onTake, enabled = ctaEnabled)
        if (ctaNote.isNotBlank()) {
            Text(
                ctaNote,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = if (ctaEnabled) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(skipLabel) }
    }
}

/** 📣 + cause dots — the causes a player champions, worn next to their avatar. */
@Composable
private fun ChampionBadge(causes: List<String>) {
    if (causes.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 3.dp),
    ) {
        Text("📣", style = MaterialTheme.typography.labelSmall)
        causes.forEach {
            Spacer(Modifier.width(2.dp))
            IdeologyDot(it, size = 7.dp)
        }
    }
}

/**
 * The court's posted wager: the qualification of the challenge and the price of
 * either outcome, announced BEFORE the answerer decides — accept the trial, or
 * compromise (no wager paid, but the card deflects to the secondary).
 */
@Composable
private fun WagerCard(
    category: String,
    conditions: String,
    answererName: String,
    challengerName: String,
    cause: String,
    secondary: String,
    terms: Pair<String, String>,
    onAccept: () -> Unit,
    onCompromise: () -> Unit,
) {
    val tint = when (category) {
        "CONTRADICTION" -> MaterialTheme.colorScheme.error
        "HERESY" -> IdeologyTheme.of(cause).brand
        else -> MaterialTheme.colorScheme.tertiary
    }
    Column(Modifier.fillMaxWidth().padding(vertical = Dim.sectionGap)) {
        Text(
            "THE COURT QUALIFIES THE CHALLENGE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            category,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
        if (conditions.isNotBlank()) {
            Spacer(Modifier.height(Dim.tight))
            Text(
                conditions,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Dim.itemGap))
        SectionCard {
            Column {
                Text(
                    "THE WAGER — posted before trial",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                )
                Spacer(Modifier.height(Dim.tight))
                Text(
                    "If the card FALLS: ${terms.first}.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "If the card HOLDS: ${terms.second}.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(Dim.tight))
                Text(
                    "Either way, the clash makes the papers.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Dim.sectionGap))
        Text(
            "$answererName — your call. Face $challengerName's charge, or settle?",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Dim.itemGap))
        PrimaryCta(
            text = "Accept the wager — 15s to close",
            onClick = onAccept,
        )
        OutlinedButton(
            onClick = onCompromise,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Compromise — settle; card deflected to $secondary") }
        Text(
            "A compromise pays no wager, but the line is lost — and the dodge makes the papers.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A tiny tinted status chip for the turn's weather row. */
@Composable
private fun InfoChip(text: String, tint: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun ErrorLine(message: String) {
    Spacer(Modifier.height(Dim.tight))
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ErrorBlock(vm: GameViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Couldn't reach Claude", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(vm.state.error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Dim.itemGap))
        PrimaryCta(text = "Retry", onClick = vm::retryRound)
        OutlinedButton(
            onClick = vm::useOfflineRound,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Use a bundled round instead") }
        TextButton(onClick = vm::openSettings, modifier = Modifier.fillMaxWidth()) { Text("Check API key") }
    }
}
