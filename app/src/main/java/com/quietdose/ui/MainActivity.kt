package com.quietdose.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.quietdose.ui.home.HomeScreen
import com.quietdose.ui.insights.InsightsScreen
import com.quietdose.ui.stack.StackScreen
import com.quietdose.ui.theme.Accent
import com.quietdose.ui.theme.AccentSoft
import com.quietdose.ui.theme.Ambient
import com.quietdose.ui.theme.DoseTheme
import com.quietdose.ui.theme.Ink
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DoseTheme {
                val permission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val hour = remember { LocalTime.now().hour }
                var tab by rememberSaveable { mutableIntStateOf(0) }

                Box(Modifier.fillMaxSize().background(Ambient.backdrop(hour))) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        bottomBar = { BottomBar(tab) { tab = it } },
                    ) { inner ->
                        when (tab) {
                            0 -> HomeScreen(modifier = Modifier.padding(inner))
                            1 -> StackScreen(modifier = Modifier.padding(inner))
                            else -> InsightsScreen(modifier = Modifier.padding(inner))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("Today", Icons.Rounded.Today, 0),
        Triple("Stack", Icons.Rounded.GridView, 1),
        Triple("Insights", Icons.Rounded.AutoAwesome, 2),
    )
    NavigationBar(containerColor = Surface1) {
        items.forEach { (label, icon, index) ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent,
                    selectedTextColor = TextHigh,
                    indicatorColor = AccentSoft,
                    unselectedIconColor = TextLow,
                    unselectedTextColor = TextLow,
                ),
            )
        }
    }
}

@Composable
private fun Placeholder(modifier: Modifier, icon: ImageVector, title: String, body: String) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = TextLow, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = TextHigh)
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = TextMid,
        )
    }
}
