package com.example.saxtrainer.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.example.saxtrainer.AppContainer
import com.example.saxtrainer.SaxTrainerApp
import com.example.saxtrainer.core.audio.capture.WebAudioCapture
import com.example.saxtrainer.core.audio.metronome.MetronomeFactory
import com.example.saxtrainer.core.audio.metronome.WebMetronome
import com.example.saxtrainer.core.settings.LocalStorageSettingsRepository

/**
 * The web app.
 *
 * The same shell, the same navigation and the same screens as Android; only the three things a
 * platform has to answer for differ. No trainers are added, so the browser gets the destinations
 * `:shell` carries and the lessons that can be taught with them - the microphone trainers arrive
 * in M8.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val container = webContainer()
    ComposeViewport {
        SaxTrainerApp(container = container)
    }
}

/**
 * Everything the app is built from in a browser. Separate from [main] so a test can start the same
 * app the page starts, rather than one assembled a second way.
 */
internal fun webContainer(): AppContainer = AppContainer(
    settingsRepository = LocalStorageSettingsRepository(),
    audioCapture = WebAudioCapture(),
    newMetronome = MetronomeFactory { WebMetronome() },
)
