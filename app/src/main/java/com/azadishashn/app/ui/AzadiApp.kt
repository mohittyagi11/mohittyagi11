package com.azadishashn.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import com.azadishashn.app.ui.components.FlowBackground

@Composable
fun AzadiApp(vm: GameViewModel = viewModel()) {
    // Intercept system Back everywhere except the root Library home, so Back
    // navigates within the app instead of closing it. Keyed on the live state —
    // AnimatedContent below only animates the swap, it never owns nav state.
    BackHandler(enabled = vm.state.screen != Screen.Library) { vm.onBack() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxSize()) {
            // ONE static field, hoisted to the root behind the screen swap and
            // edge-to-edge under the system bars. It never animates during a
            // transition, so only the lightweight glass content moves.
            FlowBackground(Modifier.fillMaxSize())

            // Inset the content away from the system bars and lift it above the
            // keyboard. AzadiScaffold relies on this owner for insets.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding(),
            ) {
                AnimatedContent(
                    targetState = vm.state.screen,
                    // Crisp + cheap: fade + a small slide, no scale, no bouncy
                    // spring — scaling would rasterize the whole subtree each frame.
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInHorizontally(tween(240)) { it / 14 }) togetherWith
                            (fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { -it / 18 })
                    },
                    label = "screen",
                ) { screen ->
                    when (screen) {
                        Screen.Library -> LibraryScreen(vm)
                        Screen.Setup -> SetupScreen(vm)
                        Screen.Settings -> SettingsScreen(vm)
                        Screen.Round -> RoundScreen(vm)
                        Screen.Result -> ResultScreen(vm)
                        Screen.Standings -> StandingsScreen(vm)
                        Screen.Edit -> EditScreen(vm)
                        Screen.Transfer -> TransferScreen(vm)
                    }
                }
            }
        }
    }
}
