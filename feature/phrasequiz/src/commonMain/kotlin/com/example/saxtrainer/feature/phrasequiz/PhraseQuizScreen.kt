package com.example.saxtrainer.feature.phrasequiz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.saxtrainer.core.designsystem.component.AnswerOption
import com.example.saxtrainer.core.designsystem.component.CheckableRow
import com.example.saxtrainer.core.designsystem.component.FieldRow
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.core.designsystem.component.InlineIcon
import com.example.saxtrainer.core.designsystem.component.MusicalNoteView
import com.example.saxtrainer.core.designsystem.component.MusicalStaffView
import com.example.saxtrainer.core.designsystem.component.NotesPreviewDialog
import com.example.saxtrainer.core.designsystem.component.PracticeTitleActions
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.ScreenTiming
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.SelectableCard
import com.example.saxtrainer.core.designsystem.component.SelectableRow
import com.example.saxtrainer.core.designsystem.component.StaffPrefix
import com.example.saxtrainer.core.designsystem.component.reservedStepRange
import com.example.saxtrainer.core.designsystem.icon.ArrowBack
import com.example.saxtrainer.core.designsystem.icon.Preview as PreviewIcon
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.FeedbackColors
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import com.example.saxtrainer.core.music.conventionalStyle
import com.example.saxtrainer.core.phrase.Phrase
import com.example.saxtrainer.core.phrase.PhraseRecipe
import com.example.saxtrainer.core.phrase.PhraseSourceType
import com.example.saxtrainer.core.phrase.ScaleChoice

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
    onChoose: (Int) -> Unit,
    onSkipRevealDelay: () -> Unit,
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
        header = headerOf(state),
        subject = subject,
        timing = timingOf(state),
        actions = { Actions(state, onStart, onStop, onReset) },
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(Dimens.spaceSm))

        when (val phase = state.phase) {
            is PhraseQuizPhase.Idle -> if (state.isPreconfigured) {
                ReadyPanel(state)
            } else {
                Setup(state = state, onOpenLibraryPicker = onOpenLibraryPicker, onOpenPatternPicker = onOpenPatternPicker)
            }

            is PhraseQuizPhase.Showing -> Text(
                text = "Watch closely - it is about to disappear.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is PhraseQuizPhase.Asking -> OptionsPanel(phase.options, phase.chosen, enabled = true, onChoose)

            is PhraseQuizPhase.Answered -> {
                // The whole panel becomes the button once the order was right, exactly as the note
                // quiz's own settled question does: a wrong order keeps its own wait, because
                // there is something in it worth reading.
                val skippable = phase.correctOrder
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (skippable) {
                                Modifier.clickable(
                                    onClickLabel = "Go on to the next unit",
                                    onClick = onSkipRevealDelay,
                                )
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    OptionsPanel(phase.options, phase.chosen, enabled = false, onChoose)
                }
            }

            is PhraseQuizPhase.Finished -> ResultCard(phase.result)
        }
    }

    if (state.libraryPickerOpen) {
        PhraseLibraryWindow(state = state, onSelect = onSelectLibraryPhrase, onDismiss = onCloseLibraryPicker)
    }
    if (state.patternPickerOpen) {
        ScalePatternWindow(
            state = state,
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
private fun Setup(state: PhraseQuizUiState, onOpenLibraryPicker: () -> Unit, onOpenPatternPicker: () -> Unit) {
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
                val unitCount = state.units.size
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

/** A full-screen window over the setup screen, for a choice too long for an inline list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenWindow(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            InlineIcon(IconSource.Vector(SaxTrainerIcons.ArrowBack), contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                content = content,
            )
        }
    }
}

/** Simply lists the phrases in the library. No editing happens here. */
@Composable
private fun PhraseLibraryWindow(state: PhraseQuizUiState, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var previewing by remember { mutableStateOf<Phrase?>(null) }

    FullScreenWindow(title = "Phrase Library", onDismiss = onDismiss) {
        if (state.library.isEmpty()) {
            Text(
                text = "No phrases yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.library.forEach { phrase ->
            SelectableCard(
                title = phrase.name,
                supportingText = phrase.description,
                selected = phrase.id == state.libraryPhraseId && state.sourceType == PhraseSourceType.LIBRARY,
                onClick = { onSelect(phrase.id) },
                trailing = { PreviewButton(name = phrase.name, onClick = { previewing = phrase }) },
            )
        }
    }

    previewing?.let { phrase ->
        NotesPreviewDialog(title = phrase.name, sequence = phrase.toneSequence(), onDismiss = { previewing = null })
    }
}

/**
 * Scale, octaves, then the stored patterns to choose between - the pattern is what closes the
 * window; changing the scale or the octaves just changes what the same list of patterns will sound
 * like. Scale and octaves are each a closed field: tapping one opens a dialog over this window
 * rather than crowding the list with a slider or a row of chips.
 */
@Composable
private fun ScalePatternWindow(
    state: PhraseQuizUiState,
    onSelectScaleChoice: (String) -> Unit,
    onToggleOctave: (Int) -> Unit,
    onSelectPattern: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var previewing by remember { mutableStateOf<PhraseRecipe?>(null) }
    var scaleDialogOpen by remember { mutableStateOf(false) }
    var octaveDialogOpen by remember { mutableStateOf(false) }

    FullScreenWindow(title = "Scale Pattern", onDismiss = onDismiss) {
        SectionHeader("Scale")
        FieldRow(
            label = "Scale",
            value = state.scaleChoice?.displayName ?: "Choose a scale",
            onClick = { scaleDialogOpen = true },
        )

        Spacer(Modifier.height(Dimens.spaceSm))
        FieldRow(
            label = "Octaves",
            value = octaveSummary(state.octaves),
            onClick = { octaveDialogOpen = true },
        )

        Spacer(Modifier.height(Dimens.spaceSm))
        SectionHeader("Pattern")
        if (state.patterns.isEmpty()) {
            Text(
                text = "No patterns yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.patterns.forEach { pattern ->
            SelectableCard(
                title = pattern.name,
                supportingText = pattern.description,
                selected = pattern.id == state.patternId && state.sourceType == PhraseSourceType.SCALE_PATTERN,
                onClick = { onSelectPattern(pattern.id) },
                trailing = { PreviewButton(name = pattern.name, onClick = { previewing = pattern }) },
            )
        }
    }

    if (scaleDialogOpen) {
        ScaleChoiceDialog(
            choices = state.scaleChoices,
            selectedId = state.scaleChoiceId,
            onSelect = onSelectScaleChoice,
            onDismiss = { scaleDialogOpen = false },
        )
    }
    if (octaveDialogOpen) {
        OctaveChoiceDialog(
            octaves = PhraseQuizUiState.OCTAVE_RANGE.toList(),
            chosen = state.octaves,
            onToggle = onToggleOctave,
            onDismiss = { octaveDialogOpen = false },
        )
    }

    // Generated from whichever scale and octaves are chosen right now, in this window - not the
    // pattern's own defaults, since it has none: the notes shown are the ones that would actually
    // be practised.
    val choice = state.scaleChoice
    val previewedPattern = previewing
    if (previewedPattern != null && choice != null && state.octaves.isNotEmpty()) {
        val resolved = previewedPattern.resolve(choice.scale, choice.root, state.octaves)
        NotesPreviewDialog(title = resolved.name, sequence = resolved.toneSequence(), onDismiss = { previewing = null })
    }
}

/** The trailing preview button every phrase or pattern card offers - see [NotesPreviewDialog]. */
@Composable
private fun PreviewButton(name: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        InlineIcon(IconSource.Vector(SaxTrainerIcons.PreviewIcon), contentDescription = "Preview $name")
    }
}

/** Which scale a pattern is played on - single choice, so picking one closes the dialog. */
@Composable
private fun ScaleChoiceDialog(
    choices: List<ScaleChoice>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a scale") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    SelectableRow(
                        title = choice.displayName,
                        selected = choice.id == selectedId,
                        onClick = {
                            onSelect(choice.id)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * Which octaves a pattern is generated in - any number of them, so every tap stays open: closing is
 * its own, separate action, the same as the note quiz's own custom-notes dialog.
 */
@Composable
private fun OctaveChoiceDialog(octaves: List<Int>, chosen: Set<Int>, onToggle: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose octaves") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                octaves.forEach { octave ->
                    CheckableRow(
                        title = "Octave $octave",
                        checked = octave in chosen,
                        onCheckedChange = { onToggle(octave) },
                    )
                }
            }
        },
        confirmButton = {
            // Nothing to generate from is not a choice, so Done waits for at least one octave.
            TextButton(onClick = onDismiss, enabled = chosen.isNotEmpty()) { Text("Done") }
        },
    )
}

/** "Choose octaves" while nothing is picked, otherwise the chosen ones in order, lowest first. */
private fun octaveSummary(octaves: Set<Int>): String =
    octaves.sorted().joinToString(", ").ifEmpty { "Choose octaves" }

/**
 * The unit's notes, shuffled among near-pitch decoys, tapped back into order. Used notes stay in
 * place but go inert. Enough of them to fill even rows lay out as a fixed grid - see [OptionGridColumns]
 * - the same "keyboard" reading the note quiz's own name grid uses; a sparse unit flows instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsPanel(options: List<ToneStep>, chosen: List<Int>, enabled: Boolean, onChoose: (Int) -> Unit) {
    val extent = reservedNotesExtent(options.map { it.note })
    if (options.size >= OptionGridColumns * 2) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
            options.withIndex().chunked(OptionGridColumns).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                    row.forEach { (index, step) ->
                        OptionTile(step, extent, used = index in chosen, enabled = enabled, modifier = Modifier.weight(1f)) {
                            onChoose(index)
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
            options.forEachIndexed { index, step ->
                OptionTile(step, extent, used = index in chosen, enabled = enabled) { onChoose(index) }
            }
        }
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
        val note = step.note
        if (note == null) {
            Text(text = "Rest", style = MaterialTheme.typography.titleMedium)
        } else {
            MusicalNoteView(
                primaryNote = note,
                lineSpacing = OptionSpacing,
                accidentalStyle = note.conventionalStyle(),
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
    MusicalStaffView(
        sequence = ToneSequence(phase.correct, phrase.timeSignature, phrase.accidentalStyle),
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
        prefix = StaffPrefix.Treble,
        fillWidth = true,
    )
}

/** What has been tapped so far, with a rest for every note still waiting to be placed. */
@Composable
private fun AskingSubject(state: PhraseQuizUiState, phase: PhraseQuizPhase.Asking) {
    val phrase = state.phrase ?: return
    val built = padTo(phase.builtNotes, phase.correct.size)
    MusicalStaffView(
        sequence = ToneSequence(builtSteps(phase.correct, built), phrase.timeSignature, phrase.accidentalStyle),
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
        prefix = StaffPrefix.Treble,
        fillWidth = true,
    )
}

/** The order as it was built, coloured note by note against the order it should have been. */
@Composable
private fun AnsweredSubject(state: PhraseQuizUiState, phase: PhraseQuizPhase.Answered) {
    val phrase = state.phrase ?: return
    val correctNotes = phase.correct.map { it.note }
    val correctColor = FeedbackColors.correct
    val incorrectColor = FeedbackColors.incorrect
    MusicalStaffView(
        sequence = ToneSequence(builtSteps(phase.correct, phase.builtNotes), phrase.timeSignature, phrase.accidentalStyle),
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
        prefix = StaffPrefix.Treble,
        fillWidth = true,
        noteColor = { index ->
            if (phase.builtNotes.getOrNull(index) == correctNotes.getOrNull(index)) correctColor else incorrectColor
        },
    )
}

/** [correct]'s own durations, paired with whichever pitches have been placed so far - a rest stands
 * in for a slot nobody has filled yet. */
private fun builtSteps(correct: List<ToneStep>, built: List<Note?>): List<ToneStep> =
    correct.mapIndexed { index, step -> ToneStep(built.getOrNull(index), step.duration) }

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
                    text = "${state.units.size} to rebuild",
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
private fun Actions(state: PhraseQuizUiState, onStart: () -> Unit, onStop: () -> Unit, onReset: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        when (state.phase) {
            is PhraseQuizPhase.Idle -> PrimaryButton(
                text = "Start",
                onClick = onStart,
                enabled = state.canStart,
                modifier = Modifier.weight(1f),
            )

            is PhraseQuizPhase.Showing, is PhraseQuizPhase.Asking, is PhraseQuizPhase.Answered ->
                SecondaryButton(text = "Stop", onClick = onStop, modifier = Modifier.weight(1f))

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

private fun reservedNotesExtent(notes: List<Note?>): IntRange =
    reservedStepRange(notes.filterNotNull().ifEmpty { listOf(Note(NoteName.C, 4)) })

/** [notes] followed by enough nulls to reach [size] - the slots still waiting to be filled. */
private fun padTo(notes: List<Note?>, size: Int): List<Note?> =
    if (notes.size >= size) notes else notes + List(size - notes.size) { null }

private val OptionSpacing = 9.dp

/** How many option tiles sit side by side once there are enough of them to fill even rows. */
private const val OptionGridColumns = 6

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
    val plannedCount = state.units.size.takeIf { it > 0 } ?: return null
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
            onChoose = {},
            onSkipRevealDelay = {},
            onStart = {},
            onStop = {},
            onReset = {},
            onBack = {},
        )
    }
}
