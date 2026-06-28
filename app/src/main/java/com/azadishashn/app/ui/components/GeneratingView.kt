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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.azadishashn.app.ui.theme.GoldBright
import com.azadishashn.app.ui.theme.GoldDim
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
        SealLoader(size = 140.dp)
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
private fun SealLoader(size: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "seal")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val seeds = IdeologyTheme.ALL.map { it.brand }
    val faint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val m = this.size.minDimension
            // concentric guide rings
            drawCircle(faint.copy(alpha = 0.12f), radius = m * 0.46f, center = Offset(cx, cy), style = Stroke(1.2f))
            drawCircle(faint.copy(alpha = 0.08f), radius = m * 0.34f, center = Offset(cx, cy), style = Stroke(1f))
            // faint ideology dots on the outer ring (colour cue, not the focus)
            seeds.forEachIndexed { i, color ->
                val a = i * (2f * Math.PI.toFloat() / seeds.size) - Math.PI.toFloat() / 2f
                drawCircle(color.copy(alpha = 0.5f), radius = m * 0.018f, center = Offset(cx + m * 0.46f * cos(a), cy + m * 0.46f * sin(a)))
            }
            // rotating gold sweep
            val r = m * 0.42f
            rotate(angle, pivot = Offset(cx, cy)) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, GoldDim, GoldBright, Color.Transparent), center = Offset(cx, cy)),
                    startAngle = 0f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(2 * r, 2 * r),
                    style = Stroke(width = m * 0.03f, cap = StrokeCap.Round),
                )
            }
        }
        SealMark(
            size = size * 0.4f,
            modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse },
        )
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
