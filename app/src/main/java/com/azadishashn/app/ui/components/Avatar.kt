package com.azadishashn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azadishashn.app.ui.theme.GoldDim

private val AvatarHues = listOf(
    Color(0xFF6C8AE4), Color(0xFFE0729A), Color(0xFF49C5A6),
    Color(0xFFE0A24A), Color(0xFF9B7BE0), Color(0xFFE06A5C),
    Color(0xFF4FB0E0), Color(0xFF8AC15A),
)

/** A colour-seeded monogram disc — gives each player a face across the app. */
@Composable
fun Avatar(name: String, seed: Int, size: Dp = 38.dp, modifier: Modifier = Modifier) {
    val hue = AvatarHues[((seed % AvatarHues.size) + AvatarHues.size) % AvatarHues.size]
    val brush = Brush.linearGradient(listOf(lerp(hue, Color.White, 0.12f), lerp(hue, Color.Black, 0.28f)))
    val initials = name.trim().take(1).uppercase().ifBlank { "?" }
    Box(
        modifier
            .size(size)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(brush)
            .border(1.dp, GoldDim.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
