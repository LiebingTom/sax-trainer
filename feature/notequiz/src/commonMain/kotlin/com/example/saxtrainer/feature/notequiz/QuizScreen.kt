package com.example.saxtrainer.feature.notequiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.component.AnswerOption
import com.example.saxtrainer.core.designsystem.component.AnswerState
import com.example.saxtrainer.core.designsystem.component.MusicalNoteView
import com.example.saxtrainer.core.designsystem.component.NoteChoiceDialog
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.SaxophoneFingeringView
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.ScreenTiming
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.SelectableRow
import com.example.saxtrainer.core.designsystem.component.reservedStepRange
import com.example.saxtrainer.core.designsystem.component.staffStepRange
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.conventionalStyle

/**
 * The note quiz, stateless.
 *
 * The question is always a drawing - a note on the staff, or a fingering diagram - and never the
 * name of a note. The name appears once the answer is in, as feedback.
 */
@Composable
fun QuizScreen(
    state: QuizUiState,
    onSelectNoteSet: (NoteSet) -> Unit,
    onEditCustomNotes: () -> Unit,
    onToggleCustomNote: (Note) -> Unit,
    onSelectAllCustomNotes: () -> Unit,
    onClearCustomNotes: () -> Unit,
    onApplyCustomNotes: () -> Unit,
    onCancelCustomNotes: () -> Unit,
    onSelectMode: (QuizMode) -> Unit,
    onSelectQuestionCount: (Int) -> Unit,
    onAnswer: (Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // What is being asked about, kept apart from the answers to it.
    val asked = when (val phase = state.phase) {
        is QuizPhase.Asking -> phase.question
        is QuizPhase.Answered -> phase.question
        else -> null
    }
    val subject: (@Composable ColumnScope.() -> Unit)? =
        if (asked == null) null else ({ QuestionSubject(asked, state.activeNotes) })

    ScreenScaffold(
        title = "Note Quiz",
        onBack = onBack,
        modifier = modifier,
        header = headerOf(state),
        subject = subject,
        timing = timingOf(state),
        actions = {
            Actions(state, onStart, onStop, onReset)
        },
    ) {
        Spacer(Modifier.height(Dimens.spaceSm))

        when (val phase = state.phase) {
            is QuizPhase.Idle -> if (state.isPreconfigured) {
                ReadyPanel(state)
            } else {
                Setup(
                    state = state,
                    onSelectNoteSet = onSelectNoteSet,
                    onEditCustomNotes = onEditCustomNotes,
                    onSelectMode = onSelectMode,
                    onSelectQuestionCount = onSelectQuestionCount,
                )
            }

            is QuizPhase.Asking -> QuestionPanel(
                state = state,
                question = phase.question,
                index = phase.index,
                chosen = null,
                onAnswer = onAnswer,
            )

            is QuizPhase.Answered -> QuestionPanel(
                state = state,
                question = phase.question,
                index = phase.index,
                chosen = phase.chosen,
                onAnswer = onAnswer,
            )

            is QuizPhase.Finished -> ResultCard(state, phase.result)
        }

        if (state.isEditingCustomNotes) {
            NoteChoiceDialog(
                notes = state.availableNotes,
                chosen = state.customNotesDraft.orEmpty(),
                label = state::nameOf,
                onToggle = onToggleCustomNote,
                onSelectAll = onSelectAllCustomNotes,
                onClearAll = onClearCustomNotes,
                onApply = onApplyCustomNotes,
                onCancel = onCancelCustomNotes,
            )
        }
    }
}

@Composable
private fun Setup(
    state: QuizUiState,
    onSelectNoteSet: (NoteSet) -> Unit,
    onEditCustomNotes: () -> Unit,
    onSelectMode: (QuizMode) -> Unit,
    onSelectQuestionCount: (Int) -> Unit,
) {
    SectionHeader("Notes")
    state.noteSets.forEach { set ->
        SelectableRow(
            title = set.displayName,
            supportingText = state.summaryOf(set, SaxophoneFingerings),
            selected = set == state.noteSet,
            onClick = {
                if (set == NoteSet.CUSTOM) onEditCustomNotes() else onSelectNoteSet(set)
            },
        )
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("Question")
    state.modes.forEach { mode ->
        SelectableRow(
            title = mode.displayName,
            supportingText = mode.description,
            selected = mode == state.mode,
            onClick = { onSelectMode(mode) },
        )
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("How many")
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        state.questionCounts.forEach { count ->
            FilterChip(
                selected = count == state.questionCount,
                onClick = { onSelectQuestionCount(count) },
                label = { Text("$count") },
            )
        }
    }

    if (!state.canStart) {
        Spacer(Modifier.height(Dimens.spaceSm))
        Text(
            text = "Choose at least ${QuizQuestionGenerator.MIN_OPTIONS} notes: a question needs " +
                "something to choose between.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * The question and its answers.
 *
 * [chosen] is null while the question is still open and holds the tapped option once it is
 * answered, which is the only difference between the two phases on screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionPanel(
    state: QuizUiState,
    question: QuizQuestion,
    index: Int,
    chosen: Int?,
    onAnswer: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        when (question) {
            is NoteToFingering -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                ) {
                    question.options.forEachIndexed { option, fingering ->
                        AnswerOption(
                            onClick = { onAnswer(option) },
                            state = stateOf(question, option, chosen),
                            contentDescription = "Fingering ${option + 1}",
                        ) {
                            SaxophoneFingeringView(
                                fingering = fingering,
                                width = OptionFingeringWidth,
                                pressedColor = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            is FingeringToNote -> {
                // Every option is drawn on a staff of the same height, so a high answer and a low
                // one line up and the row does not jump as the question changes.
                val extent = staffStepRange(question.options, Note::conventionalStyle)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                ) {
                    question.options.forEachIndexed { option, note ->
                        AnswerOption(
                            onClick = { onAnswer(option) },
                            state = stateOf(question, option, chosen),
                            contentDescription = state.nameOf(note),
                        ) {
                            MusicalNoteView(
                                primaryNote = note,
                                lineSpacing = OptionStaffSpacing,
                                accidentalStyle = note.conventionalStyle(),
                                stepRange = extent,
                                primaryColor = MaterialTheme.colorScheme.onSurface,
                                staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (chosen != null) {
            // The name of the note appears here and nowhere else: it is the feedback, never the
            // question.
            Text(
                text = if (chosen == question.correctIndex) {
                    "Yes - ${state.nameOf(question.note)}"
                } else {
                    "That was ${state.nameOf(question.note)}"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Neutral until an answer is in; then the right one and, if it differs, the one that was tapped. */
private fun stateOf(question: QuizQuestion, option: Int, chosen: Int?): AnswerState = when {
    chosen == null -> AnswerState.NEUTRAL
    option == question.correctIndex -> AnswerState.CORRECT
    option == chosen -> AnswerState.INCORRECT
    else -> AnswerState.NEUTRAL
}

/** What a ready-made quiz is about to ask. There is nothing to change; it was decided elsewhere. */
@Composable
private fun ReadyPanel(state: QuizUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            Text(text = "Ready", style = MaterialTheme.typography.titleMedium)
            Text(
                text = state.activeNotes.joinToString(" ") { state.nameOf(it) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${state.questionCount} questions, ${state.mode.displayName.lowercase()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(state: QuizUiState, result: QuizResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            Text(
                text = if (result.completed) "Quiz complete" else "Stopped early",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${result.correctCount} of ${result.answers.size} right",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (result.missedNotes.isNotEmpty()) {
                Text(
                    text = "Worth another look",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                    result.missedNotes.forEach { note ->
                        Text(
                            text = state.nameOf(note),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Actions(
    state: QuizUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        when (state.phase) {
            is QuizPhase.Idle -> PrimaryButton(
                text = "Start",
                onClick = onStart,
                enabled = state.canStart,
                modifier = Modifier.weight(1f),
            )

            is QuizPhase.Asking, is QuizPhase.Answered -> SecondaryButton(
                text = "Stop",
                onClick = onStop,
                modifier = Modifier.weight(1f),
            )

            is QuizPhase.Finished -> {
                PrimaryButton(
                    text = "Again",
                    onClick = onStart,
                    enabled = state.canStart,
                    modifier = Modifier.weight(1f),
                )
                if (!state.isPreconfigured) {
                    SecondaryButton(
                        text = "Change notes",
                        onClick = onReset,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val PromptStaffSpacing = 16.dp
private val OptionStaffSpacing = 9.dp
private val PromptFingeringWidth = 110.dp
private val OptionFingeringWidth = 64.dp

@Preview(showBackground = true)
@Composable
private fun QuizSetupPreview() {
    SaxTrainerTheme {
        QuizScreen(
            state = QuizUiState(
                activeNotes = listOf(Note(com.example.saxtrainer.core.music.NoteName.C, 4)),
                questionCounts = listOf(10, 20, 30),
            ),
            onSelectNoteSet = {},
            onEditCustomNotes = {},
            onToggleCustomNote = {},
            onSelectAllCustomNotes = {},
            onClearCustomNotes = {},
            onApplyCustomNotes = {},
            onCancelCustomNotes = {},
            onSelectMode = {},
            onSelectQuestionCount = {},
            onAnswer = {},
            onStart = {},
            onStop = {},
            onReset = {},
            onBack = {},
        )
    }
}

/**
 * What the screen says for itself, above the rule at the top.
 *
 * While it is being set up it says what the practice is, in plain words; once it is running it asks
 * for what the player should be doing right now.
 */
private fun headerOf(state: QuizUiState): ScreenHeader = when (val phase = state.phase) {
    is QuizPhase.Asking -> ScreenHeader.Question(phase.question.prompt)
    is QuizPhase.Answered -> ScreenHeader.Question(phase.question.prompt)
    is QuizPhase.Finished -> ScreenHeader.Question("How did that go?")
    QuizPhase.Idle -> ScreenHeader.Description(
        "See a note and pick its fingering, or a fingering and pick its note. " +
            "No instrument needed.",
    )
}

/**
 * How far through the quiz, for the bar across the foot of the screen.
 *
 * Nothing here is timed - a question waits as long as it takes - so the bar measures the run
 * instead, and there is no second one underneath saying the same thing twice.
 */
private fun timingOf(state: QuizUiState): ScreenTiming? {
    val step = when (val phase = state.phase) {
        is QuizPhase.Asking -> phase.index + 1
        is QuizPhase.Answered -> phase.index + 1
        else -> return null
    }

    return ScreenTiming(
        fraction = sessionFractionOf(step, state.questionCount),
        step = step,
        stepCount = state.questionCount,
    )
}

/**
 * How far through the whole run: steps finished over steps planned.
 *
 * Counted as steps *finished*, so the first one reads as nothing done rather than as a slice
 * already gone.
 */
private fun sessionFractionOf(step: Int, stepCount: Int): Float =
    if (stepCount <= 0) 0f else ((step - 1).toFloat() / stepCount).coerceIn(0f, 1f)

/**
 * The thing the question is about: a note to find a grip for, or a grip to name.
 *
 * Kept out of the panel below it so the question and the answers to it never run together.
 */
@Composable
private fun QuestionSubject(question: QuizQuestion, pool: List<Note>) {
    when (question) {
        // Sized for every note the quiz can ask about, so the band does not change height when a
        // note that hangs below the staff comes up.
        is NoteToFingering -> MusicalNoteView(
            primaryNote = question.note,
            lineSpacing = PromptStaffSpacing,
            accidentalStyle = question.note.conventionalStyle(),
            stepRange = reservedStepRange(pool.ifEmpty { listOf(question.note) }),
            primaryColor = MaterialTheme.colorScheme.primary,
            staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is FingeringToNote -> SaxophoneFingeringView(
            fingering = question.fingering,
            width = PromptFingeringWidth,
            pressedColor = MaterialTheme.colorScheme.primary,
            contentDescription = question.prompt,
        )
    }
}
