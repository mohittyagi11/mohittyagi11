package com.azadishashn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.azadishashn.app.ui.AzadiApp
import com.azadishashn.app.ui.theme.AzadiShashnTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AzadiShashnTheme {
                AzadiApp()
            }
        }
    }
}
