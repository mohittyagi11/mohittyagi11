package com.azadishashn.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azadishashn.app.model.Ideologies
import com.azadishashn.app.ui.theme.IdeologyTheme

/** A solid brand-coloured dot for an ideology (legends, inline markers). */
@Composable
fun IdeologyDot(name: String, size: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(IdeologyTheme.of(name).brand),
    )
}

/**
 * A pill badge: icon + ideology name (+ optional trailing count) on its tinted
 * container. The core unit of the ideology identity used across screens.
 */
@Composable
fun IdeologyBadge(
    name: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    showLabel: Boolean = true,
) {
    val v = IdeologyTheme.of(name)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(IdeologyTheme.container(name))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(v.brand),
            contentAlignment = Alignment.Center,
        ) {
            Icon(v.icon, contentDescription = null, tint = v.onBrand, modifier = Modifier.size(13.dp))
        }
        if (showLabel) {
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "· ${v.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (count != null) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** A selectable branded chip (offline secondary-lean picker). */
@Composable
fun IdeologyChip(name: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val v = IdeologyTheme.of(name)
    val bg = if (selected) IdeologyTheme.container(name) else MaterialTheme.colorScheme.surface
    val borderColor = if (selected) v.brand else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IdeologyDot(name, size = 12.dp)
        Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Horizontal stacked proportion bar of a player's ideology cards — one
 * brand-coloured segment per non-zero ideology, width ∝ count. The fix for the
 * old cramped one-line standings.
 */
@Composable
fun IdeologyDistributionBar(counts: Map<String, Int>, modifier: Modifier = Modifier) {
    val total = Ideologies.NAMES.sumOf { counts[it] ?: 0 }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val grow by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "grow",
    )
    Row(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .graphicsLayer {
                scaleX = grow
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
    ) {
        if (total == 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Ideologies.NAMES.forEach { name ->
                val v = counts[name] ?: 0
                if (v > 0) {
                    Box(
                        Modifier
                            .weight(v.toFloat())
                            .fillMaxHeight()
                            .background(IdeologyTheme.of(name).brand),
                    )
                }
            }
        }
    }
}
