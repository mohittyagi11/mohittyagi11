package com.azadishashn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azadishashn.app.ui.theme.Elev
import com.azadishashn.app.ui.theme.GlassDarkBottom
import com.azadishashn.app.ui.theme.GlassDarkTop

/**
 * The keystone surface of the cinematic dark-glass look: a frosted, translucent
 * pane that floats over the animated field with a soft drop shadow, a hairline
 * gradient edge (bright at top), a faint top sheen, and an optional ideology/gold
 * [glow] behind it or coloured [accent] edge. Blur isn't used (needs API 31);
 * the glass is built from translucent gradients + glow so it works on every API.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = Elev.floating,
    glow: Color? = null,
    accent: Color? = null,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val base = if (dark) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
    } else {
        Color.White.copy(alpha = 0.74f)
    }
    val sheen = if (dark) {
        Brush.verticalGradient(listOf(GlassDarkTop, GlassDarkBottom, Color.Transparent))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent))
    }
    val edge = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.28f else 0.9f),
            Color.White.copy(alpha = if (dark) 0.04f else 0.2f),
        ),
    )

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (glow != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(glow.copy(alpha = 0.40f), Color.Transparent),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.maxDimension * 0.7f,
                            ),
                            radius = size.maxDimension * 0.7f,
                        )
                    },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(elevation, shape, clip = false)
                .clip(shape)
                .background(base)
                .background(sheen)
                .border(1.dp, edge, shape)
                .then(
                    if (accent != null) {
                        Modifier.drawWithContent {
                            drawContent()
                            drawRect(
                                color = accent,
                                topLeft = Offset.Zero,
                                size = Size(4.dp.toPx(), this.size.height),
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(contentPadding),
            content = content,
        )
    }
}
