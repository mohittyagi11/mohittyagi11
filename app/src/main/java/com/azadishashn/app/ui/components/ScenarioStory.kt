package com.azadishashn.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.model.RoundData
import kotlinx.coroutines.delay

/**
 * Tells the scenario as an unfolding story: the dossier reveals in staggered
 * springy beats, capped by a highlighted dilemma, with a prominent "Narrate the
 * story" control that shows a live waveform while speaking.
 */
@Composable
fun ScenarioStory(
    round: RoundData,
    isReading: Boolean,
    onNarrate: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Reveal beats: 1 ribbon, 2 title, 3 situation, 4 dilemma, 5 narrate.
    var step by remember(round) { mutableIntStateOf(0) }
    LaunchedEffect(round) {
        step = 0
        repeat(5) {
            delay(if (it == 0) 120 else 320)
            step = it + 1
        }
    }

    SectionCard(modifier) {
        Beat(visible = step >= 1) {
            if (round.scenario.dimension.isNotBlank()) {
                Text(
                    round.scenario.dimension.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.height(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${round.scenario.setting} · ${round.scenario.era}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        Beat(visible = step >= 2) {
            Spacer(Modifier.height(10.dp))
            Text(round.scenario.title, style = MaterialTheme.typography.headlineMedium)
        }

        Beat(visible = step >= 3) {
            Spacer(Modifier.height(12.dp))
            Text(
                round.scenario.situation,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Beat(visible = step >= 4) {
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.secondary),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    round.dilemma.question,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Beat(visible = step >= 5) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { if (isReading) onStop() else onNarrate() },
                shape = MaterialTheme.shapes.large,
                colors = if (isReading) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isReading) {
                    Waveform(color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(10.dp))
                    Text("Narrating… tap to stop")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.height(18.dp))
                } else {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Narrate the story")
                }
            }
        }
    }
}

@Composable
private fun Beat(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        ) { it / 3 },
    ) {
        Column { content() }
    }
}
