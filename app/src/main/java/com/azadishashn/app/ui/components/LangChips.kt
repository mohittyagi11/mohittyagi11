package com.azadishashn.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Friendly label for a language code. */
fun languageLabel(code: String): String = when (code) {
    "hi" -> "हिंदी"
    "hinglish" -> "Hinglish"
    else -> "English"
}

/**
 * A row of language chips to switch the on-screen (and read-aloud) language.
 * Hidden when there's only one language to choose from.
 */
@Composable
fun LangChips(
    langs: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (langs.size <= 1) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        langs.forEach { lang ->
            FilterChip(
                selected = lang == selected,
                onClick = { onSelect(lang) },
                label = { Text(languageLabel(lang)) },
            )
        }
    }
}
