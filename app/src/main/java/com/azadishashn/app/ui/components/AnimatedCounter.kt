package com.azadishashn.app.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** A number that rolls up to its value (tabular figures via [style]). */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    durationMillis: Int = 600,
) {
    val shown by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis),
        label = "counter",
    )
    Text(shown.toString(), modifier = modifier, style = style, color = color)
}
