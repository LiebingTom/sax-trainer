package com.example.saxtrainer.learning

import com.example.saxtrainer.core.learning.LearningUnit
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.UserLesson
import com.example.saxtrainer.core.settings.UserLessonBook
import com.example.saxtrainer.feature.longtones.longTonePresetOf
import com.example.saxtrainer.feature.notequiz.quizPresetOf
import com.example.saxtrainer.feature.notetrainer.noteTrainerPresetOf
import com.example.saxtrainer.feature.phrasequiz.phraseQuizPresetOf
import com.example.saxtrainer.feature.phrasetrainer.phraseTrainerPresetOf
import com.example.saxtrainer.feature.sequence.sequencePresetOf
import com.example.saxtrainer.learning.catalog.steps.longToneStep
import com.example.saxtrainer.learning.catalog.steps.noteQuizStep
import com.example.saxtrainer.learning.catalog.steps.noteTrainerStep
import com.example.saxtrainer.learning.catalog.steps.phraseQuizStep
import com.example.saxtrainer.learning.catalog.steps.phraseTrainerStep
import com.example.saxtrainer.learning.catalog.steps.sequenceStep

/**
 * What the player saved, as lessons.
 *
 * There is one lesson system and this is what keeps it that way: a saved practice becomes a real
 * [Lesson] in a real [LearningUnit], so starting it, walking it, scoring it and drawing it are the
 * code the built-in units already use. Only where a lesson comes from differs.
 *
 * This is also the one place in the app that sees both a stored setup and a trainer's own
 * configuration type, which is why it sits beside the catalog's step adapters rather than in
 * `core:settings`, which must not know what a trainer is.
 */
fun userUnitsOf(book: UserLessonBook): List<LearningUnit> = book.groups.mapNotNull { group ->
    val lessons = book.lessonsOf(group.id).mapNotNull(::lessonOf)
    // A unit has to have something in it, and a group the player has made but not filled yet is
    // still theirs: the My Lessons screen draws it from the book, so it is only missing from the
    // list of things that can actually be started.
    if (lessons.isEmpty()) {
        null
    } else {
        LearningUnit(
            id = unitIdOf(group.id),
            title = group.name,
            description = savedSummaryOf(lessons.size),
            lessons = lessons,
        )
    }
}

/** The unit a saved group is drawn as. Prefixed so it can never collide with a catalog id. */
fun unitIdOf(groupId: String): String = "$USER_PREFIX$groupId"

/** The lesson a saved practice is started as. Prefixed for the same reason. */
fun lessonIdOf(lessonId: String): LessonId = LessonId("$USER_PREFIX$lessonId")

/** Which saved practice a lesson id names, or null for one of the built-in lessons. */
fun savedLessonIdOf(id: LessonId): String? =
    id.value.takeIf { it.startsWith(USER_PREFIX) }?.removePrefix(USER_PREFIX)

/**
 * A saved practice as a lesson of one step, or null for one this build cannot start.
 *
 * Null covers both halves of the same rule: a trainer whose name this version does not know - a
 * book written by a newer one - and a configuration that no longer describes an exercise, such as a
 * quiz left with too few notes to ask a question. Neither is an error worth stopping for; the
 * lesson is simply not offered, exactly as an unknown starred tool is not drawn.
 */
private fun lessonOf(saved: UserLesson): Lesson? {
    val step = stepOf(saved) ?: return null
    return Lesson(
        id = lessonIdOf(saved.id),
        title = saved.name,
        subtitle = subtitleOf(step.trainerId),
        steps = listOf(step),
    )
}

private fun stepOf(saved: UserLesson): LessonStep? {
    val trainer = TrainerId.entries.firstOrNull { it.name == saved.trainerId } ?: return null
    val setup: ExerciseSetup = saved.setup

    return when (trainer) {
        TrainerId.NOTE_TRAINER -> noteTrainerPresetOf(setup)?.let(::noteTrainerStep)
        TrainerId.NOTE_QUIZ -> quizPresetOf(setup)?.let(::noteQuizStep)
        TrainerId.SEQUENCE -> sequencePresetOf(setup)?.let(::sequenceStep)
        TrainerId.LONG_TONES -> longTonePresetOf(setup)?.let(::longToneStep)
        TrainerId.PHRASE_QUIZ -> phraseQuizPresetOf(setup)?.let(::phraseQuizStep)
        TrainerId.PHRASE_TRAINER -> phraseTrainerPresetOf(setup)?.let(::phraseTrainerStep)
        // A tempo is not a practice, so the metronome is not something to save.
        TrainerId.METRONOME -> null
    }
}

/**
 * What the tile says under the name the player gave it: which practice this is.
 *
 * Null for a name this version does not know - a book written by a newer one - which is also the
 * answer to "can this be started at all".
 */
fun trainerNameOf(trainerId: String): String? =
    TrainerId.entries.firstOrNull { it.name == trainerId }?.let(::subtitleOf)

private fun subtitleOf(trainer: TrainerId): String = when (trainer) {
    TrainerId.NOTE_QUIZ -> "Note Quiz"
    TrainerId.NOTE_TRAINER -> "Note Trainer"
    TrainerId.SEQUENCE -> "Sequence Trainer"
    TrainerId.LONG_TONES -> "Long Tones"
    TrainerId.METRONOME -> "Metronome"
    TrainerId.PHRASE_QUIZ -> "Phrase Quiz"
    TrainerId.PHRASE_TRAINER -> "Phrase Trainer"
}

/** How a group of saved practices describes itself, on its card and as a unit. */
fun savedSummaryOf(count: Int): String = when (count) {
    0 -> "Nothing saved here yet."
    1 -> "One saved exercise."
    else -> "$count saved exercises."
}

/**
 * What marks an id as the player's own.
 *
 * Only letters, digits and a dash, because an id travels in a navigation route and nothing in that
 * layer escapes anything. The name the player typed never does.
 */
private const val USER_PREFIX = "my-"
