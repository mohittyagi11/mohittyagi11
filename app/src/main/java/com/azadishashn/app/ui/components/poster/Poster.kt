package com.azadishashn.app.ui.components.poster

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azadishashn.app.ui.components.AppLogo
import com.azadishashn.app.ui.theme.Abyss
import com.azadishashn.app.ui.theme.DeepField
import com.azadishashn.app.ui.theme.GoldBright
import com.azadishashn.app.ui.theme.IdeologyTheme
import com.azadishashn.app.ui.theme.OverlineStyle
import kotlin.math.cos
import kotlin.math.sin

/** The four SHASN brand colours — the sunburst's wedges. */
private val SUNBURST_COLORS: List<Color> = IdeologyTheme.ALL.map { it.brand }

/**
 * The poster motif — a radial sunburst of the four ideology colours glowing out
 * from the centre. Each wedge fades to transparent at the rim, so it reads as
 * rays of light rather than a flat pie. Carries the launcher icon's energy onto
 * any surface; tune [intensity] for backdrop vs. accent use.
 */
@Composable
fun Sunburst(
    modifier: Modifier = Modifier,
    colors: List<Color> = SUNBURST_COLORS,
    rays: Int = 16,
    rotation: Float = 0f,
    intensity: Float = 0.5f,
) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val radius = size.maxDimension * 0.78f
        val step = 360f / rays
        for (i in 0 until rays) {
            val mid = rotation + i * step
            val col = colors[i % colors.size]
            val a0 = Math.toRadians((mid - step * 0.34f).toDouble())
            val a1 = Math.toRadians((mid + step * 0.34f).toDouble())
            val path = Path().apply {
                moveTo(c.x, c.y)
                lineTo(c.x + radius * cos(a0).toFloat(), c.y + radius * sin(a0).toFloat())
                lineTo(c.x + radius * cos(a1).toFloat(), c.y + radius * sin(a1).toFloat())
                close()
            }
            drawPath(
                path,
                Brush.radialGradient(
                    colors = listOf(col.copy(alpha = intensity), Color.Transparent),
                    center = c,
                    radius = radius,
                ),
            )
        }
    }
}

/**
 * A slowly-rotating [Sunburst] for ambient backdrops (poster heroes, ceremony).
 * One infinite transition; GPU-cheap.
 */
@Composable
fun RotatingSunburst(modifier: Modifier = Modifier, intensity: Float = 0.45f, periodMs: Int = 60000) {
    val t = rememberInfiniteTransition(label = "sunburst")
    val rot by t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "rot",
    )
    Sunburst(modifier, rotation = rot, intensity = intensity)
}

/**
 * The propaganda-poster hero — the illustrated fist emblem over a glowing
 * sunburst on a near-black ground, with a vignette scrim and bold poster
 * lettering. The app-wide signature, echoing the launcher icon.
 */
@Composable
fun PosterHero(
    title: String,
    kicker: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    emblemSize: Dp = 92.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Abyss, DeepField, Abyss))),
        )
        RotatingSunburst(Modifier.matchParentSize(), intensity = 0.42f)
        // Vignette so the centre stays legible and the rays fall off at the edges.
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.radialGradient(listOf(Color.Transparent, Abyss.copy(alpha = 0.86f)))),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLogo(size = emblemSize)
            Spacer(Modifier.height(16.dp))
            Text(kicker.uppercase(), style = OverlineStyle, color = GoldBright)
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * A one-colour [Sunburst] burst for the verdict ceremony — rays in the winning
 * ideology's colour radiating behind the awarded card.
 */
@Composable
fun PosterBurst(accent: Color, modifier: Modifier = Modifier, intensity: Float = 0.5f) {
    RotatingSunburstSingle(modifier, accent, intensity)
}

@Composable
private fun RotatingSunburstSingle(modifier: Modifier, accent: Color, intensity: Float) {
    val t = rememberInfiniteTransition(label = "burst")
    val rot by t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(90000, easing = LinearEasing), RepeatMode.Restart),
        label = "rot",
    )
    Sunburst(modifier, colors = listOf(accent), rays = 20, rotation = rot, intensity = intensity)
}
