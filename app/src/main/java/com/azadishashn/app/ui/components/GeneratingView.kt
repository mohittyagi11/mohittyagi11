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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.azadishashn.app.ui.theme.IdeologyTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Space-filling branded loader: the four ideology orbs orbit a glowing core
 * while storytelling captions cycle, so generation/judging never feels static.
 */
@Composable
fun GeneratingView(kind: String?, context: String, modifier: Modifier = Modifier) {
    val captions = remember(kind, context) { captionsFor(kind, context) }
    var index by remember(captions) { mutableIntStateOf(0) }
    LaunchedEffect(captions) {
        while (true) {
            delay(2000)
            index = (index + 1) % captions.size
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OrbitLoader(size = 132.dp)
        Spacer(Modifier.height(36.dp))
        AnimatedContent(
            targetState = captions[index % captions.size],
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
            label = "caption",
        ) { caption ->
            Text(
                caption,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "one moment…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OrbitLoader(size: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "orbit")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val seeds = IdeologyTheme.ALL.map { it.brand }
    Canvas(Modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val orbit = this.size.minDimension * 0.32f
        val orb = this.size.minDimension * 0.12f
        // Glowing core.
        drawCircle(Color.White.copy(alpha = 0.18f), radius = orb * 1.5f * pulse, center = Offset(cx, cy))
        seeds.forEachIndexed { i, color ->
            val a = angle + i * (2f * Math.PI.toFloat() / seeds.size)
            val x = cx + orbit * cos(a)
            val y = cy + orbit * sin(a)
            // Trailing glow then the orb.
            drawCircle(color.copy(alpha = 0.18f), radius = orb * 1.6f, center = Offset(x, y))
            drawCircle(color, radius = orb * (if (i % 2 == 0) pulse else 2f - pulse), center = Offset(x, y))
        }
    }
}

private fun captionsFor(kind: String?, context: String): List<String> = when (kind) {
    "twist" -> listOf(
        "Twisting the card…",
        "Raising the stakes…",
        "Making the easy answer costly…",
    )
    "judge" -> listOf(
        "Weighing your argument…",
        "Reading between the lines…",
        "Consulting history…",
        "Naming the ideology…",
    )
    else -> listOf(
        "Researching the dilemma…",
        "Localising to $context…",
        "Drafting the four ideologies…",
        "Setting the scene…",
    )
}
