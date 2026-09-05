package com.example.saxtrainer.learning.catalog.steps

import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.learning.TrainerLaunch
import com.example.saxtrainer.core.music.Tempo

/** How a sequence exercise becomes a lesson step. See [FingeringLaunch] for why it is data. */
data class SequenceLaunch(
    /** Id of a sequence in the trainer's library. An unknown id leaves the default selected. */
    val sequenceId: String,
    val tempo: Tempo,
    /** Plays the notes back down to where they started before repeating. */
    val addInverse: Boolean = false,
    /** How many times through. Five is a practice session rather than a single read-through. */
    val repeats: Int = 5,
    val metronome: Boolean = true,
    val checkPitch: Boolean = false,
    val checkTiming: Boolean = false,
    /** Whether the exercise begins by itself rather than waiting for Start. */
    val autoStart: Boolean = true,
) : TrainerLaunch

fun sequenceStep(
    sequenceId: String,
    tempo: Tempo,
    addInverse: Boolean = false,
    repeats: Int = 5,
    metronome: Boolean = true,
    checkPitch: Boolean = false,
    checkTiming: Boolean = false,
    autoStart: Boolean = true,
    title: String = "",
) = LessonStep(
    trainerId = TrainerId.SEQUENCE,
    launch = SequenceLaunch(
        sequenceId = sequenceId,
        tempo = tempo,
        addInverse = addInverse,
        repeats = repeats,
        metronome = metronome,
        checkPitch = checkPitch,
        checkTiming = checkTiming,
        autoStart = autoStart,
    ),
    title = title,
)

fun sequenceLaunchOf(step: LessonStep): SequenceLaunch? = step.launch as? SequenceLaunch
