package com.example.saxtrainer.navigation

import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.feature.fingering.FingeringPreset
import com.example.saxtrainer.feature.fingering.FingeringRoute
import com.example.saxtrainer.feature.longtones.LongToneRoute
import com.example.saxtrainer.feature.notetrainer.NoteTrainerPreset
import com.example.saxtrainer.feature.notetrainer.NoteTrainerRoute
import com.example.saxtrainer.feature.sequence.SequencePreset
import com.example.saxtrainer.feature.sequence.SequenceRoute
import com.example.saxtrainer.learning.catalog.steps.fingeringLaunchOf
import com.example.saxtrainer.learning.catalog.steps.fingeringOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerLaunchOf
import com.example.saxtrainer.learning.catalog.steps.noteTrainerOutcomeOf
import com.example.saxtrainer.learning.catalog.steps.sequenceLaunchOf
import com.example.saxtrainer.learning.catalog.steps.sequenceOutcomeOf
import androidx.navigation.compose.composable

/**
 * The four trainers that need a microphone, which only Android can build today.
 *
 * They are registered on exactly the same terms as the ones `:shell` carries - the same
 * `lessonStep`, the same routes, the same step-to-step plumbing. The only thing this file adds is
 * turning a lesson step's configuration into each trainer's own preset type, which is work `:shell`
 * cannot do because it also builds for the web. M8 moves all of it into the shell.
 */
val AndroidTrainers = PlatformTrainers(
    ids = setOf(
        TrainerId.FINGERING,
        TrainerId.NOTE_TRAINER,
        TrainerId.SEQUENCE,
        TrainerId.LONG_TONES,
    ),
) { navController, container ->
    composable(SaxTrainerDestination.LONG_TONES) {
        LongToneRoute(
            audioCapture = container.audioCapture,
            newMetronome = container.newMetronome,
            settingsRepository = container.settingsRepository,
            onBack = { navController.popBackStack() },
        )
    }
    lessonStep(
        pattern = SaxTrainerDestination.FINGERING_PATTERN,
        container = container,
        navController = navController,
    ) { lesson, stepIndex, finish ->
        // No lesson argument is the freely configurable Practice entry, which is what every
        // default in FingeringRoute describes.
        FingeringRoute(
            settingsRepository = container.settingsRepository,
            audioCapture = container.audioCapture,
            newMetronome = container.newMetronome,
            preset = lesson?.stepAt(stepIndex)?.let(::fingeringPresetOf),
            onExerciseFinished = { result -> finish(fingeringOutcomeOf(result)) },
            onBack = { navController.popBackStack() },
        )
    }
    lessonStep(
        pattern = SaxTrainerDestination.NOTE_TRAINER_PATTERN,
        container = container,
        navController = navController,
    ) { lesson, stepIndex, finish ->
        NoteTrainerRoute(
            settingsRepository = container.settingsRepository,
            audioCapture = container.audioCapture,
            newMetronome = container.newMetronome,
            preset = lesson?.stepAt(stepIndex)?.let(::noteTrainerPresetOf),
            onExerciseFinished = { result -> finish(noteTrainerOutcomeOf(result)) },
            onBack = { navController.popBackStack() },
        )
    }
    lessonStep(
        pattern = SaxTrainerDestination.SEQUENCE_PATTERN,
        container = container,
        navController = navController,
    ) { lesson, stepIndex, finish ->
        SequenceRoute(
            settingsRepository = container.settingsRepository,
            audioCapture = container.audioCapture,
            newMetronome = container.newMetronome,
            preset = lesson?.stepAt(stepIndex)?.let(::sequencePresetOf),
            onExerciseFinished = { result -> finish(sequenceOutcomeOf(result)) },
            onBack = { navController.popBackStack() },
        )
    }
}

/** The configuration a step carries, as the trainer's own type. Field for field, no decisions. */
private fun fingeringPresetOf(step: LessonStep): FingeringPreset? =
    fingeringLaunchOf(step)?.let {
        FingeringPreset(
            notes = it.notes,
            order = it.order,
            secondsPerNote = it.secondsPerNote,
            rounds = it.rounds,
            checkPlaying = it.checkPlaying,
            noteDisplay = it.noteDisplay,
            autoStart = it.autoStart,
        )
    }

private fun noteTrainerPresetOf(step: LessonStep): NoteTrainerPreset? =
    noteTrainerLaunchOf(step)?.let {
        NoteTrainerPreset(
            notes = it.notes,
            order = it.order,
            rounds = it.rounds,
            showFingeringHint = it.showFingeringHint,
            targetSeconds = it.targetSeconds,
            autoStart = it.autoStart,
        )
    }

private fun sequencePresetOf(step: LessonStep): SequencePreset? =
    sequenceLaunchOf(step)?.let {
        SequencePreset(
            sequenceId = it.sequenceId,
            tempo = it.tempo,
            addInverse = it.addInverse,
            repeats = it.repeats,
            metronome = it.metronome,
            checkPitch = it.checkPitch,
            checkTiming = it.checkTiming,
            autoStart = it.autoStart,
        )
    }
