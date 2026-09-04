package com.example.saxtrainer

import android.app.Application
import android.content.Context
import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioRecordCapture
import com.example.saxtrainer.core.learning.Curriculum
import com.example.saxtrainer.core.learning.InMemoryLessonProgressRepository
import com.example.saxtrainer.core.learning.InMemoryLessonRunRepository
import com.example.saxtrainer.core.learning.LessonProgressRepository
import com.example.saxtrainer.core.learning.LessonRunRepository
import com.example.saxtrainer.learning.SaxTrainerCurriculum
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
    val curriculum: Curriculum = SaxTrainerCurriculum

    /**
     * Progress lasts as long as the app runs. A persistent implementation belongs beside
     * [SharedPreferencesSettingsRepository], which already owns everything the app stores; only this
     * line changes when it arrives.
     */
    val lessonProgress: LessonProgressRepository = InMemoryLessonProgressRepository()

    /**
     * What the steps of the lesson being worked through have scored so far. Scaffolding for one
     * sitting, so there is nothing here worth persisting even when progress is.
     */
    val lessonRuns: LessonRunRepository = InMemoryLessonRunRepository()
}

class SaxTrainerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
