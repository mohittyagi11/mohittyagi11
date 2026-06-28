package com.azadishashn.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.azadishashn.app.ui.theme.GoldBright
import com.azadishashn.app.ui.theme.IdeologyTheme
import com.azadishashn.app.ui.theme.OverlineStyle
import kotlinx.coroutines.delay

/**
 * Space-filling branded loader: the raised fist clenches finger by finger over a
 * sunburst while full-sentence captions — shuffled so they don't repeat within a
 * wait — narrate what's happening, under a kicker and live ideology-colour dots.
 */
@Composable
fun GeneratingView(kind: String?, context: String, modifier: Modifier = Modifier) {
    val pool = remember(kind, context) { captionsFor(kind, context) }

    // Walk a shuffled order so no line repeats until the pool is exhausted; on
    // exhaustion reshuffle while avoiding an immediate repeat of the last line.
    var order by remember(pool) { mutableStateOf(pool.indices.shuffled()) }
    var pos by remember(pool) { mutableIntStateOf(0) }
    val caption = pool[order[pos % order.size]]

    LaunchedEffect(pool) {
        while (true) {
            delay(2600)
            if (pos + 1 >= order.size) {
                val last = order[pos % order.size]
                var next = pool.indices.shuffled()
                if (next.size > 1 && next.first() == last) next = next.drop(1) + next.first()
                order = next
                pos = 0
            } else {
                pos += 1
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FistLoader(size = 168.dp)
        Spacer(Modifier.height(36.dp))

        Text(kickerFor(kind), style = OverlineStyle, color = GoldBright)
        Spacer(Modifier.height(10.dp))
        AnimatedContent(
            targetState = caption,
            transitionSpec = {
                (fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 3 }) togetherWith
                    fadeOut(tween(240))
            },
            label = "caption",
        ) { line ->
            Text(
                line,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(20.dp))
        BreathingDots()
    }
}

/** Four ideology-coloured dots pulsing in sequence — a live "working" cue. */
@Composable
private fun BreathingDots() {
    val colors = IdeologyTheme.ALL.map { it.brand }
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEachIndexed { i, c ->
            val a by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(700, delayMillis = i * 150, easing = LinearEasing),
                    RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(Modifier.size(9.dp).clip(CircleShape).background(c.copy(alpha = a)))
        }
    }
}

private fun kickerFor(kind: String?): String = when (kind) {
    "twist" -> "RAISING THE STAKES"
    "judge" -> "DELIVERING THE VERDICT"
    else -> "DRAFTING THE SCENARIO"
}

private fun captionsFor(kind: String?, context: String): List<String> = when (kind) {
    "twist" -> listOf(
        "Twisting the knife — the easy answer just got expensive…",
        "Raising the stakes until someone has to blink…",
        "Rewriting the rules so comfort isn't an option…",
        "Pulling the safe ground out from under the choice…",
        "Forcing the cost of conviction into the open…",
        "Turning a clean decision into a real sacrifice…",
        "Adding the complication nobody wanted to face…",
        "Making the principled path the costly one…",
        "Tightening the screws on every comfortable answer…",
        "Daring you to hold the line when it bites…",
        "Loading the dilemma with a price you'll feel…",
        "Escalating until neutrality is no longer free…",
    )
    "judge" -> listOf(
        "Weighing your words against the cost of power…",
        "Reading between the lines for the ideology underneath…",
        "Consulting history for how this gamble has played before…",
        "Tracing the consequences three moves ahead…",
        "Naming the conviction your argument really served…",
        "Measuring conviction against the resources it spends…",
        "Separating the rhetoric from the real position…",
        "Auditing who pays and who gains under your call…",
        "Cross-examining your case like a tribunal…",
        "Mapping where this stance leads in the long run…",
        "Deciding which ideology your answer truly fed…",
        "Tallying the political capital your argument earned…",
    )
    else -> listOf(
        "Scouring the archives for a dilemma with no clean answer…",
        "Translating the crisis onto the streets of $context…",
        "Drawing the battle lines between the four ideologies…",
        "Weighing whose freedom pays for whose order…",
        "Stress-testing every side until only hard choices remain…",
        "Casting the players, the stakes, and the ticking clock…",
        "Hunting for the question that splits a room in two…",
        "Sharpening the trade-off until it genuinely hurts…",
        "Grounding the scenario in how power really moves…",
        "Letting principle and pragmatism collide…",
        "Setting the stage where conviction meets consequence…",
        "Framing four futures, each with its own price…",
    )
}
