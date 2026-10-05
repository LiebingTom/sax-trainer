package com.example.saxtrainer.feature.phrasetrainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.saxtrainer.core.designsystem.component.BoundedMusicalPage
import com.example.saxtrainer.core.designsystem.component.CheckableRow
import com.example.saxtrainer.core.designsystem.component.PhraseLibraryWindow
import com.example.saxtrainer.core.designsystem.component.PracticeTitleActions
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.ScalePatternWindow
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.StepSlider
import com.example.saxtrainer.core.designsystem.component.octaveSummary
import com.example.saxtrainer.core.designsystem.text.toFixed
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.FeedbackColors
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.phrase.Phrase
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
    onSelectRepeats: (Int) -> Unit,
    onSetMixed: (Boolean) -> Unit,
    onShowSecondsChange: (Double) -> Unit,
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
        is PhraseTrainerPhase.Listening -> if (state.showsThroughout) {
            { PlayingSubject(state) }
        } else {
            null
        }
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
        keepScreenOn = state.isBusy,
        header = headerOf(state),
        subject = subject,
        actions = { Actions(state, onStart, onTogglePause, onSkip, onStop, onReset) },
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
                    onSelectRepeats = onSelectRepeats,
                    onSetMixed = onSetMixed,
                    onShowSecondsChange = onShowSecondsChange,
                    onCheckDurationChange = onCheckDurationChange,
                )
            }

            is PhraseTrainerPhase.CountIn -> CountInPanel(phase.beatsRemaining)

            is PhraseTrainerPhase.Showing -> Text(
                text = "Watch closely - it is about to disappear.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
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
            octaveRange = PhraseTrainerUiState.OCTAVE_RANGE,
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
    onSelectRepeats: (Int) -> Unit,
    onSetMixed: (Boolean) -> Unit,
    onShowSecondsChange: (Double) -> Unit,
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
        supportingText = if (state.showsThroughout) {
            "The unit stays on screen while you play it"
        } else {
            "How long a unit stays on screen before it disappears"
        },
        value = state.showSeconds,
        options = PhraseTrainerUiState.SHOW_SECONDS_OPTIONS,
        valueLabel = { if (it.isInfinite()) "Always" else "${it.toFixed(1)} s" },
        onValueChange = onShowSecondsChange,
    )

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
                val unitCount = state.plannedUnitCount
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

/** The correct notation, drawn in full: what is about to be taken away. */
@Composable
private fun ShowingSubject(state: PhraseTrainerUiState, phase: PhraseTrainerPhase.Showing) {
    val phrase = state.phrase ?: return
    val unit = state.runUnits.getOrNull(phase.unitIndex) ?: return
    BoundedMusicalPage(
        sequence = phrase.toneSequenceOf(unit),
        modifier = Modifier.fillMaxWidth(),
        naming = state.noteNaming,
        octaves = state.octaveNumbering,
        color = MaterialTheme.colorScheme.primary,
        staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The unit being played, left on screen because it is shown throughout. Plain notation, the same as
 * when it was shown: nothing is highlighted, the player reads it as they would a page.
 */
@Composable
private fun PlayingSubject(state: PhraseTrainerUiState) {
    val phrase = state.phrase ?: return
    val unit = state.progress?.let { state.runUnits.getOrNull(it.unitIndex) } ?: return
    BoundedMusicalPage(
        sequence = phrase.toneSequenceOf(unit),
        modifier = Modifier.fillMaxWidth(),
        naming = state.noteNaming,
        octaves = state.octaveNumbering,
        color = MaterialTheme.colorScheme.primary,
        staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** What is being listened for right now, in words and colour - below the notes, or instead of them. */
@Composable
private fun ListeningPanel(state: PhraseTrainerUiState) {
    val progress = state.progress
    Column(
        modifier = Modifier.fillMaxWidth(),
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
                    text = "${state.plannedUnitCount} to play",
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
    is PhraseTrainerPhase.Listening -> ScreenHeader.Question(
        if (state.showsThroughout) "Play what you see" else "Play it back from memory",
    )
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
            onSelectRepeats = {},
            onSetMixed = {},
            onShowSecondsChange = {},
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
