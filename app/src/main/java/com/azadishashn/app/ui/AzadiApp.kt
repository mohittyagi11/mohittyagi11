package com.azadishashn.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azadishashn.app.game.GameViewModel
import com.azadishashn.app.game.Screen

@Composable
fun AzadiApp(vm: GameViewModel = viewModel()) {
    // Intercept system Back everywhere except the root Setup screen, so Back
    // navigates within the app instead of closing it.
    BackHandler(enabled = vm.state.screen != Screen.Setup) { vm.onBack() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        // Inset the content away from the status/navigation bars (edge-to-edge is
        // forced on Android 15 / target SDK 35) and lift it above the keyboard.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
        ) {
            when (vm.state.screen) {
                Screen.Setup -> SetupScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
                Screen.Round -> RoundScreen(vm)
                Screen.Vote -> VoteScreen(vm)
                Screen.Result -> ResultScreen(vm)
                Screen.Standings -> StandingsScreen(vm)
            }
        }
    }
}
