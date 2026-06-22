package com.quietdose.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quietdose.di.ServiceLocator
import com.quietdose.ui.home.HomeViewModel
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid
import com.quietdose.ui.viz.DayGraph

@Composable
fun InsightsScreen(modifier: Modifier = Modifier, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val events = state.dayEvents
    val context = LocalContext.current
    val spec = remember(events) { ServiceLocator.brain(context).design(events) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(44.dp))
        Text(spec.title, style = MaterialTheme.typography.displaySmall, color = TextHigh)
        Text(spec.caption, style = MaterialTheme.typography.bodyLarge, color = TextMid)

        if (events.isEmpty()) {
            Spacer(Modifier.height(40.dp))
            Text("Nothing to chart yet today.", style = MaterialTheme.typography.bodyLarge, color = TextLow)
            return@Column
        }

        Spacer(Modifier.height(16.dp))
        DayGraph(
            events = events,
            spec = spec,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )

        Spacer(Modifier.height(20.dp))
        // legend: colour → category
        val legend = events.distinctBy { it.category }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            legend.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(e.color, CircleShape))
                    Spacer(Modifier.size(10.dp))
                    Text(e.category, style = MaterialTheme.typography.bodyMedium, color = TextMid)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "A seeded view. The on-device model will design these graphs from your day.",
            style = MaterialTheme.typography.labelSmall,
            color = TextLow,
        )
        Spacer(Modifier.height(24.dp))
    }
}
