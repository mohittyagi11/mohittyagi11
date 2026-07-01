package com.azadishashn.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.ui.theme.LaserBlue

/** Friendly label for a language code. */
fun languageLabel(code: String): String = when (code) {
    "hi" -> "हिंदी"
    "hinglish" -> "Hinglish"
    else -> "English"
}

/**
 * A row of language chips to switch the on-screen (and read-aloud) language.
 * Hidden when there's only one language to choose from. Dark mode renders black
 * glossy glass chips (active = brighter gloss + blue under-glow); light mode
 * keeps the standard Material [FilterChip].
 */
@Composable
fun LangChips(
    langs: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (langs.size <= 1) return
    val dark = isSystemInDarkTheme()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        langs.forEach { lang ->
            val on = lang == selected
            if (dark) {
                GlassChip(label = languageLabel(lang), selected = on) { onSelect(lang) }
            } else {
                FilterChip(
                    selected = on,
                    onClick = { onSelect(lang) },
                    label = { Text(languageLabel(lang)) },
                )
            }
        }
    }
}

/** A single black glossy glass chip (dark mode) — reused by the theme picker. */
@Composable
fun GlassChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .shadow(if (selected) 9.dp else 6.dp, shape, clip = false)
            .clip(shape)
            .background(glassBase(true))
            .background(glassSheen(true))
            .border(1.dp, glassEdge(true), shape)
            .then(
                if (selected) {
                    Modifier
                        .border(BorderStroke(1.dp, LaserBlue.copy(alpha = 0.42f)), shape)
                        .drawWithContent {
                            drawContent()
                            // blue under-glow marking the active language
                            drawRect(
                                brush = Brush.verticalGradient(
                                    listOf(Color.Transparent, LaserBlue.copy(alpha = 0.5f)),
                                    startY = size.height * 0.4f, endY = size.height,
                                ),
                                topLeft = Offset(0f, size.height * 0.4f),
                            )
                        }
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color(0xFFEDF2FF) else Color(0xFFCFD3E6),
        )
    }
}
