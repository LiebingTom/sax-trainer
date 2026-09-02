package com.example.saxtrainer

import android.app.Application
import android.content.Context
import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioRecordCapture
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.core.settings.SharedPreferencesSettingsRepository

/**
 * Manual dependency container. The app is small enough that constructing a handful of singletons
 * here is clearer than adding a dependency injection framework; feature modules receive what they
 * need as parameters and never look dependencies up themselves.
 */
class AppContainer(context: Context) {
    val settingsRepository: SettingsRepository = SharedPreferencesSettingsRepository(context)
    val audioCapture: AudioCapture = AudioRecordCapture(context)
}

class SaxTrainerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
