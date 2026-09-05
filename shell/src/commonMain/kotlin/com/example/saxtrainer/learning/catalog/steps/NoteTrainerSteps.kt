package com.example.saxtrainer.learning.catalog.steps

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.learning.TrainerLaunch
import com.example.saxtrainer.core.music.Note

/** How a note trainer exercise becomes a lesson step. See [FingeringLaunch] for why it is data. */
data class NoteTrainerLaunch(
    val notes: List<Note>,
    val order: NoteOrder = NoteOrder.RANDOM,
    val rounds: Int = 1,
    /** Whether the fingering is offered while the note is being asked for. */
    val showFingeringHint: Boolean = false,
    /** What counts as a quick answer at this level. Scoring only; nothing times out. */
    val targetSeconds: Int = 4,
    /** Whether the exercise begins by itself rather than waiting for Start. */
    val autoStart: Boolean = true,
) : TrainerLaunch

fun noteTrainerStep(
    notes: List<Note>,
    order: NoteOrder = NoteOrder.RANDOM,
    rounds: Int = 1,
    showFingeringHint: Boolean = false,
    targetSeconds: Int = 4,
    autoStart: Boolean = true,
    title: String = "",
) = LessonStep(
    trainerId = TrainerId.NOTE_TRAINER,
    launch = NoteTrainerLaunch(
        notes = notes,
        order = order,
        rounds = rounds,
        showFingeringHint = showFingeringHint,
        targetSeconds = targetSeconds,
        autoStart = autoStart,
    ),
    title = title,
)

fun noteTrainerLaunchOf(step: LessonStep): NoteTrainerLaunch? = step.launch as? NoteTrainerLaunch
