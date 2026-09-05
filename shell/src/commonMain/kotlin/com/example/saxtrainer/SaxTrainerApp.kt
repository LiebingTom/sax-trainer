package com.example.saxtrainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.navigation.SaxTrainerNavHost

/**
 * The whole application, from the theme down.
 *
 * An entry point builds an [AppContainer] out of its platform's implementations and calls this.
 * Nothing else about the app lives outside this module, and both platforms get the same
 * navigation, the same screens and the same trainers.
 */
@Composable
fun SaxTrainerApp(container: AppContainer, modifier: Modifier = Modifier) {
    SaxTrainerTheme {
        SaxTrainerNavHost(container = container, modifier = modifier)
    }
}
