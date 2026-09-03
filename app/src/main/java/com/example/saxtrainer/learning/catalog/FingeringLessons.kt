package com.example.saxtrainer.learning.catalog

import com.example.saxtrainer.core.learning.ExerciseOutcome
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.learning.LessonPath
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.learning.TrainerLaunch
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.feature.fingering.CheckMode
import com.example.saxtrainer.feature.fingering.FingeringPreset
import com.example.saxtrainer.feature.fingering.FingeringResult
import com.example.saxtrainer.feature.fingering.NoteOrder
import com.example.saxtrainer.core.settings.NoteDisplay

/**
 * The guided fingering lessons, and the two adapters that connect them to the trainer.
 *
 * This is the only file that sees both sides: `core:learning` knows nothing about a fingering
 * exercise, and `feature:fingering` knows nothing about lessons. Everything specific to teaching
 * fingering by lesson lives here, so a future Pitch or Rhythm trainer is a sibling file and touches
 * nothing that already works.
 */

/** Route and home entry both need to name this path. */
const val FINGERING_PATH_ID = "fingering"

/** Carries a fingering configuration through the Learning layer, which never looks inside it. */
private data class FingeringLaunch(val preset: FingeringPreset) : TrainerLaunch

private fun note(name: NoteName, octave: Int) = Note(name, octave)

private val LEVEL_1_NOTES = listOf(note(NoteName.C, 4), note(NoteName.D, 4), note(NoteName.E, 4))

private val LEVEL_2_NOTES = LEVEL_1_NOTES + listOf(
    note(NoteName.F, 4),
    note(NoteName.G, 4),
    note(NoteName.A, 4),
)

private val LEVEL_3_NOTES = LEVEL_2_NOTES + listOf(note(NoteName.B, 4), note(NoteName.C, 5))

private fun fingeringStep(preset: FingeringPreset) =
    LessonStep(TrainerId.FINGERING, FingeringLaunch(preset))

/**
 * Three levels, each a step wider and a step quicker than the last.
 *
 * `requires` is left null throughout, so every level can be opened while the catalog is still being
 * written; turning progression on later is a change to this data and to nothing else.
 */
val fingeringLessonPath = LessonPath(
    id = FINGERING_PATH_ID,
    title = "Fingering",
    description = "Learn the grips a few notes at a time.",
    lessons = listOf(
        Lesson(
            id = LessonId("fingering-1"),
            title = "Level 1",
            subtitle = "C, D and E, in order",
            steps = listOf(
                fingeringStep(
                    FingeringPreset(
                        notes = LEVEL_1_NOTES,
                        order = NoteOrder.ORDERED,
                        secondsPerNote = 8,
                        noteDisplay = NoteDisplay.BOTH,
                    ),
                ),
            ),
        ),
        Lesson(
            id = LessonId("fingering-2"),
            title = "Level 2",
            subtitle = "Adds F, G and A",
            steps = listOf(
                fingeringStep(
                    FingeringPreset(
                        notes = LEVEL_2_NOTES,
                        order = NoteOrder.ORDERED,
                        secondsPerNote = 5,
                        noteDisplay = NoteDisplay.BOTH,
                    ),
                ),
            ),
        ),
        Lesson(
            id = LessonId("fingering-3"),
            title = "Level 3",
            subtitle = "The whole first octave, mixed up",
            steps = listOf(
                fingeringStep(
                    FingeringPreset(
                        notes = LEVEL_3_NOTES,
                        order = NoteOrder.RANDOM,
                        secondsPerNote = 5,
                        rounds = 2,
                        checkPlaying = true,
                        checkMode = CheckMode.SHOW,
                        noteDisplay = NoteDisplay.BOTH,
                    ),
                ),
            ),
        ),
        Lesson(
            id = LessonId("fingering-4"),
            title = "Level 4",
            subtitle = "Read it either way: name or notation, at random",
            steps = listOf(
                fingeringStep(
                    FingeringPreset(
                        notes = LEVEL_3_NOTES,
                        order = NoteOrder.RANDOM,
                        secondsPerNote = 5,
                        rounds = 2,
                        checkPlaying = true,
                        checkMode = CheckMode.SHOW,
                        // Each note comes up as its letter or on the staff, decided as the exercise
                        // is built: the point of the level is that neither can be relied on.
                        noteDisplay = NoteDisplay.RANDOM,
                    ),
                ),
            ),
        ),
    ),
)

/** The configuration a step carries, or null when the step belongs to a different trainer. */
fun fingeringPresetOf(step: LessonStep): FingeringPreset? = (step.launch as? FingeringLaunch)?.preset

/**
 * How a finished exercise reads as lesson progress.
 *
 * Reaching the last note is what completes a lesson: with listening switched off the trainer judges
 * nothing, so there would otherwise be no way to finish one in a quiet room. The score is only
 * meaningful when the microphone was actually listening.
 */
fun fingeringOutcomeOf(result: FingeringResult): ExerciseOutcome = ExerciseOutcome(
    completed = result.completed,
    score = if (result.checked && result.plannedNoteCount > 0) {
        result.correctCount.toFloat() / result.plannedNoteCount
    } else {
        null
    },
)
