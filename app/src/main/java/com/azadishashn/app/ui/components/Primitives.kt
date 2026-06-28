package com.azadishashn.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azadishashn.app.ui.theme.Dim
import com.azadishashn.app.ui.theme.Elev
import com.azadishashn.app.ui.theme.Gold
import com.azadishashn.app.ui.theme.GoldBright
import com.azadishashn.app.ui.theme.GoldDim
import com.azadishashn.app.ui.theme.IdeologyTheme

/** The consistent grouped section — now a frosted glass pane. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    glow: Color? = null,
    accent: Color? = null,
    elevation: Dp = Elev.floating,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        elevation = elevation,
        glow = glow,
        accent = accent,
        contentPadding = Dim.cardPad,
        content = content,
    )
}

/** Full-width primary action: gradient fill, glow, press feedback. */
@Composable
fun PrimaryCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gold: Boolean = false,
) {
    val source = rememberPressSource()
    val primary = MaterialTheme.colorScheme.primary
    val brush = if (gold) {
        Brush.verticalGradient(listOf(GoldBright, GoldDim))
    } else {
        Brush.verticalGradient(listOf(primary, androidx.compose.ui.graphics.lerp(primary, Color.Black, 0.22f)))
    }
    val contentColor = if (gold) Color(0xFF2A1E00) else MaterialTheme.colorScheme.onPrimary
    val alpha = if (enabled) 1f else 0.45f
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        shadowElevation = if (enabled) 8.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(source),
    ) {
        Box(
            Modifier
                .background(brush)
                .padding(vertical = 16.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor.copy(alpha = alpha),
            )
        }
    }
}

/** Icon-only action button. */
@Composable
fun IconActionButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, tint: Color? = null) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = contentDescription, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Animated 0..10 strength gauge coloured by [ideology], with a marker tick at
 * the [required] threshold and a glow at the fill's leading edge. [strength] < 0
 * renders a neutral "no rating" state (offline).
 */
@Composable
fun StrengthMeter(strength: Int, required: Int, ideology: String, modifier: Modifier = Modifier) {
    val brand = IdeologyTheme.of(ideology).brand
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    if (strength < 0) {
        Column(modifier.fillMaxWidth()) {
            Text(
                "Self-tagged (offline) — no strength rating",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(track),
            )
        }
        return
    }
    val fraction by animateFloatAsState(
        targetValue = (strength.coerceIn(0, 10)) / 10f,
        animationSpec = tween(800),
        label = "strength",
    )
    val reqFraction = (required.coerceIn(0, 10)) / 10f
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Argument strength",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$strength / 10  ·  needs $required",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(track),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Brush.horizontalGradient(listOf(brand.copy(alpha = 0.6f), brand))),
            )
            BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight()) {
                val x = maxWidth * reqFraction
                Box(
                    Modifier
                        .padding(start = x)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.onSurface),
                )
            }
        }
    }
}

/** Gold / silver / bronze disc for the top three; outlined disc otherwise. */
@Composable
fun RankMedallion(rank: Int, modifier: Modifier = Modifier) {
    val (c1, c2, fg) = when (rank) {
        1 -> Triple(Color(0xFFFFE08A), Color(0xFFE0A300), Color(0xFF3A2E00))
        2 -> Triple(Color(0xFFE6ECF2), Color(0xFFAEB6C0), Color(0xFF22272E))
        3 -> Triple(Color(0xFFF0B583), Color(0xFFC06E32), Color(0xFF2E1604))
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Box(
        modifier
            .size(36.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c1, c2)))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(rank.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg)
    }
}

/** A compact labelled metric tile (e.g. resource awards) — glassy. */
@Composable
fun StatTile(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(tint.copy(alpha = if (dark) 0.55f else 1f))
            .border(1.dp, Color.White.copy(alpha = if (dark) 0.12f else 0.4f), MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
