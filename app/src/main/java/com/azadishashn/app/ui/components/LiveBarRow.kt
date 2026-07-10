package com.azadishashn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azadishashn.app.game.LiveBar
import com.azadishashn.app.ui.theme.IdeologyTheme
import com.azadishashn.app.ui.theme.NumberStyle

/**
 * TONIGHT'S BAR — the live number each ideology demands of the current
 * answerer to KEEP its card, computed with the exact math the verdict will
 * use. Nobody argues blind: mood shifts show as ▼/▲, a nation crisis shows
 * the ⚡+1 it grants, a golden age its ☼−1.
 */
@Composable
fun LiveBarRow(bars: List<LiveBar>, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Text(
            "TONIGHT'S BAR",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            bars.forEach { b ->
                val brand = IdeologyTheme.of(b.ideology).brand
                Column(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(IdeologyTheme.container(b.ideology))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IdeologyDot(b.ideology, size = 8.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            b.ideology,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedCounter(value = b.required, style = NumberStyle, color = brand)
                        Spacer(Modifier.width(4.dp))
                        val marks = buildList {
                            if (b.moodAdj < 0) add("▼ mood")
                            if (b.moodAdj > 0) add("▲ mood")
                            if (b.meterAdj > 0) add("⚡ +1")
                            if (b.meterAdj < 0) add("☼ −1")
                        }
                        if (marks.isNotEmpty()) {
                            Text(
                                marks.joinToString(" "),
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    b.meterAdj > 0 || b.moodAdj < 0 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.error
                                },
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        "hold ${b.held}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "The strength each line needs tonight to KEEP its card — the more you hold, the higher your bar. ⚡ = argues at +1 (crisis), ☼ = −1 (golden age).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
