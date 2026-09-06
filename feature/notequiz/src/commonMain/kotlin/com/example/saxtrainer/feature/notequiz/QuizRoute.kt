package com.example.saxtrainer.feature.notequiz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.saxtrainer.core.settings.ExerciseSetupRepository
import com.example.saxtrainer.core.settings.SettingsRepository

/**
 * Stateful entry point of the note quiz.
 *
 * No microphone and no permission: this is the one practice tool that asks for nothing but a pair
 * of eyes, which is what makes it usable in five spare minutes.
 *
 * A [preset] starts the quiz on a ready-made run instead of the setup screen. Where it came from is
 * not the quiz's concern; [onExerciseFinished] simply reports how it went to whoever asked.
 */
@Composable
fun QuizRoute(
    settingsRepository: SettingsRepository,
    exerciseSetups: ExerciseSetupRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    preset: QuizPreset? = null,
    onExerciseFinished: (QuizResult) -> Unit = {},
    viewModel: QuizViewModel = viewModel {
        QuizViewModel(settingsRepository = settingsRepository, preset = preset)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val startNow by rememberUpdatedState(viewModel::start)
    LaunchedEffect(viewModel) {
        if (viewModel.consumeAutoStart()) startNow()
    }

    // Reported once per finished run: Finished carries the result, so the same value never fires
    // twice.
    val report by rememberUpdatedState(onExerciseFinished)
    LaunchedEffect(state.phase) {
        (state.phase as? QuizPhase.Finished)?.let { report(it.result) }
    }

    // A quiz the player cannot see should not be counting down to its next question.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.stop()
    }

    QuizScreen(
        state = state,
        onSelectNoteSet = viewModel::selectNoteSet,
        onEditCustomNotes = viewModel::editCustomNotes,
        onToggleCustomNote = viewModel::toggleCustomNote,
        onSelectAllCustomNotes = viewModel::selectAllCustomNotes,
        onClearCustomNotes = viewModel::clearCustomNotes,
        onApplyCustomNotes = viewModel::applyCustomNotes,
        onCancelCustomNotes = viewModel::cancelCustomNotes,
        onSelectMode = viewModel::selectMode,
        onSelectQuestionCount = viewModel::selectQuestionCount,
        onSelectSuccessDelay = viewModel::selectSuccessDelaySeconds,
        onAnswer = { slot, option -> viewModel.answer(slot, option) },
        onSkipSuccessDelay = viewModel::skipSuccessDelay,
        onStart = viewModel::start,
        onStop = viewModel::stop,
        onReset = viewModel::reset,
        onBack = onBack,
        modifier = modifier,
    )
}
