package com.example.saxtrainer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.saxtrainer.AppContainer
import com.example.saxtrainer.feature.longtones.LongToneRoute
import com.example.saxtrainer.feature.metronome.MetronomeRoute
import com.example.saxtrainer.feature.settings.SettingsRoute
import com.example.saxtrainer.home.HomeScreen

/**
 * Central navigation graph, and the only place that knows both the destinations and the
 * dependencies a feature needs. Feature screens receive callbacks and dependencies, never the
 * [NavHostController], so features stay independent of each other and of the app module.
 */
@Composable
fun SaxTrainerNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = SaxTrainerDestination.HOME,
        modifier = modifier,
    ) {
        composable(SaxTrainerDestination.HOME) {
            HomeScreen(onEntryClick = { route -> navController.navigate(route) })
        }
        composable(SaxTrainerDestination.METRONOME) {
            MetronomeRoute(onBack = { navController.popBackStack() })
        }
        composable(SaxTrainerDestination.LONG_TONES) {
            LongToneRoute(
                audioCapture = container.audioCapture,
                settingsRepository = container.settingsRepository,
                onBack = { navController.popBackStack() },
            )
        }
        composable(SaxTrainerDestination.SETTINGS) {
            SettingsRoute(
                settingsRepository = container.settingsRepository,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
