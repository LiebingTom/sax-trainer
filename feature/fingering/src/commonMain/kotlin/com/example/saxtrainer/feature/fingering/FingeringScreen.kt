package com.example.saxtrainer.feature.fingering

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.core.designsystem.icon.ChooseNotes
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.component.InlineIcon
import com.example.saxtrainer.core.designsystem.component.NoteChoiceDialog
import com.example.saxtrainer.core.designsystem.component.NoteDisplayPicker
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.SaxophoneFingeringView
import com.example.saxtrainer.core.designsystem.component.fingeringHeightOf
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.ScreenTiming
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.SelectableRow
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.FeedbackColors
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.designsystem.theme.numericLarge
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.settings.NoteDisplay
import kotlin.math.ceil
import com.example.saxtrainer.core.designsystem.component.MusicalStaffView
import com.example.saxtrainer.core.designsystem.component.StaffNoteStyle
import com.example.saxtrainer.core.designsystem.component.StaffPrefix
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import com.example.saxtrainer.core.designsystem.component.reservedStepRange
import androidx.compose.runtime.remember
import com.example.saxtrainer.core.music.conventionalStyle
import com.example.saxtrainer.core.designsystem.component.CurrentAndNextNote
import com.example.saxtrainer.core.designsystem.component.NextNoteName
import com.example.saxtrainer.core.designsystem.text.toFixed

/** Stateless fingering trainer UI. Everything it needs is in [state]. */
@Composable
fun FingeringScreen(
    state: FingeringUiState,
    onSelectNoteSet: (NoteSet) -> Unit,
    onEditCustomNotes: () -> Unit,
    onToggleCustomNote: (Note) -> Unit,
    onSelectAllCustomNotes: () -> Unit,
    onClearCustomNotes: () -> Unit,
    onApplyCustomNotes: () -> Unit,
    onCancelCustomNotes: () -> Unit,
    onRandomChange: (Boolean) -> Unit,
    onBeatsChange: (Beats) -> Unit,
    onTempoChange: (Int) -> Unit,
    onShowNextNoteChange: (Boolean) -> Unit,
    onCheckPlayingChange: (Boolean) -> Unit,
    onSuccessDelayChange: (Float) -> Unit,
    onNoteDisplayChange: (NoteDisplay) -> Unit,
    onSkipSuccessDelay: () -> Unit,
    onStart: () -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToLessons: (() -> Unit)? = null,
) {
    // The note being read, kept apart from the fingering that answers it.
    val noteSubject: (@Composable ColumnScope.() -> Unit)? =
        if (state.phase is FingeringPhase.Running) ({ NoteSubject(state) }) else null

    ScreenScaffold(
        title = "Fingering Trainer",
        onBack = onBack,
        modifier = modifier,
        header = headerOf(state),
        subject = noteSubject,
        timing = timingOf(state),
        actions = {
            Actions(
                state = state,
                onStart = onStart,
                onTogglePause = onTogglePause,
                onStop = onStop,
                onReset = onReset,
                onBack = onBack,
            )
        },
    ) {
        Spacer(Modifier.height(Dimens.spaceSm))

        when (val phase = state.phase) {
            is FingeringPhase.Idle -> if (state.isPreconfigured) {
                ReadyPanel(state)
            } else {
                Setup(
                    state = state,
                    onSelectNoteSet = onSelectNoteSet,
                    onEditCustomNotes = onEditCustomNotes,
                    onRandomChange = onRandomChange,
                    onBeatsChange = onBeatsChange,
                    onTempoChange = onTempoChange,
                    onShowNextNoteChange = onShowNextNoteChange,
                    onCheckPlayingChange = onCheckPlayingChange,
                    onSuccessDelayChange = onSuccessDelayChange,
                    onNoteDisplayChange = onNoteDisplayChange,
                )
                if (onAddToLessons != null) {
                    Spacer(Modifier.height(Dimens.spaceMd))
                    SecondaryButton(
                        text = "Add to your lessons",
                        onClick = onAddToLessons,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            is FingeringPhase.CountIn -> CountInPanel(phase.beatsRemaining, state.isPaused)
            is FingeringPhase.Running -> RunningPanel(state, onSkipSuccessDelay)
            is FingeringPhase.Finished -> ResultCard(state, phase.result)
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
    state: FingeringUiState,
    onSelectNoteSet: (NoteSet) -> Unit,
    onEditCustomNotes: () -> Unit,
    onRandomChange: (Boolean) -> Unit,
    onBeatsChange: (Beats) -> Unit,
    onTempoChange: (Int) -> Unit,
    onShowNextNoteChange: (Boolean) -> Unit,
    onCheckPlayingChange: (Boolean) -> Unit,
    onSuccessDelayChange: (Float) -> Unit,
    onNoteDisplayChange: (NoteDisplay) -> Unit,
) {
    SectionHeader("Notes")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(Dimens.cardPadding)) {
            state.noteSets.forEach { noteSet ->
                SelectableRow(
                    title = noteSet.displayName,
                    supportingText = state.summaryOf(noteSet, SaxophoneFingerings),
                    selected = noteSet == state.noteSet,
                    onClick = { onSelectNoteSet(noteSet) },
                    trailing = if (noteSet == NoteSet.CUSTOM) {
                        {
                            IconButton(onClick = onEditCustomNotes) {
                                InlineIcon(
                                    icon = IconSource.Vector(SaxTrainerIcons.ChooseNotes),
                                    contentDescription = "Choose the notes yourself",
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("Tempo")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        SecondaryButton(text = "-5", onClick = { onTempoChange(state.tempo.bpm - 5) })
        Text(
            text = "${state.tempo.bpm} BPM",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        SecondaryButton(text = "+5", onClick = { onTempoChange(state.tempo.bpm + 5) })
    }
    Text(
        text = "A beat lasts ${state.tempo.secondsFor(Beats.of(1)).toFixed(1)} s, so each note is " +
            "shown for ${state.tempo.secondsFor(state.beatsPerNote).toFixed(1)} s.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(Dimens.spaceSm))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Show next note", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "The note after this one, as its name. Off, there is nothing to read but " +
                    "the staff.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = state.showNextNote, onCheckedChange = onShowNextNoteChange)
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(text = "Mixed order", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Notes come in a different order every time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = state.isRandom, onCheckedChange = onRandomChange)
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("Show each note for")
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        state.beatOptions.forEach { beats ->
            FilterChip(
                selected = beats == state.beatsPerNote,
                onClick = { onBeatsChange(beats) },
                label = { Text(beatsLabelOf(beats), maxLines = 1) },
            )
        }
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Play the notes", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = when {
                    state.checkUnavailable ->
                        "The microphone is not available, so the fingerings are simply shown."

                    else ->
                        "Find the fingering yourself and play it, heard as " + state.instrumentName +
                            ". The fingering is shown once you have."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.checkUnavailable) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Switch(checked = state.checkPlaying, onCheckedChange = onCheckPlayingChange)
    }

    // Only asked about when there is something to be right about: with checking off nothing is
    // played, so there is nothing to stay on.
    if (state.checkPlaying) {
        Spacer(Modifier.height(Dimens.spaceSm))
        SectionHeader("Stay on a correct note for")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
            state.successDelayOptions.forEach { seconds ->
                FilterChip(
                    selected = seconds == state.successDelaySeconds,
                    onClick = { onSuccessDelayChange(seconds) },
                    label = { Text(successDelayLabelOf(seconds)) },
                )
            }
        }
        Text(
            text = "Tap the fingering to move on sooner.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("Note display")
    NoteDisplayPicker(
        options = state.noteDisplayOptions,
        selected = state.noteDisplay,
        label = { it.shortName },
        onSelect = onNoteDisplayChange,
    )

    if (!state.canStart) {
        Spacer(Modifier.height(Dimens.spaceSm))
        Text(
            text = "Choose at least one note in the custom list to practise.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun CountInPanel(beatsRemaining: Int, paused: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        Text(
            text = beatsRemaining.toString(),
            style = numericLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = if (paused) "Paused" else "Get ready",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The fingering is the point of the screen, so it gets the most room. */
@Composable
private fun RunningPanel(state: FingeringUiState, onSkipSuccessDelay: () -> Unit) {
    val progress = state.progress ?: return

    // The whole panel is the button, but only while the note is being held on screen after being
    // played right. A note still being asked for is answered by playing it, never by tapping.
    val skippable = progress.landed && !state.isPaused

    val correctColor = FeedbackColors.correct
    val wrongColor = FeedbackColors.incorrect
    // The key diagram stays neutral: it is a chart to read, not the note being asked for.
    val fingeringColor = if (progress.isCorrect) correctColor else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (skippable) {
                    Modifier.clickable(
                        onClickLabel = "Go on to the next note",
                        onClick = onSkipSuccessDelay,
                    )
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        when {
            // Held back while the note is being asked for: recalling the grip is the exercise, and
            // the answer would give it away. The box stays, so nothing jumps when it appears.
            !progress.showFingering -> Spacer(Modifier.height(fingeringHeightOf(FingeringWidth)))

            progress.currentFingering != null -> SaxophoneFingeringView(
                fingering = progress.currentFingering,
                width = FingeringWidth,
                pressedColor = fingeringColor,
                contentDescription = "Fingering for ${state.nameOf(progress.currentNote)}",
            )

            else -> Text(
                text = "No fingering stored for ${state.nameOf(progress.currentNote)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.checkPlaying) {
            Text(
                text = when {
                    // Already played right: what happens to the sound now changes nothing.
                    progress.landed -> "That is the note"
                    else -> when (progress.heard) {
                        Heard.CORRECT -> "That is the note"
                        Heard.WRONG -> progress.detectedNote
                            ?.let { "That is " + state.nameOf(it) }
                            ?: "Not that one"
                        // Something was played but no pitch could be read from it, which is not the
                        // same as playing the wrong note and is not treated like one.
                        Heard.UNCLEAR -> "Keep the tone going"
                        Heard.NOTHING -> if (progress.showFingering) {
                            "This is the fingering"
                        } else {
                            "Play the note"
                        }
                    }
                },
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    progress.landed || progress.heard == Heard.CORRECT -> correctColor
                    progress.heard == Heard.WRONG -> wrongColor
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            if (skippable) {
                Text(
                    text = "Tap to go on",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * What a ready-made exercise is about to ask for. Shown only when a preset chose not to start by
 * itself; there is nothing here to change, because the exercise was decided elsewhere.
 */
@Composable
private fun ReadyPanel(state: FingeringUiState) {
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
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "${beatsLabelOf(state.beatsPerNote)} per note, " +
                    if (state.isRandom) "mixed up" else "in order",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultCard(state: FingeringUiState, result: FingeringResult) {
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
                text = if (result.completed) "Fingering practice complete" else "Stopped early",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = buildString {
                    append(result.notes.size)
                    append(" of ")
                    append(result.plannedNoteCount)
                    append(" notes practised")
                    if (result.checked) {
                        append(", ")
                        append(result.correctCount)
                        append(" played")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val correctColor = FeedbackColors.correct
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                result.notes.forEach { practised ->
                    Text(
                        text = state.nameOf(practised.note),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (practised.playedCorrectly) {
                            correctColor
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Actions(
    state: FingeringUiState,
    onStart: () -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        when (state.phase) {
            // Pause takes the primary place while a run is on: it is the one a player reaches for
            // to answer the door, and stopping throws the rest of the exercise away.
            is FingeringPhase.CountIn, is FingeringPhase.Running -> {
                PrimaryButton(
                    text = if (state.isPaused) "Continue" else "Pause",
                    onClick = onTogglePause,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "Stop",
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                )
            }

            is FingeringPhase.Finished -> {
                PrimaryButton(
                    text = "Practice again",
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                )
                if (state.isPreconfigured) {
                    SecondaryButton(
                        text = "Done",
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    SecondaryButton(
                        text = "Change notes",
                        onClick = onReset,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            is FingeringPhase.Idle -> PrimaryButton(
                text = "Start",
                onClick = onStart,
                enabled = state.canStart,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** How a wait reads on the setup screen; none of the offered ones needs a decimal point. */
private fun successDelayLabelOf(seconds: Float): String =
    if (seconds <= 0f) "Straight on" else "${seconds.toInt()} s"

private val CurrentNoteStaffSpacing = 12.dp
private val FingeringWidth = 120.dp

@Preview(showBackground = true)
@Composable
private fun FingeringSetupPreview() {
    val notes = SaxophoneFingerings.supportedNotes
    SaxTrainerTheme {
        FingeringScreen(
            state = FingeringUiState(
                availableNotes = notes,
                customNotes = notes.toSet(),
                activeNotes = notes,
                beatOptions = listOf(3, 5, 8).map(Beats::of),
            ),
            onSelectNoteSet = {},
            onEditCustomNotes = {},
            onToggleCustomNote = {},
            onSelectAllCustomNotes = {},
            onClearCustomNotes = {},
            onApplyCustomNotes = {},
            onCancelCustomNotes = {},
            onRandomChange = {},
            onBeatsChange = {},
            onTempoChange = {},
            onShowNextNoteChange = {},
            onCheckPlayingChange = {},
            onSuccessDelayChange = {},
            onNoteDisplayChange = {},
            onSkipSuccessDelay = {},
            onStart = {},
            onTogglePause = {},
            onStop = {},
            onReset = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FingeringRunningPreview() {
    val notes = SaxophoneFingerings.supportedNotes
    SaxTrainerTheme {
        FingeringScreen(
            state = FingeringUiState(
                phase = FingeringPhase.Running,
                availableNotes = notes,
                customNotes = notes.toSet(),
                activeNotes = notes,
                beatOptions = listOf(3, 5, 8).map(Beats::of),
                progress = FingeringProgress(
                    noteIndex = 2,
                    noteCount = 10,
                    currentNote = notes[5],
                    currentFingering = SaxophoneFingerings.fingeringFor(notes[5]),
                    nextNote = notes[8],
                    remainingSeconds = 3.2f,
                    noteSeconds = 5f,
                ),
            ),
            onSelectNoteSet = {},
            onEditCustomNotes = {},
            onToggleCustomNote = {},
            onSelectAllCustomNotes = {},
            onClearCustomNotes = {},
            onApplyCustomNotes = {},
            onCancelCustomNotes = {},
            onRandomChange = {},
            onBeatsChange = {},
            onTempoChange = {},
            onShowNextNoteChange = {},
            onCheckPlayingChange = {},
            onSuccessDelayChange = {},
            onNoteDisplayChange = {},
            onSkipSuccessDelay = {},
            onStart = {},
            onTogglePause = {},
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
private fun headerOf(state: FingeringUiState): ScreenHeader = when (state.phase) {
    FingeringPhase.Idle -> ScreenHeader.Description(
        "Learn which keys to press for each written note. Play along or just look.",
    )

    is FingeringPhase.CountIn -> ScreenHeader.Question("Get ready")
    // With checking on the grip is the answer, so the question is to play it rather than read it.
    FingeringPhase.Running -> ScreenHeader.Question(
        if (state.checkPlaying) "Play this note" else "How is this note fingered?",
    )

    is FingeringPhase.Finished -> ScreenHeader.Question("How did that go?")
}

/**
 * How long is left on the note, for the bar across the foot of the screen.
 *
 * Null whenever nothing is being timed, which takes the bar off the screen rather than leaving an
 * empty one behind.
 */
private fun timingOf(state: FingeringUiState): ScreenTiming? {
    val progress = state.progress ?: return null
    if (state.phase !is FingeringPhase.Running) return null

    return ScreenTiming(
        fraction = progress.noteFraction,
        sessionFraction = sessionFractionOf(progress.displayPosition, progress.noteCount),
        step = progress.displayPosition,
        stepCount = progress.noteCount,
        secondsLeft = ceil(progress.remainingSeconds).toInt(),
        secondsAtMost = ceil(progress.noteSeconds).toInt(),
        paused = state.isPaused,
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
 * The note being read, and the one after it.
 *
 * Kept out of the panel below so the note being asked about never runs into the fingering that
 * answers it.
 */
@Composable
private fun NoteSubject(state: FingeringUiState) {
    val progress = state.progress ?: return

    // The note itself takes the accent colour the buttons use, whether it is drawn or written, and
    // turns green while it is being played correctly.
    val noteColor =
        if (progress.isCorrect) FeedbackColors.correct else MaterialTheme.colorScheme.primary

    // One note on the staff, always starting its own bar, so the same length is always written the
    // same way. This exercise measures a note in seconds and only counts them in beats against the
    // tempo the player chose, so there is no rhythm here to write down: every note is a plain
    // quarter, and the staff carries no time signature and no bar line saying otherwise.
    val note = progress.currentNote
    val sequence = remember(note) {
        ToneSequence(
            steps = listOf(ToneStep(note, SUBJECT_NOTE_BEATS)),
            // Spelled the way the note is usually written, as the name beside it always was.
            accidentalStyle = note.conventionalStyle(),
        )
    }
    val nextNote = progress.nextNote.takeIf { state.showNextNote }

    CurrentAndNextNote(
        modifier = Modifier.fillMaxWidth(),
        next = nextNote?.let { { NextNoteName(state.nameOf(it)) } },
    ) {
        MusicalStaffView(
            sequence = sequence,
            modifier = Modifier.fillMaxWidth(),
            color = noteColor,
            staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
            lineSpacing = CurrentNoteStaffSpacing,
            naming = state.noteNaming,
            prefix = StaffPrefix.Treble,
            // The name-or-notation setting reaches the staff as a per-note choice, which is the only
            // shape the design system understands - it never learns what a setting is.
            noteStyle = {
                if (progress.noteDisplay.showsStaff) StaffNoteStyle.HEAD else StaffNoteStyle.NAME
            },
            labelBackground = MaterialTheme.colorScheme.surface,
            fillWidth = true,
            closingBar = false,
            centerMusic = true,
            reserveAccidentals = true,
            stepRange = reservedStepRange(state.activeNotes),
        )
    }
}


/** How a duration reads on the setup screen. Beats, because that is what it is. */
private fun beatsLabelOf(beats: Beats): String {
    val whole = beats.value.toInt()
    return if (whole == 1) "1 beat" else "$whole beats"
}

/** What a note of this exercise is written as. Not what it lasts - see [NoteSubject]. */
private val SUBJECT_NOTE_BEATS = Beats.of(1)
