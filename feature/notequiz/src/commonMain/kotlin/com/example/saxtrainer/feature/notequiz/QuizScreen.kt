package com.example.saxtrainer.feature.notequiz

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.saxtrainer.core.designsystem.component.PracticeTitleActions
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.ScreenTiming
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.CheckableRow
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.SelectableRow
import com.example.saxtrainer.core.designsystem.component.StepSlider
import com.example.saxtrainer.core.designsystem.component.reservedWrittenStepRange
import com.example.saxtrainer.core.designsystem.component.writtenStepRange
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.designsystem.component.MusicalStaffView
import com.example.saxtrainer.core.designsystem.component.StaffPrefix
import com.example.saxtrainer.core.music.ToneSequence

/**
 * The note quiz, stateless.
 *
 * The question is always a drawing - a note on the staff, or a fingering diagram - and never the
 * name of a note. A name may be an answer to choose between, and is the feedback once the answer
 * is in, but it is never what is being asked.
 */
@Composable
fun QuizScreen(
    state: QuizUiState,
    onSelectNoteSet: (NoteSet) -> Unit,
    onEditCustomNotes: () -> Unit,
    onToggleCustomNote: (SpelledNote) -> Unit,
    onSelectAllCustomNotes: () -> Unit,
    onClearCustomNotes: () -> Unit,
    onApplyCustomNotes: () -> Unit,
    onCancelCustomNotes: () -> Unit,
    onSelectMode: (QuizMode) -> Unit,
    onSelectQuestionCount: (Int) -> Unit,
    onAnswer: (slot: Int, option: Int) -> Unit,
    onSkipSuccessDelay: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToLessons: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: ((Boolean) -> Unit)? = null,
    onAllFingeringsChange: (Boolean) -> Unit = {},
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
        // Starring and saving are about the practice as it is set up, not something to reach for
        // mid-question.
        titleActions = {
            if (!state.isRunning) {
                PracticeTitleActions("Note Quiz", isFavorite, onToggleFavorite, onAddToLessons)
            }
        },
        showTrail = !state.isRunning,
        keepScreenOn = state.isRunning,
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
                    onAllFingeringsChange = onAllFingeringsChange,
                )
            }

            is QuizPhase.Asking -> QuestionPanel(
                state = state,
                question = phase.question,
                chosen = phase.chosen,
                onAnswer = onAnswer,
                onSkipSuccessDelay = onSkipSuccessDelay,
            )

            is QuizPhase.Answered -> QuestionPanel(
                state = state,
                question = phase.question,
                chosen = phase.chosen,
                onAnswer = onAnswer,
                onSkipSuccessDelay = onSkipSuccessDelay,
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
    onAllFingeringsChange: (Boolean) -> Unit,
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
    CheckableRow(
        title = "All fingerings",
        supportingText = "Alternate grips are shown too, and count as right",
        checked = state.allFingerings,
        onCheckedChange = onAllFingeringsChange,
        // Naming a note shows no grip at all, so there is nothing for it to change.
        enabled = state.mode != QuizMode.NOTE_TO_NAME,
    )

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("How many")
    // How long a right answer stays up is no longer asked: it keeps its default, and a tap on the
    // answers still moves on sooner.
    StepSlider(
        title = "Questions",
        value = state.questionCount,
        options = state.questionCounts,
        valueLabel = { "$it" },
        onValueChange = onSelectQuestionCount,
    )

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
 * [chosen] holds one entry per slot: the option taken, or null while that slot is still open. A
 * slot that has been answered is shown as right or wrong straight away; the rest of the question
 * carries on being asked around it.
 */
@Composable
private fun QuestionPanel(
    state: QuizUiState,
    question: QuizQuestion,
    chosen: List<Int?>,
    onAnswer: (slot: Int, option: Int) -> Unit,
    onSkipSuccessDelay: () -> Unit,
) {
    val settled = chosen.all { it != null }
    val allCorrect = settled &&
        chosen.indices.all { chosen[it] == question.answers[it].correctIndex }

    // The whole panel is the button once the question is over and every part of it was right, and
    // only then: a question still open is answered by choosing an option, never by tapping around
    // it, and a wrong answer keeps its own wait because there is something to read in it.
    val skippable = settled && allCorrect

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (skippable) {
                    Modifier.clickable(
                        onClickLabel = "Go on to the next question",
                        onClick = onSkipSuccessDelay,
                    )
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        question.answers.forEachIndexed { slot, answers ->
            AnswerSlotOptions(
                state = state,
                answers = answers,
                chosen = chosen[slot],
                onAnswer = { option -> onAnswer(slot, option) },
            )
        }

        // The combined mode has just had the player name the note themselves, next to the note
        // itself: saying it again underneath would be telling them what they have just said.
        if (settled && state.mode != QuizMode.NOTE_AND_FINGERING) {
            // The name of the note appears here and nowhere else as feedback: it is what the
            // question was about, never the question itself. It is worth saying wherever the player
            // was not asked for the name - most of all in the mode that shows only a grip.
            Text(
                text = if (allCorrect) {
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

/** One slot's options, drawn as whatever kind of thing that slot is asking the player to choose. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerSlotOptions(
    state: QuizUiState,
    answers: QuizAnswers,
    chosen: Int?,
    onAnswer: (Int) -> Unit,
) {
    when (answers) {
        is FingeringAnswers -> FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            answers.options.forEachIndexed { option, fingering ->
                AnswerOption(
                    onClick = { onAnswer(option) },
                    state = stateOf(answers, option, chosen),
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

        is NameAnswers -> NameOptions(state, answers, chosen, onAnswer)

        is StaffNoteAnswers -> {
            // Every option is drawn on a staff of the same height, so a high answer and a low
            // one line up and the row does not jump as the question changes.
            val extent = writtenStepRange(answers.options)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                answers.options.forEachIndexed { option, note ->
                    AnswerOption(
                        onClick = { onAnswer(option) },
                        state = stateOf(answers, option, chosen),
                        contentDescription = state.nameOf(note),
                    ) {
                        MusicalNoteView(
                            written = note,
                            lineSpacing = OptionStaffSpacing,
                            stepRange = extent,
                            primaryColor = MaterialTheme.colorScheme.onSurface,
                            staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Names to choose between.
 *
 * Enough of them to fill even rows are laid out as a grid, which is what ten of them are for: five
 * across and two down reads as a keyboard of names rather than as a paragraph. Fewer than that -
 * which only happens when the note pool cannot fill the ten - flow instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameOptions(
    state: QuizUiState,
    answers: NameAnswers,
    chosen: Int?,
    onAnswer: (Int) -> Unit,
) {
    val options = answers.options
    if (options.size >= NameGridColumns * 2) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            options.withIndex().chunked(NameGridColumns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                ) {
                    row.forEach { (option, note) ->
                        AnswerOption(
                            onClick = { onAnswer(option) },
                            modifier = Modifier.weight(1f),
                            state = stateOf(answers, option, chosen),
                        ) {
                            Text(
                                text = state.nameOf(note),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            options.forEachIndexed { option, note ->
                AnswerOption(
                    onClick = { onAnswer(option) },
                    state = stateOf(answers, option, chosen),
                ) {
                    Text(
                        text = state.nameOf(note),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = OptionNameWidth),
                    )
                }
            }
        }
    }
}

/** Neutral until that slot is answered; then the right one and, if it differs, the one tapped. */
private fun stateOf(answers: QuizAnswers, option: Int, chosen: Int?): AnswerState = when {
    chosen == null -> AnswerState.NEUTRAL
    option == answers.correctIndex -> AnswerState.CORRECT
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

/** Wide enough that a bare name is still a comfortable target, and that the grid stays even. */
private val OptionNameWidth = 44.dp

/** How many names sit side by side once there are enough of them to fill even rows. */
private const val NameGridColumns = 5

@Preview(showBackground = true)
@Composable
private fun QuizSetupPreview() {
    SaxTrainerTheme {
        QuizScreen(
            state = QuizUiState(
                activeNotes = listOf(SpelledNote.of(Note(com.example.saxtrainer.core.music.NoteName.C, 4))),
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
            onAnswer = { _, _ -> },
            onSkipSuccessDelay = {},
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
        "See a note and pick its fingering or its name, or a fingering and pick its note. " +
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
private fun QuestionSubject(
    question: QuizQuestion,
    pool: List<SpelledNote>,
) {
    when (question) {
        // Sized for every note the quiz can ask about, so the band does not change height when a
        // note that hangs below the staff comes up.
        is NoteToFingering, is NoteToName, is NoteAndFingering -> MusicalStaffView(
            sequence = ToneSequence.of(question.note),
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
            lineSpacing = PromptStaffSpacing,
            prefix = StaffPrefix.Treble,
            fillWidth = true,
            // A question is one note held up to be read, not a piece of music: no closing bar, and
            // the same room kept for an accidental whichever note is asked, so it does not shift.
            closingBar = false,
            centerMusic = true,
            reserveAccidentals = true,
            stepRange = reservedWrittenStepRange(pool.ifEmpty { listOf(question.note) }),
        )

        is FingeringToNote -> SaxophoneFingeringView(
            fingering = question.fingering,
            width = PromptFingeringWidth,
            pressedColor = MaterialTheme.colorScheme.primary,
            contentDescription = question.prompt,
        )
    }
}
