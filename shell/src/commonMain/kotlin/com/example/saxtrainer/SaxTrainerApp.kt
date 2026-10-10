package com.example.saxtrainer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.settings.ThemeMode
import com.example.saxtrainer.feature.settings.seed
import com.example.saxtrainer.navigation.SaxTrainerNavHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The whole application, from the theme down.
 *
 * An entry point builds an [AppContainer] out of its platform's implementations and calls this.
 * Nothing else about the app lives outside this module, and both platforms get the same
 * navigation, the same screens and the same trainers.
 *
 * The theme follows the settings: their mode and main colour are applied here, once, so every
 * screen below changes with them. [onAppearanceChanged] hands the result to what the platform draws
 * outside Compose - Android's system bars and window, the web page behind the canvas - whenever it
 * changes, including once at start.
 */
@Composable
fun SaxTrainerApp(
    container: AppContainer,
    modifier: Modifier = Modifier,
    onAppearanceChanged: (darkTheme: Boolean, background: Color) -> Unit = { _, _ -> },
    /** Requests from outside the screens to open the reed logbook - see [SaxTrainerNavHost]. */
    reedLogbookRequests: Flow<Unit> = emptyFlow(),
    /** Requests from outside the screens to show the open metronome - see [SaxTrainerNavHost]. */
    metronomeRequests: Flow<Unit> = emptyFlow(),
) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    SaxTrainerTheme(darkTheme = darkTheme, seed = settings.themeColor.seed) {
        val background = MaterialTheme.colorScheme.background
        val notify by rememberUpdatedState(onAppearanceChanged)
        LaunchedEffect(darkTheme, background) { notify(darkTheme, background) }

        SaxTrainerNavHost(
            container = container,
            modifier = modifier,
            reedLogbookRequests = reedLogbookRequests,
            metronomeRequests = metronomeRequests,
        )
    }
}
