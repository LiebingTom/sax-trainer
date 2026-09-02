package com.example.saxtrainer.feature.fingering

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.example.saxtrainer.core.designsystem.component.CheckableRow
import com.example.saxtrainer.core.designsystem.component.NoteDisplayPicker
import com.example.saxtrainer.core.designsystem.component.NotePresentation
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.SaxophoneFingeringView
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.SelectableRow
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.FeedbackColors
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.designsystem.theme.numericLarge
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.settings.NoteDisplay
import kotlin.math.ceil

/** Stateless fingering trainer UI. Everything it needs is in [state]. */
@Composable
fun FingeringScreen(
    state: FingeringUiState,
    onToggleNote: (Note) -> Unit,
    onSelectAll: () -> Unit,
    onClearNotes: () -> Unit,
    onRandomChange: (Boolean) -> Unit,
    onSecondsChange: (Int) -> Unit,
    onCheckPlayingChange: (Boolean) -> Unit,
    onCheckModeChange: (CheckMode) -> Unit,
    onNoteDisplayChange: (NoteDisplay) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(title = "Fingering Trainer", onBack = onBack, modifier = modifier) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Dimens.spaceSm))

            when (val phase = state.phase) {
                is FingeringPhase.Idle -> Setup(
                    state = state,
                    onToggleNote = onToggleNote,
                    onSelectAll = onSelectAll,
                    onClearNotes = onClearNotes,
                    onRandomChange = onRandomChange,
                    onSecondsChange = onSecondsChange,
                    onCheckPlayingChange = onCheckPlayingChange,
                    onCheckModeChange = onCheckModeChange,
                    onNoteDisplayChange = onNoteDisplayChange,
                )

                is FingeringPhase.Running -> RunningPanel(state)
                is FingeringPhase.Finished -> ResultCard(phase.result)
            }

            Spacer(Modifier.height(Dimens.spaceLg))
            Actions(
                state = state,
                onStart = onStart,
                onStop = onStop,
                onReset = onReset,
            )
            Spacer(Modifier.height(Dimens.spaceLg))
        }
    }
}

@Composable
private fun Setup(
    state: FingeringUiState,
    onToggleNote: (Note) -> Unit,
    onSelectAll: () -> Unit,
    onClearNotes: () -> Unit,
    onRandomChange: (Boolean) -> Unit,
    onSecondsChange: (Int) -> Unit,
    onCheckPlayingChange: (Boolean) -> Unit,
    onCheckModeChange: (CheckMode) -> Unit,
    onNoteDisplayChange: (NoteDisplay) -> Unit,
) {
    Text(
        text = "Learn which keys to press for each note. Play along or just look.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("Notes")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(Dimens.cardPadding)) {
            state.availableNotes.forEach { note ->
                CheckableRow(
                    title = note.label,
                    checked = state.isSelected(note),
                    onCheckedChange = { onToggleNote(note) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                SecondaryButton(text = "All", onClick = onSelectAll)
                SecondaryButton(text = "None", onClick = onClearNotes)
            }
        }
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
        state.secondsOptions.forEach { seconds ->
            FilterChip(
                selected = seconds == state.secondsPerNote,
                onClick = { onSecondsChange(seconds) },
                label = { Text("$seconds s") },
            )
        }
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("Note display")
    NoteDisplayPicker(
        options = state.noteDisplayOptions,
        selected = state.noteDisplay,
        label = { it.shortName },
        onSelect = onNoteDisplayChange,
    )

    Spacer(Modifier.height(Dimens.spaceSm))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Check my playing", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (state.checkUnavailable) {
                    "The microphone is not available, so the exercise runs on time alone."
                } else {
                    "Marks the note green while you play it, heard as " + state.instrumentName
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

    if (state.checkPlaying) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                CheckMode.entries.forEach { mode ->
                    SelectableRow(
                        title = mode.displayName,
                        supportingText = mode.description,
                        selected = mode == state.checkMode,
                        onClick = { onCheckModeChange(mode) },
                    )
                }
            }
        }
    }

    if (!state.canStart) {
        Spacer(Modifier.height(Dimens.spaceSm))
        Text(
            text = "Choose at least one note to practise.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** The fingering is the point of the screen, so it gets the most room. */
@Composable
private fun RunningPanel(state: FingeringUiState) {
    val progress = state.progress ?: return

    val correctColor = FeedbackColors.correct
    val noteColor = if (progress.isCorrect) correctColor else MaterialTheme.colorScheme.primary
    val staffColor = if (progress.isCorrect) correctColor else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        Text(
            text = "${progress.displayPosition} / ${progress.noteCount}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            NotePresentation(
                note = progress.currentNote,
                label = progress.currentNote.label,
                showName = state.noteDisplay.showsName,
                showStaff = state.noteDisplay.showsStaff,
                color = noteColor,
                staffColor = staffColor,
                nameStyle = numericLarge,
            )
            if (progress.nextNote != null) {
                NotePresentation(
                    note = progress.nextNote,
                    label = progress.nextNote.label,
                    showName = state.noteDisplay.showsName,
                    showStaff = state.noteDisplay.showsStaff,
                    color = MaterialTheme.colorScheme.onSurface,
                    nameStyle = numericLarge.copy(
                        fontSize = numericLarge.fontSize * NEXT_NOTE_SCALE,
                        lineHeight = numericLarge.lineHeight * NEXT_NOTE_SCALE,
                    ),
                    staffLineSpacing = NextNoteStaffSpacing,
                )
            }
        }

        if (progress.currentFingering != null) {
            SaxophoneFingeringView(
                fingering = progress.currentFingering,
                width = FingeringWidth,
                pressedColor = staffColor,
                contentDescription = "Fingering for ${progress.currentNote.label}",
            )
        } else {
            Text(
                text = "No fingering stored for ${progress.currentNote.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LinearProgressIndicator(
            progress = { progress.noteFraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${ceil(progress.remainingSeconds).toInt()} s",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.checkPlaying) {
            Text(
                text = when {
                    progress.isCorrect -> "That is the note"
                    progress.detectedNote != null -> "Hearing " + progress.detectedNote.label
                    else -> "Listening..."
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (progress.isCorrect) {
                    correctColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun ResultCard(result: FingeringResult) {
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
                        append(" played correctly")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val correctColor = FeedbackColors.correct
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                result.notes.forEach { practised ->
                    Text(
                        text = practised.note.label,
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
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        when (state.phase) {
            is FingeringPhase.Running -> PrimaryButton(
                text = "Stop",
                onClick = onStop,
                modifier = Modifier.weight(1f),
            )

            is FingeringPhase.Finished -> {
                PrimaryButton(
                    text = "Practice again",
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "Change notes",
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                )
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

private const val NEXT_NOTE_SCALE = 0.5f
private val NextNoteStaffSpacing = 5.dp
private val FingeringWidth = 120.dp

@Preview(showBackground = true)
@Composable
private fun FingeringSetupPreview() {
    val notes = SaxophoneFingerings.supportedNotes
    SaxTrainerTheme {
        FingeringScreen(
            state = FingeringUiState(
                availableNotes = notes,
                selectedNotes = notes.toSet(),
                secondsOptions = listOf(3, 5, 8),
            ),
            onToggleNote = {},
            onSelectAll = {},
            onClearNotes = {},
            onRandomChange = {},
            onSecondsChange = {},
            onCheckPlayingChange = {},
            onCheckModeChange = {},
            onNoteDisplayChange = {},
            onStart = {},
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
                selectedNotes = notes.toSet(),
                secondsOptions = listOf(3, 5, 8),
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
            onToggleNote = {},
            onSelectAll = {},
            onClearNotes = {},
            onRandomChange = {},
            onSecondsChange = {},
            onCheckPlayingChange = {},
            onCheckModeChange = {},
            onNoteDisplayChange = {},
            onStart = {},
            onStop = {},
            onReset = {},
            onBack = {},
        )
    }
}
