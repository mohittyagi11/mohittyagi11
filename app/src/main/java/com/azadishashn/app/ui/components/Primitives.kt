package com.azadishashn.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.ui.theme.IdeologyTheme

/** The one consistent elevated card used everywhere a section is grouped. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** Full-width primary action. One per screen. */
@Composable
fun PrimaryCta(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) { Text(text) }
}

/** Icon-only action button (replaces the old emoji TextButtons). */
@Composable
fun IconActionButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, tint: Color? = null) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = contentDescription, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Animated 0..10 strength gauge coloured by [ideology], with a marker tick at
 * the [required] threshold. [strength] < 0 renders a neutral "no rating" state
 * (offline). Pure visualization of existing AwardResult fields.
 */
@Composable
fun StrengthMeter(strength: Int, required: Int, ideology: String, modifier: Modifier = Modifier) {
    val brand = IdeologyTheme.of(ideology).brand
    val track = MaterialTheme.colorScheme.surfaceVariant
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
        animationSpec = tween(700),
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
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(track),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(brand.copy(alpha = 0.75f), brand))),
            )
            // The required-threshold marker tick.
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
    val (bg, fg) = when (rank) {
        1 -> Color(0xFFFFD24A) to Color(0xFF3A2E00)
        2 -> Color(0xFFCBD2DA) to Color(0xFF22272E)
        3 -> Color(0xFFD9925A) to Color(0xFF2E1604)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(rank.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg)
    }
}

/** A compact labelled metric tile (e.g. resource awards). */
@Composable
fun StatTile(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(tint)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
