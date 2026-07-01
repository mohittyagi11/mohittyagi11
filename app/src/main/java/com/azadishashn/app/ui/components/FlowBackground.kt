package com.azadishashn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The cinematic field. In dark mode this is the "diffraction glass" light field:
 * glowing organic ideology contours over a deep base (static layer), plus a
 * central light orb and a diagonal laser streak that gently animate (thin overlay
 * layer). Splitting static from animated keeps the many contour paths off the
 * per-frame path so scrolling stays smooth. Light mode stays the clean paper
 * background. Background-only.
 */
@Composable
fun FlowBackground(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val background = MaterialTheme.colorScheme.background
    val phase = rememberDiffractionPhase()

    Box(modifier) {
        // Static base — no animation state read, so drawn once.
        Canvas(Modifier.fillMaxSize()) {
            if (dark) drawDiffractionBase() else drawRect(background)
        }
        // Animated light — only the orb + streak repaint each frame.
        if (dark) {
            Canvas(Modifier.fillMaxSize()) { drawDiffractionLight(phase) }
        }
    }
}
