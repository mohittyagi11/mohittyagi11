package com.azadishashn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.azadishashn.app.ui.AzadiApp
import com.azadishashn.app.ui.theme.AzadiShashnTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge is on by default on Android 15.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val dark = isSystemInDarkTheme()
            // System-bar icon colour follows the theme so they stay visible on
            // both the light paper and dark navy canvases. In a SideEffect so it
            // re-applies if the device theme flips at runtime.
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            AzadiShashnTheme(darkTheme = dark) {
                AzadiApp()
            }
        }
    }
}
