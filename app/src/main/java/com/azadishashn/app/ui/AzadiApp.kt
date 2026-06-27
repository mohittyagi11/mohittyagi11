package com.azadishashn.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azadishashn.app.game.GameViewModel
import com.azadishashn.app.game.Screen

@Composable
fun AzadiApp(vm: GameViewModel = viewModel()) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
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
