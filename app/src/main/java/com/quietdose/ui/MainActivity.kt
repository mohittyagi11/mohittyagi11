package com.quietdose.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quietdose.ui.theme.DoseTheme
import com.quietdose.ui.theme.TextMid

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DoseTheme {
                Scaffold { inner ->
                    HomePlaceholder(Modifier.padding(inner))
                }
            }
        }
    }
}

/**
 * Temporary home for increment 1 — proves the theme + build pipeline end to
 * end. Increment 5 replaces this with the real glanceable "what's next" screen
 * and the buttery check-off.
 */
@Composable
private fun HomePlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Dose", style = MaterialTheme.typography.displaySmall)
        Text(
            "Reminders that follow your day, not the clock.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMid,
        )
    }
}

@Preview
@Composable
private fun HomePreview() {
    DoseTheme { HomePlaceholder() }
}
