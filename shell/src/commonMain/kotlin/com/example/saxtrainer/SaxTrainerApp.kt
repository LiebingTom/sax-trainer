package com.example.saxtrainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.navigation.PlatformTrainers
import com.example.saxtrainer.navigation.SaxTrainerNavHost

/**
 * The whole application, from the theme down.
 *
 * An entry point builds an [AppContainer] out of its platform's implementations, says which
 * trainers that platform has, and calls this. Nothing else about the app lives outside this module.
 */
@Composable
fun SaxTrainerApp(
    container: AppContainer,
    platform: PlatformTrainers = PlatformTrainers.None,
    modifier: Modifier = Modifier,
) {
    SaxTrainerTheme {
        SaxTrainerNavHost(container = container, platform = platform, modifier = modifier)
    }
}
