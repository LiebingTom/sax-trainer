package com.example.saxtrainer.feature.phrasequiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.component.AnswerOption
import com.example.saxtrainer.core.designsystem.component.BoundedMusicalPage
import com.example.saxtrainer.core.designsystem.component.CheckableRow
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.core.designsystem.component.InlineIcon
import com.example.saxtrainer.core.designsystem.component.MusicalNoteView
import com.example.saxtrainer.core.designsystem.component.PhraseLibraryWindow
import com.example.saxtrainer.core.designsystem.component.PracticeTitleActions
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.ScalePatternWindow
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.ScreenTiming
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.StepSlider
import com.example.saxtrainer.core.designsystem.component.octaveSummary
import com.example.saxtrainer.core.designsystem.component.reservedWrittenStepRange
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.icon.Undo as UndoIcon
import com.example.saxtrainer.core.designsystem.text.toFixed
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.FeedbackColors
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import com.example.saxtrainer.core.phrase.Phrase
import com.example.saxtrainer.core.phrase.PhraseSourceType

/**
 * The phrase quiz, stateless.
 *
 * A unit is drawn once, taken away, and then rebuilt from its own notes shuffled into the row
 * below: what is asked is always an order, drawn from the same notes as the answer, never a choice
 * between several different phrases.
 */
@Composable
fun PhraseQuizScreen(
    state: PhraseQuizUiState,
    onOpenLibraryPicker: () -> Unit,
    onCloseLibraryPicker: () -> Unit,
    onSelectLibraryPhrase: (String) -> Unit,
    onOpenPatternPicker: () -> Unit,
    onClosePatternPicker: () -> Unit,
    onSelectScaleChoice: (String) -> Unit,
    onToggleOctave: (Int) -> Unit,
    onSelectPattern: (String) -> Unit,
    onSelectRepeats: (Int) -> Unit,
    onSetMixed: (Boolean) -> Unit,
    onShowSecondsChange: (Double) -> Unit,
    onChoose: (Int) -> Unit,
    onUndo: () -> Unit,
    onRevealNow: () -> Unit,
    onNext: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToLessons: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: ((Boolean) -> Unit)? = null,
) {
    val subject: (@Composable ColumnScope.() -> Unit)? = when (val phase = state.phase) {
        is PhraseQuizPhase.Showing -> { { ShowingSubject(state, phase) } }
        is PhraseQuizPhase.Asking -> { { AskingSubject(state, phase) } }
        is PhraseQuizPhase.Answered -> { { AnsweredSubject(state, phase) } }
        else -> null
    }

    ScreenScaffold(
        title = "Phrase Quiz",
        onBack = onBack,
        modifier = modifier,
        // Starring and saving are about the practice as it is set up, not something to reach for
        // mid-unit.
        titleActions = {
            if (!state.isRunning) {
                PracticeTitleActions("Phrase Quiz", isFavorite, onToggleFavorite, onAddToLessons)
            }
        },
        showTrail = !state.isRunning,
        keepScreenOn = state.isRunning,
        header = headerOf(state),
        subject = subject,
        timing = timingOf(state),
        actions = { Actions(state, onStart, onStop, onReset, onNext, onRevealNow) },
    ) {
        Spacer(Modifier.height(Dimens.spaceSm))

        when (val phase = state.phase) {
            is PhraseQuizPhase.Idle -> if (state.isPreconfigured) {
                ReadyPanel(state)
            } else {
                Setup(
                    state = state,
                    onOpenLibraryPicker = onOpenLibraryPicker,
                    onOpenPatternPicker = onOpenPatternPicker,
                    onSelectRepeats = onSelectRepeats,
                    onSetMixed = onSetMixed,
                    onShowSecondsChange = onShowSecondsChange,
                )
            }

            is PhraseQuizPhase.Showing -> Text(
                text = "Watch closely - it is about to disappear.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            is PhraseQuizPhase.Asking -> OptionsPanel(phase.options, phase.chosen, enabled = true, onChoose, onUndo)

            // Settled and left up until the player is ready - see Actions, where "Next" replaces
            // "Stop" here: nothing moves on by itself.
            is PhraseQuizPhase.Answered -> OptionsPanel(phase.options, phase.chosen, enabled = false, onChoose, onUndo)

            is PhraseQuizPhase.Finished -> ResultCard(phase.result)
        }
    }

    if (state.libraryPickerOpen) {
        PhraseLibraryWindow(
            phrases = state.library,
            selectedId = state.libraryPhraseId.takeIf { state.sourceType == PhraseSourceType.LIBRARY },
            onSelect = onSelectLibraryPhrase,
            onDismiss = onCloseLibraryPicker,
        )
    }
    if (state.patternPickerOpen) {
        ScalePatternWindow(
            patterns = state.patterns,
            selectedPatternId = state.patternId.takeIf { state.sourceType == PhraseSourceType.SCALE_PATTERN },
            scaleChoices = state.scaleChoices,
            scaleChoiceId = state.scaleChoiceId,
            octaves = state.octaves,
            octaveRange = PhraseQuizUiState.OCTAVE_RANGE,
            onSelectScaleChoice = onSelectScaleChoice,
            onToggleOctave = onToggleOctave,
            onSelectPattern = onSelectPattern,
            onDismiss = onClosePatternPicker,
        )
    }
}

/**
 * The setup screen proper: two entries, each opening its own window rather than changing what is
 * shown here - see [PhraseLibraryWindow] and [ScalePatternWindow]. Neither window can be used to
 * edit a phrase or a pattern; both are plain lists to choose from.
 */
@Composable
private fun Setup(
    state: PhraseQuizUiState,
    onOpenLibraryPicker: () -> Unit,
    onOpenPatternPicker: () -> Unit,
    onSelectRepeats: (Int) -> Unit,
    onSetMixed: (Boolean) -> Unit,
    onShowSecondsChange: (Double) -> Unit,
) {
    SectionHeader("Source")
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        SourceButton(
            text = "Phrase library",
            selected = state.sourceType == PhraseSourceType.LIBRARY,
            onClick = onOpenLibraryPicker,
            modifier = Modifier.weight(1f),
        )
        SourceButton(
            text = "Scale pattern",
            selected = state.sourceType == PhraseSourceType.SCALE_PATTERN,
            onClick = onOpenPatternPicker,
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    SelectionSummary(state)

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("How much")
    StepSlider(
        title = "Repeat",
        supportingText = "${state.repeats} time${if (state.repeats == 1) "" else "s"} through, " +
            "${state.plannedUnitCount} unit${if (state.plannedUnitCount == 1) "" else "s"} in all",
        value = state.repeats,
        options = state.repeatRange.toList(),
        valueLabel = { "$it" },
        onValueChange = onSelectRepeats,
    )
    CheckableRow(
        title = "Mixed order",
        supportingText = "Shuffle the units instead of playing them in order - across every " +
            "repeat together, not one pass at a time.",
        checked = state.mixed,
        onCheckedChange = onSetMixed,
    )
    StepSlider(
        title = "Preview time",
        supportingText = if (state.showSeconds.isFinite()) {
            "How long a unit stays on screen before it disappears"
        } else {
            "Stays on screen until you press Ready"
        },
        value = state.showSeconds,
        options = PhraseQuizUiState.SHOW_SECONDS_OPTIONS,
        valueLabel = { if (it.isFinite()) "${it.toFixed(1)} s" else "Until ready" },
        onValueChange = onShowSecondsChange,
    )

    if (!state.canStart) {
        Spacer(Modifier.height(Dimens.spaceSm))
        Text(
            text = "Choose a phrase to practice.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * What is currently selected, said plainly: a name, where it came from, and how the quiz will walk
 * through it - so the player knows what "Start" is about to run without opening either window.
 */
@Composable
private fun SelectionSummary(state: PhraseQuizUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
        ) {
            val phrase = state.phrase
            if (phrase == null) {
                Text(text = "Nothing chosen yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Open a phrase library or a scale pattern above to pick what to practice.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(text = phrase.name, style = MaterialTheme.typography.titleMedium)
                val detail = when (state.sourceType) {
                    PhraseSourceType.LIBRARY -> phrase.description.takeIf { it.isNotBlank() }
                    PhraseSourceType.SCALE_PATTERN -> state.scaleChoice
                        ?.let { choice -> "${state.pattern?.name} in ${choice.displayName}, octave ${octaveSummary(state.octaves)}" }
                }
                detail?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val unitCount = state.plannedUnitCount
                if (unitCount > 0) {
                    Text(
                        text = "$unitCount unit${if (unitCount == 1) "" else "s"} to rebuild, " +
                            "${phrase.trainingBars} bar${if (phrase.trainingBars == 1) "" else "s"} at a time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One of the two source buttons: filled while it is the current source, outlined otherwise. */
@Composable
private fun SourceButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (selected) {
        PrimaryButton(text = text, onClick = onClick, modifier = modifier)
    } else {
        SecondaryButton(text = text, onClick = onClick, modifier = modifier)
    }
}

/**
 * The unit's notes, shuffled among near-pitch decoys, tapped back into order. Used notes stay in
 * place but go inert - unless [onUndo] is reached for, which frees the last one placed and puts it
 * back among the choices. Enough of them to fill even rows lay out as a fixed grid - see
 * [OptionGridColumns] - the same "keyboard" reading the note quiz's own name grid uses; a sparse
 * unit flows instead. Either way the grid opens on an undo tile - the same size as a note - rather
 * than starting straight on the first choice, so taking a note back is always the same reach.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsPanel(
    options: List<ToneStep>,
    chosen: List<Int>,
    enabled: Boolean,
    onChoose: (Int) -> Unit,
    onUndo: () -> Unit,
) {
    val extent = reservedNotesExtent(options.map { it.written })
    val canUndo = enabled && chosen.isNotEmpty()
    val cells = buildList<@Composable (Modifier) -> Unit> {
        add { modifier -> UndoTile(canUndo = canUndo, onUndo = onUndo, modifier = modifier) }
        options.forEachIndexed { index, step ->
            add { modifier ->
                OptionTile(step, extent, used = index in chosen, enabled = enabled, modifier = modifier) {
                    onChoose(index)
                }
            }
        }
    }

    if (cells.size >= OptionGridColumns * 2) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
            cells.chunked(OptionGridColumns).forEach { row ->
                // Every tile in a row stretches to the tallest one in it - the undo tile included -
                // so a plain icon reads as the same size button as a note glyph beside it.
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                ) {
                    row.forEach { cell -> cell(Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            cells.forEach { cell -> cell(Modifier) }
        }
    }
}

/** Takes back the last note placed - drawn the same size as a note tile, so the grid keeps its shape. */
@Composable
private fun UndoTile(canUndo: Boolean, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    AnswerOption(onClick = onUndo, modifier = modifier, enabled = canUndo, contentDescription = "Undo the last note") {
        InlineIcon(
            IconSource.Vector(SaxTrainerIcons.UndoIcon),
            contentDescription = null,
            modifier = Modifier.size(UndoIconTileSize),
        )
    }
}

@Composable
private fun OptionTile(
    step: ToneStep,
    extent: IntRange,
    used: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AnswerOption(onClick = onClick, modifier = modifier, enabled = enabled && !used, selected = used) {
        // Drawn as the phrase writes it, never respelled: an E flat tile is an E flat.
        val note = step.written
        if (note == null) {
            Text(text = "Rest", style = MaterialTheme.typography.titleMedium)
        } else {
            MusicalNoteView(
                written = note,
                lineSpacing = OptionSpacing,
                stepRange = extent,
                primaryColor = if (used) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** The correct order, drawn as one continuous line of music - what is about to be taken away. */
@Composable
private fun ShowingSubject(state: PhraseQuizUiState, phase: PhraseQuizPhase.Showing) {
    val phrase = state.phrase ?: return
    BoundedMusicalPage(
        sequence = ToneSequence(phase.correct, phrase.timeSignature, phrase.accidentalStyle),
        modifier = Modifier.fillMaxWidth(),
        naming = state.noteNaming,
        octaves = state.octaveNumbering,
        color = MaterialTheme.colorScheme.primary,
        staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** What has been tapped so far, with a rest for every note still waiting to be placed. */
@Composable
private fun AskingSubject(state: PhraseQuizUiState, phase: PhraseQuizPhase.Asking) {
    val phrase = state.phrase ?: return
    val built = padTo(phase.builtNotes, phase.correct.size)
    BoundedMusicalPage(
        sequence = ToneSequence(builtSteps(phase.correct, built), phrase.timeSignature, phrase.accidentalStyle),
        modifier = Modifier.fillMaxWidth(),
        naming = state.noteNaming,
        octaves = state.octaveNumbering,
        color = MaterialTheme.colorScheme.primary,
        staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The order as it was built, coloured note by note against the order it should have been - and
 * beside every wrong one, the note that should have been there instead, in the same green a right
 * answer gets: a red note alone says only that something was wrong, not what to have played.
 */
@Composable
private fun AnsweredSubject(state: PhraseQuizUiState, phase: PhraseQuizPhase.Answered) {
    val phrase = state.phrase ?: return
    val correctNotes = phase.correct.map { it.written }
    val correctColor = FeedbackColors.correct
    val incorrectColor = FeedbackColors.incorrect
    BoundedMusicalPage(
        sequence = ToneSequence(builtSteps(phase.correct, phase.builtNotes), phrase.timeSignature, phrase.accidentalStyle),
        modifier = Modifier.fillMaxWidth(),
        naming = state.noteNaming,
        octaves = state.octaveNumbering,
        color = MaterialTheme.colorScheme.primary,
        staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
        noteColor = { index ->
            if (phase.builtNotes.getOrNull(index) == correctNotes.getOrNull(index)) correctColor else incorrectColor
        },
        secondaryWritten = { index ->
            correctNotes.getOrNull(index)?.takeIf { it != phase.builtNotes.getOrNull(index) }
        },
        secondaryColor = correctColor,
    )
}

/** [correct]'s own durations, paired with whichever notes have been placed so far, as written - a
 * rest stands in for a slot nobody has filled yet. */
private fun builtSteps(correct: List<ToneStep>, built: List<SpelledNote?>): List<ToneStep> =
    correct.mapIndexed { index, step ->
        built.getOrNull(index)?.let { ToneStep.of(it, step.duration) } ?: ToneStep.rest(step.duration)
    }

/** What a ready-made quiz is about to ask. There is nothing to change; it was decided elsewhere. */
@Composable
private fun ReadyPanel(state: PhraseQuizUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            Text(text = "Ready", style = MaterialTheme.typography.titleMedium)
            state.phrase?.let { phrase ->
                Text(
                    text = phrase.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.units.isNotEmpty()) {
                Text(
                    text = "${state.plannedUnitCount} to rebuild",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(result: PhraseQuizResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
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
                text = "${result.correctCount} of ${result.attempted.size} rebuilt correctly",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Actions(
    state: PhraseQuizUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onNext: () -> Unit,
    onRevealNow: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        when (state.phase) {
            is PhraseQuizPhase.Idle -> PrimaryButton(
                text = "Start",
                onClick = onStart,
                enabled = state.canStart,
                modifier = Modifier.weight(1f),
            )

            // "Ready" cuts the preview short, whether it is about to end on its own or - shown
            // until pressed - never would: either way, seen enough is seen enough.
            is PhraseQuizPhase.Showing -> {
                SecondaryButton(text = "Stop", onClick = onStop, modifier = Modifier.weight(1f))
                SecondaryButton(text = "Ready", onClick = onRevealNow, modifier = Modifier.weight(1f))
            }

            is PhraseQuizPhase.Asking ->
                SecondaryButton(text = "Stop", onClick = onStop, modifier = Modifier.weight(1f))

            // Settled, right or wrong: the player decides when to move on, not a timer.
            is PhraseQuizPhase.Answered ->
                PrimaryButton(text = "Next", onClick = onNext, modifier = Modifier.weight(1f))

            is PhraseQuizPhase.Finished -> {
                PrimaryButton(
                    text = "Again",
                    onClick = onStart,
                    enabled = state.canStart,
                    modifier = Modifier.weight(1f),
                )
                if (!state.isPreconfigured) {
                    SecondaryButton(text = "Change phrase", onClick = onReset, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun reservedNotesExtent(notes: List<SpelledNote?>): IntRange =
    reservedWrittenStepRange(notes.filterNotNull().ifEmpty { listOf(SpelledNote.of(Note(NoteName.C, 4))) })

/** [notes] followed by enough nulls to reach [size] - the slots still waiting to be filled. */
private fun padTo(notes: List<SpelledNote?>, size: Int): List<SpelledNote?> =
    if (notes.size >= size) notes else notes + List(size - notes.size) { null }

private val OptionSpacing = 9.dp

/** How many option tiles sit side by side once there are enough of them to fill even rows. */
private const val OptionGridColumns = 6

/** About as tall as a note tile's own glyph, so the undo tile reads as one of the row. */
private val UndoIconTileSize = 28.dp

/**
 * What the screen says for itself, above the rule at the top.
 *
 * While it is being set up it says what the practice is; once it is running it asks for what the
 * player should be doing right now.
 */
private fun headerOf(state: PhraseQuizUiState): ScreenHeader = when (state.phase) {
    is PhraseQuizPhase.Showing -> ScreenHeader.Question("Listen and watch")
    is PhraseQuizPhase.Asking, is PhraseQuizPhase.Answered ->
        ScreenHeader.Question("Tap the notes back into order")
    is PhraseQuizPhase.Finished -> ScreenHeader.Question("How did that go?")
    PhraseQuizPhase.Idle -> ScreenHeader.Description(
        "See a phrase, then rebuild it from memory by tapping its notes back into order. " +
            "No instrument needed.",
    )
}

/** How far through the phrase's units, for the bar across the foot of the screen. */
private fun timingOf(state: PhraseQuizUiState): ScreenTiming? {
    val plannedCount = state.plannedUnitCount.takeIf { it > 0 } ?: return null
    val step = when (val phase = state.phase) {
        is PhraseQuizPhase.Showing -> phase.unitIndex + 1
        is PhraseQuizPhase.Asking -> phase.unitIndex + 1
        is PhraseQuizPhase.Answered -> phase.unitIndex + 1
        else -> return null
    }
    return ScreenTiming(
        fraction = ((step - 1).toFloat() / plannedCount).coerceIn(0f, 1f),
        step = step,
        stepCount = plannedCount,
    )
}

@Preview(showBackground = true)
@Composable
private fun PhraseQuizSetupPreview() {
    SaxTrainerTheme {
        PhraseQuizScreen(
            state = PhraseQuizUiState(
                library = com.example.saxtrainer.core.phrase.PhraseLibrary.all,
                libraryPhraseId = com.example.saxtrainer.core.phrase.PhraseLibrary.all.first().id,
            ),
            onOpenLibraryPicker = {},
            onCloseLibraryPicker = {},
            onSelectLibraryPhrase = {},
            onOpenPatternPicker = {},
            onClosePatternPicker = {},
            onSelectScaleChoice = {},
            onToggleOctave = {},
            onSelectPattern = {},
            onSelectRepeats = {},
            onSetMixed = {},
            onShowSecondsChange = {},
            onChoose = {},
            onUndo = {},
            onRevealNow = {},
            onNext = {},
            onStart = {},
            onStop = {},
            onReset = {},
            onBack = {},
        )
    }
}
