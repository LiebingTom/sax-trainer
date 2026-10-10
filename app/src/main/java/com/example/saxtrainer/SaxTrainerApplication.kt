package com.example.saxtrainer

import android.app.Application
import com.example.saxtrainer.core.audio.capture.AudioRecordCapture
import com.example.saxtrainer.core.audio.metronome.MetronomeEngine
import com.example.saxtrainer.core.audio.metronome.MetronomeFactory
import com.example.saxtrainer.core.reed.SharedPreferencesReedLogbookRepository
import com.example.saxtrainer.core.settings.SharedPreferencesExerciseSetupRepository
import com.example.saxtrainer.core.settings.SharedPreferencesSettingsRepository
import com.example.saxtrainer.core.settings.SharedPreferencesUserLessonRepository
import kotlinx.coroutines.MainScope

/**
 * Where the Android app is assembled: the things `AppContainer` cannot build for itself, each of
 * them the Android implementation of an interface in a core module - and the two things only Android
 * has, the notifications of the running reed session and of the open metronome.
 */
class SaxTrainerApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** Keeps the reed session's notification in step with the logbook, for the life of the process. */
    lateinit var reedSessionSync: ReedSessionSync
        private set

    /** Keeps the open metronome's notification in step with it, for the life of the process. */
    lateinit var metronomeSync: MetronomeSync
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            settingsRepository = SharedPreferencesSettingsRepository(this),
            exerciseSetups = SharedPreferencesExerciseSetupRepository(this),
            audioCapture = AudioRecordCapture(this),
            newMetronome = MetronomeFactory { MetronomeEngine() },
            userLessons = SharedPreferencesUserLessonRepository(this),
            reedLogbook = SharedPreferencesReedLogbookRepository(this),
            // Shown and stopped from its own notification while it does: see MetronomeService.
            metronomePlaysInBackground = true,
        )
        reedSessionSync = ReedSessionSync(this, container.reedLogbook).also { it.start(MainScope()) }
        metronomeSync = MetronomeSync(this, container.activeMetronome).also { it.start(MainScope()) }
    }
}
