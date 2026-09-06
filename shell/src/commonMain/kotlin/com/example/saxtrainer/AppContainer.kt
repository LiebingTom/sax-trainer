package com.example.saxtrainer

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.metronome.MetronomeFactory
import com.example.saxtrainer.core.learning.Curriculum
import com.example.saxtrainer.core.learning.InMemoryLessonProgressRepository
import com.example.saxtrainer.core.learning.InMemoryLessonRunRepository
import com.example.saxtrainer.core.learning.LessonProgressRepository
import com.example.saxtrainer.core.learning.LessonRunRepository
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.SettingsRepository
import com.example.saxtrainer.learning.SaxTrainerCurriculum

/**
 * Manual dependency container. The app is small enough that constructing a handful of singletons
 * here is clearer than adding a dependency injection framework; feature modules receive what they
 * need as parameters and never look dependencies up themselves.
 *
 * What a platform has to supply is what cannot be written once: where settings are kept, where
 * the setups exercises were last started with are kept, where sound comes from, and where a click
 * comes from. Everything else is built here and is the same on Android and on the web.
 */
class AppContainer(
    val settingsRepository: SettingsRepository,
    /** What each exercise was last started with, offered back as its defaults next time. */
    val exerciseSetups: ExerciseSetupRepository,
    val audioCapture: AudioCapture,
    /** A new click per practice: two exercises must never share one. */
    val newMetronome: MetronomeFactory,
    curriculum: Curriculum = SaxTrainerCurriculum,
) {
    val curriculum: Curriculum = curriculum

    /**
     * Progress lasts as long as the app runs. A persistent implementation belongs beside whatever
     * the platform already uses to store settings; only this line changes when it arrives.
     */
    val lessonProgress: LessonProgressRepository = InMemoryLessonProgressRepository()

    /**
     * What the steps of the lesson being worked through have scored so far. Scaffolding for one
     * sitting, so there is nothing here worth persisting even when progress is.
     */
    val lessonRuns: LessonRunRepository = InMemoryLessonRunRepository()
}
