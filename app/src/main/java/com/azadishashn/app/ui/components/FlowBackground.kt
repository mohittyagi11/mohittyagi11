package com.azadishashn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The cinematic field. In dark mode this is the "diffraction glass" light field —
 * glowing organic ideology contours over a deep base, a central light orb and a
 * diagonal laser streak. It is drawn ONCE and holds still: hoisted to the app
 * root behind the screen-swap animation, it never repaints per frame and is never
 * caught inside a transition, so screen changes and scrolling stay smooth. Light
 * mode is the clean paper background. Background-only.
 */
@Composable
fun FlowBackground(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val background = MaterialTheme.colorScheme.background
    Canvas(modifier) {
        if (dark) {
            drawDiffractionBase()
            drawDiffractionLight(0f)
        } else {
            drawRect(background)
        }
    }
}
