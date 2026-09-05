package com.example.saxtrainer.learning.catalog.steps

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.learning.TrainerLaunch
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.settings.NoteDisplay

/**
 * How a fingering exercise becomes a lesson step.
 *
 * The configuration is written out here rather than being a `FingeringPreset`, because
 * `:feature:fingering` is Android-only until M8 and this catalog is shared. The fields are the
 * trainer's own, one for one; `:app` turns a [FingeringLaunch] back into a preset where it
 * registers the destination, which is the only place that can see both. When the trainer becomes
 * multiplatform this can carry the preset again, as [NoteQuizSteps] already does.
 */
data class FingeringLaunch(
    val notes: List<Note>,
    val order: NoteOrder = NoteOrder.RANDOM,
    val secondsPerNote: Int = 5,
    val rounds: Int = 1,
    /** Whether the player has to sound each note, or only read its fingering. */
    val checkPlaying: Boolean = false,
    /** null leaves the player's own note-display preference alone. */
    val noteDisplay: NoteDisplay? = null,
    /** Whether the exercise begins by itself rather than waiting for Start. */
    val autoStart: Boolean = true,
) : TrainerLaunch

fun fingeringStep(
    notes: List<Note>,
    order: NoteOrder = NoteOrder.RANDOM,
    secondsPerNote: Int = 5,
    rounds: Int = 1,
    checkPlaying: Boolean = false,
    noteDisplay: NoteDisplay? = null,
    autoStart: Boolean = true,
    title: String = "",
) = LessonStep(
    trainerId = TrainerId.FINGERING,
    launch = FingeringLaunch(
        notes = notes,
        order = order,
        secondsPerNote = secondsPerNote,
        rounds = rounds,
        checkPlaying = checkPlaying,
        noteDisplay = noteDisplay,
        autoStart = autoStart,
    ),
    title = title,
)

/** The configuration a step carries, or null when the step belongs to a different trainer. */
fun fingeringLaunchOf(step: LessonStep): FingeringLaunch? = step.launch as? FingeringLaunch
