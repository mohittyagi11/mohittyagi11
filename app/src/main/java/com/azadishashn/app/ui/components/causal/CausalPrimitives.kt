package com.azadishashn.app.ui.components.causal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.ui.theme.OverlineStyle

/** "EXHIBIT — Title" kicker with an accent tick — the consulting-deck header. */
@Composable
fun ExhibitHeader(kicker: String, title: String, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(kicker.uppercase(), style = OverlineStyle, color = accent)
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Small lane tag for a step's time horizon. */
@Composable
fun HorizonTag(horizon: String, modifier: Modifier = Modifier) {
    val text = when (horizon) {
        "immediate" -> "NOW"
        "short_term" -> "SOON"
        "long_term" -> "LATER"
        else -> horizon.uppercase()
    }
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** ▲ gain · ▼ cost · ◆ mixed — semantic (not ideology) colours. */
@Composable
fun PolarityGlyph(polarity: String, modifier: Modifier = Modifier) {
    val (glyph, color) = when (polarity) {
        "gain" -> "▲" to Color(0xFF35C26A)
        "cost" -> "▼" to Color(0xFFE5565B)
        else -> "◆" to Color(0xFFE0A93B)
    }
    Text(glyph, modifier = modifier, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
}

/** A numbered node in the consequence flow. */
@Composable
fun NodeCard(
    index: Int,
    step: com.azadishashn.app.model.CausalStep,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.06f))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("$index", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                step.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (step.polarity.isNotBlank()) PolarityGlyph(step.polarity)
            if (step.horizon.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                HorizonTag(step.horizon)
            }
        }
    }
}

/** A compact "where this ideology leads" card for the fan-out. */
@Composable
fun PathCard(
    name: String,
    dot: Color,
    stance: String,
    outcome: String,
    risk: String,
    chosen: Boolean,
    modifier: Modifier = Modifier,
) {
    val border = if (chosen) dot else MaterialTheme.colorScheme.outlineVariant
    val alpha = if (chosen) 1f else 0.62f
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(if (chosen) 2.dp else 1.dp, border, RoundedCornerShape(10.dp))
            .background(if (chosen) dot.copy(alpha = 0.08f) else Color.Transparent)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(11.dp).clip(CircleShape).background(dot.copy(alpha = alpha)))
            Spacer(Modifier.width(8.dp))
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                modifier = Modifier.weight(1f),
            )
            if (chosen) {
                Text("YOUR CALL ✓", style = OverlineStyle, color = dot)
            }
        }
        if (stance.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(stance, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        }
        if (outcome.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("→ $outcome", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        }
        if (risk.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text("⚠ $risk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        }
    }
}
