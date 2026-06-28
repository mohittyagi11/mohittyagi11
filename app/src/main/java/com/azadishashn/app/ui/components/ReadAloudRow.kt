package com.azadishashn.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Friendly label for a read-aloud language code. */
private fun langLabel(code: String): String = when (code) {
    "hi" -> "हिंदी"
    "hinglish" -> "Hinglish"
    else -> "English"
}

/**
 * The read-aloud control: one play button per chosen narration language (so a
 * turn can be heard in English, Hinglish, …), collapsing to a single Stop while
 * speaking. [textFor] resolves the spoken text for a language.
 */
@Composable
fun ReadAloudRow(
    langs: List<String>,
    isReading: Boolean,
    textFor: (String) -> String,
    onPlay: (lang: String, text: String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (langs.isEmpty()) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isReading) {
            OutlinedButton(onClick = onStop, shape = MaterialTheme.shapes.large) {
                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Stop", color = MaterialTheme.colorScheme.error)
            }
        } else {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Read aloud",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(20.dp),
            )
            langs.forEach { lang ->
                OutlinedButton(
                    onClick = { onPlay(lang, textFor(lang)) },
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(if (langs.size == 1) "Read aloud" else langLabel(lang))
                }
            }
        }
    }
}
