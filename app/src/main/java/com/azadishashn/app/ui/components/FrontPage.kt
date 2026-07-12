package com.azadishashn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One partisan front page — the judge's headline set in newsprint. A warm
 * paper wash, a masthead with a double rule, the headline big in quotes, the
 * outlet's slant as a byline. Pages land slightly tilted (alternate the
 * [tilt]) like papers tossed on the breakfast table.
 */
@Composable
fun FrontPage(
    outlet: String,
    slant: String,
    headline: String,
    edition: String = "MORNING EDITION",
    tilt: Float = -1.5f,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val rule = ink.copy(alpha = 0.4f)
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { rotationZ = tilt }
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            // The paper: a faint warm wash over the surface, works on both themes.
            .background(Color(0xFFEFDFC2).copy(alpha = 0.10f))
            .border(0.5.dp, rule.copy(alpha = 0.25f), MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                outlet.uppercase(),
                style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 2.sp),
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                edition,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        // The masthead's double rule.
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(rule))
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(rule))
        Spacer(Modifier.height(8.dp))
        Text(
            "“$headline”",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = ink,
        )
        if (slant.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "— $slant",
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
