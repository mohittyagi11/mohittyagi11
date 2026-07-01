package com.azadishashn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The cinematic field. In dark mode this is the "diffraction glass" light field:
 * a central light orb, a diagonal laser streak, glowing ideology contours with
 * directional relief, and an edge vignette — all drawn in screen space so the
 * translucent black-glass panes above read as windows onto the light. In light
 * mode it stays the clean editorial paper background. GPU-cheap: one shared
 * infinite transition, no blur. Background-only.
 */
@Composable
fun FlowBackground(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val background = MaterialTheme.colorScheme.background
    val phase = rememberDiffractionPhase()

    Canvas(modifier) {
        if (dark) {
            drawDiffractionField(phase)
        } else {
            drawRect(background)
        }
    }
}
