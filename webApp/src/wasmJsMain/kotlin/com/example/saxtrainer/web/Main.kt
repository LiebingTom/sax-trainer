package com.example.saxtrainer.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.ComposeViewport
import com.example.saxtrainer.AppContainer
import com.example.saxtrainer.SaxTrainerApp
import com.example.saxtrainer.core.audio.capture.WebAudioCapture
import com.example.saxtrainer.core.audio.metronome.MetronomeFactory
import com.example.saxtrainer.core.audio.metronome.WebMetronome
import com.example.saxtrainer.core.settings.LocalStorageExerciseSetupRepository
import com.example.saxtrainer.core.settings.LocalStorageSettingsRepository
import com.example.saxtrainer.core.settings.LocalStorageUserLessonRepository
import kotlinx.browser.document

/**
 * The web app.
 *
 * The same shell, the same navigation, the same screens and the same trainers as Android; only the
 * things a platform has to answer for differ, and each of those is an implementation of an
 * interface `:shell` never looks behind.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val container = webContainer()
    ComposeViewport {
        SaxTrainerApp(container = container, onAppearanceChanged = ::applyAppearance)
    }
}

/**
 * Everything the app is built from in a browser. Separate from [main] so a test can start the same
 * app the page starts, rather than one assembled a second way.
 */
internal fun webContainer(): AppContainer = AppContainer(
    settingsRepository = LocalStorageSettingsRepository(),
    exerciseSetups = LocalStorageExerciseSetupRepository(),
    audioCapture = WebAudioCapture(),
    newMetronome = MetronomeFactory { WebMetronome() },
    userLessons = LocalStorageUserLessonRepository(),
)

/**
 * The page around the canvas follows the app's theme, not the browser's.
 *
 * `index.html` can only guess before the app has read its settings; from here on the page background
 * and `color-scheme` - which decides how the browser draws anything of its own - are the theme's.
 */
private fun applyAppearance(darkTheme: Boolean, background: Color) {
    val style = document.body?.style ?: return
    style.setProperty("color-scheme", if (darkTheme) "dark" else "light")
    style.background = background.toCssHex()
}

private fun Color.toCssHex(): String =
    "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0')
