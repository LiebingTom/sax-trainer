package com.example.saxtrainer.feature.phrasetrainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.saxtrainer.core.designsystem.component.CheckableRow
import com.example.saxtrainer.core.designsystem.component.FieldRow
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.core.designsystem.component.InlineIcon
import com.example.saxtrainer.core.designsystem.component.MusicalStaffView
import com.example.saxtrainer.core.designsystem.component.NotesPreviewDialog
import com.example.saxtrainer.core.designsystem.component.PracticeTitleActions
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.SelectableCard
import com.example.saxtrainer.core.designsystem.component.SelectableRow
import com.example.saxtrainer.core.designsystem.component.StaffPrefix
import com.example.saxtrainer.core.designsystem.icon.ArrowBack
import com.example.saxtrainer.core.designsystem.icon.Preview as PreviewIcon
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.FeedbackColors
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.phrase.Phrase
import com.example.saxtrainer.core.phrase.PhraseRecipe
import com.example.saxtrainer.core.phrase.PhraseSourceType

/**
 * The phrase trainer, stateless.
 *
 * A unit is drawn once, taken away, and then played back into the microphone from memory - the
 * mirror image of the phrase quiz's "tap it back into order", answered with an instrument instead
 * of a finger.
 */
@Composable
fun PhraseTrainerScreen(
    state: PhraseTrainerUiState,
    onOpenLibraryPicker: () -> Unit,
    onCloseLibraryPicker: () -> Unit,
    onSelectLibraryPhrase: (String) -> Unit,
    onOpenPatternPicker: () -> Unit,
    onClosePatternPicker: () -> Unit,
    onSelectScaleChoice: (String) -> Unit,
    onToggleOctave: (Int) -> Unit,
    onSelectPattern: (String) -> Unit,
    onCheckDurationChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToLessons: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: ((Boolean) -> Unit)? = null,
) {
    val subject: (@Composable ColumnScope.() -> Unit)? = when (val phase = state.phase) {
        is PhraseTrainerPhase.Showing -> { { ShowingSubject(state, phase) } }
        else -> null
    }

    ScreenScaffold(
        title = "Phrase Trainer",
        onBack = onBack,
        modifier = modifier,
        titleActions = {
            if (!state.isBusy) {
                PracticeTitleActions("Phrase Trainer", isFavorite, onToggleFavorite, onAddToLessons)
            }
        },
        showTrail = !state.isBusy,
        header = headerOf(state),
        subject = subject,
        actions = { Actions(state, onStart, onTogglePause, onSkip, onStop, onReset) },
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(Dimens.spaceSm))

        when (val phase = state.phase) {
            is PhraseTrainerPhase.Idle -> if (state.isPreconfigured) {
                ReadyPanel(state)
            } else {
                Setup(
                    state = state,
                    onOpenLibraryPicker = onOpenLibraryPicker,
                    onOpenPatternPicker = onOpenPatternPicker,
                    onCheckDurationChange = onCheckDurationChange,
                )
            }

            is PhraseTrainerPhase.CountIn -> CountInPanel(phase.beatsRemaining)

            is PhraseTrainerPhase.Showing -> Text(
                text = "Watch closely - it is about to disappear.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is PhraseTrainerPhase.Listening -> ListeningPanel(state)

            is PhraseTrainerPhase.Finished -> ResultCard(phase.result)
        }

        if (state.microphoneUnavailable) {
            Spacer(Modifier.height(Dimens.spaceSm))
            Text(
                text = "Microphone access is needed to play this back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
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
 * shown here, plus the one Trainer-only setting - see [ScalePatternWindow] and
 * [PhraseLibraryWindow]. Neither window can be used to edit a phrase or a pattern; both are plain
 * lists to choose from.
 */
@Composable
private fun Setup(
    state: PhraseTrainerUiState,
    onOpenLibraryPicker: () -> Unit,
    onOpenPatternPicker: () -> Unit,
    onCheckDurationChange: (Boolean) -> Unit,
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
    SectionHeader("Checking")
    CheckableRow(
        title = "Also check note length",
        supportingText = "Hold every note for its written length, not just play it.",
        checked = state.checkDuration,
        onCheckedChange = onCheckDurationChange,
    )

    if (!state.canStart) {
        Spacer(Modifier.height(Dimens.spaceSm))
        Text(
            text = if (state.microphoneUnavailable) {
                "Microphone access is needed to play this back."
            } else {
                "Choose a phrase to practice."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * What is currently selected, said plainly: a name, where it came from, and how the practice will
 * walk through it - so the player knows what "Start" is about to run without opening either window.
 */
@Composable
private fun SelectionSummary(state: PhraseTrainerUiState) {
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
                        text = "$unitCount unit${if (unitCount == 1) "" else "s"} to play, " +
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
private fun PhraseLibraryWindow(state: PhraseTrainerUiState, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
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
 * Octave, scale, then the stored patterns to choose between - the pattern is what closes the
 * window; changing the octave or the scale just changes what the same list of patterns will sound
 * like.
 */
@Composable
private fun ScalePatternWindow(
    state: PhraseTrainerUiState,
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
            octaves = PhraseTrainerUiState.OCTAVE_RANGE.toList(),
            chosen = state.octaves,
            onToggle = onToggleOctave,
            onDismiss = { octaveDialogOpen = false },
        )
    }

    val choice = state.scaleChoice
    val previewedPattern = previewing
    if (previewedPattern != null && choice != null && state.octaves.isNotEmpty()) {
        val resolved = previewedPattern.resolve(choice.scale, choice.root, state.octaves)
        NotesPreviewDialog(title = resolved.name, sequence = resolved.toneSequence(), onDismiss = { previewing = null })
    }
}

/** The trailing preview button on a phrase/pattern row: opens a read-only look at its notes. */
@Composable
private fun PreviewButton(name: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        InlineIcon(IconSource.Vector(SaxTrainerIcons.PreviewIcon), contentDescription = "Preview $name")
    }
}

/** Which scale a pattern is played on - single choice, so picking one closes the dialog. */
@Composable
private fun ScaleChoiceDialog(
    choices: List<com.example.saxtrainer.core.phrase.ScaleChoice>,
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

/** The correct notation, drawn in full: what is about to be taken away. */
@Composable
private fun ShowingSubject(state: PhraseTrainerUiState, phase: PhraseTrainerPhase.Showing) {
    val phrase = state.phrase ?: return
    val unit = state.units.getOrNull(phase.unitIndex) ?: return
    MusicalStaffView(
        sequence = phrase.toneSequenceOf(unit),
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
        prefix = StaffPrefix.Treble,
        fillWidth = true,
    )
}

/** What is being listened for right now, in words and colour rather than notation - it is hidden. */
@Composable
private fun ListeningPanel(state: PhraseTrainerUiState) {
    val progress = state.progress
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        if (progress == null) {
            Text(text = "Get ready...", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Text(
            text = "Note ${progress.stepIndex + 1} of ${progress.stepCount}",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        val (message, color) = when (progress.listening) {
            Listening.WAITING -> "Listening..." to MaterialTheme.colorScheme.onSurfaceVariant
            Listening.UNCLEAR -> "..." to MaterialTheme.colorScheme.onSurfaceVariant
            Listening.WRONG_PITCH -> {
                val heard = progress.detectedNote?.let(state::nameOf)
                (if (heard != null) "Heard $heard" else "Not quite") to FeedbackColors.incorrect
            }
            Listening.CORRECT -> (if (progress.landed) "Got it!" else "Keep going...") to FeedbackColors.correct
        }
        Text(text = message, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun CountInPanel(beatsRemaining: Int) {
    Text(
        text = "$beatsRemaining",
        style = MaterialTheme.typography.displayLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** What a ready-made exercise is about to ask. There is nothing to change; it was decided elsewhere. */
@Composable
private fun ReadyPanel(state: PhraseTrainerUiState) {
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
                    text = "${state.units.size} to play",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(result: PhraseTrainerResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            Text(
                text = if (result.completed) "Exercise complete" else "Stopped early",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${result.correctCount} of ${result.attempts.size} played correctly",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Actions(
    state: PhraseTrainerUiState,
    onStart: () -> Unit,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        when (state.phase) {
            is PhraseTrainerPhase.Idle -> PrimaryButton(
                text = "Start",
                onClick = onStart,
                enabled = state.canStart,
                modifier = Modifier.weight(1f),
            )

            is PhraseTrainerPhase.CountIn, is PhraseTrainerPhase.Showing ->
                SecondaryButton(text = "Stop", onClick = onStop, modifier = Modifier.weight(1f))

            is PhraseTrainerPhase.Listening -> {
                SecondaryButton(
                    text = if (state.isPaused) "Resume" else "Pause",
                    onClick = onTogglePause,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(text = "Skip", onClick = onSkip, modifier = Modifier.weight(1f))
                SecondaryButton(text = "Stop", onClick = onStop, modifier = Modifier.weight(1f))
            }

            is PhraseTrainerPhase.Finished -> {
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

/**
 * What the screen says for itself, above the rule at the top.
 *
 * While it is being set up it says what the practice is; once it is running it asks for what the
 * player should be doing right now.
 */
private fun headerOf(state: PhraseTrainerUiState): ScreenHeader = when (state.phase) {
    is PhraseTrainerPhase.CountIn -> ScreenHeader.Question("Get ready")
    is PhraseTrainerPhase.Showing -> ScreenHeader.Question("Listen and watch")
    is PhraseTrainerPhase.Listening -> ScreenHeader.Question("Play it back from memory")
    is PhraseTrainerPhase.Finished -> ScreenHeader.Question("How did that go?")
    PhraseTrainerPhase.Idle -> ScreenHeader.Description(
        "See a phrase, then play it back from memory once it disappears. A microphone is needed.",
    )
}

@Preview(showBackground = true)
@Composable
private fun PhraseTrainerSetupPreview() {
    SaxTrainerTheme {
        PhraseTrainerScreen(
            state = PhraseTrainerUiState(
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
            onCheckDurationChange = {},
            onStart = {},
            onTogglePause = {},
            onSkip = {},
            onStop = {},
            onReset = {},
            onBack = {},
        )
    }
}
