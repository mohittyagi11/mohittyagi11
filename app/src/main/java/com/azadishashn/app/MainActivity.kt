package com.azadishashn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.azadishashn.app.ui.AzadiApp
import com.azadishashn.app.ui.theme.AzadiShashnTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge is on by default on Android 15; make the system-bar icons
        // dark so they stay visible against the app's light background.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setContent {
            AzadiShashnTheme {
                AzadiApp()
            }
        }
    }
}
