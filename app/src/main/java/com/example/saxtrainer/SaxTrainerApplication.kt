package com.example.saxtrainer

import android.app.Application
import com.example.saxtrainer.core.audio.capture.AudioRecordCapture
import com.example.saxtrainer.core.audio.metronome.MetronomeEngine
import com.example.saxtrainer.core.audio.metronome.MetronomeFactory
import com.example.saxtrainer.core.settings.SharedPreferencesExerciseSetupRepository
import com.example.saxtrainer.core.settings.SharedPreferencesSettingsRepository
import com.example.saxtrainer.core.settings.SharedPreferencesUserLessonRepository

/**
 * Where the Android app is assembled: the four things `AppContainer` cannot build for itself, each
 * of them the Android implementation of an interface in a core module.
 */
class SaxTrainerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            settingsRepository = SharedPreferencesSettingsRepository(this),
            exerciseSetups = SharedPreferencesExerciseSetupRepository(this),
            audioCapture = AudioRecordCapture(this),
            newMetronome = MetronomeFactory { MetronomeEngine() },
            userLessons = SharedPreferencesUserLessonRepository(this),
        )
    }
}
