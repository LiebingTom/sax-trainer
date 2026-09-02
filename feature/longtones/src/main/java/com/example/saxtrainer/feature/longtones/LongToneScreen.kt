package com.example.saxtrainer.feature.longtones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.audio.capture.AudioCaptureError
import com.example.saxtrainer.core.designsystem.component.CentsMeter
import com.example.saxtrainer.core.designsystem.component.CentsMeterLabels
import com.example.saxtrainer.core.designsystem.component.LevelMeter
import com.example.saxtrainer.core.designsystem.component.MetricDisplay
import com.example.saxtrainer.core.designsystem.component.NoteDisplayPicker
import com.example.saxtrainer.core.designsystem.component.NotePresentation
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.SelectableRow
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.designsystem.theme.numericLarge
import com.example.saxtrainer.core.exercise.ExercisePattern
import com.example.saxtrainer.core.exercise.SequenceCatalog
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.Transposition
import com.example.saxtrainer.core.settings.NoteDisplay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Stateless long tone UI. All measurements arrive ready to render in [state]. */
@Composable
fun LongToneScreen(
    state: LongToneUiState,
    onSelectPattern: (ExercisePattern) -> Unit,
    onSelectRootNote: (Note) -> Unit,
    onSelectBeats: (Beats) -> Unit,
    onSetTempo: (Int) -> Unit,
    onSelectNoteDisplay: (NoteDisplay) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    permissionDenied: Boolean = false,
    onOpenAppSettings: (() -> Unit)? = null,
    detectedNoteColor: Color = MaterialTheme.colorScheme.error,
) {
    ScreenScaffold(title = "Long Tones", onBack = onBack, modifier = modifier) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Dimens.spaceSm))

            when (val phase = state.phase) {
                is LongTonePhase.Failed -> ErrorCard(
                    error = phase.error,
                    onOpenAppSettings = onOpenAppSettings,
                )

                else -> if (permissionDenied) {
                    ErrorCard(
                        error = AudioCaptureError.PermissionDenied,
                        onOpenAppSettings = onOpenAppSettings,
                    )
                }
            }

            NoteHeader(state, detectedNoteColor)
            Spacer(Modifier.height(Dimens.spaceMd))

            if (!state.isBusy) {
                ExerciseSetup(
                    state = state,
                    onSelectPattern = onSelectPattern,
                    onSelectRootNote = onSelectRootNote,
                    onSelectBeats = onSelectBeats,
                    onSetTempo = onSetTempo,
                    onSelectNoteDisplay = onSelectNoteDisplay,
                )
                Spacer(Modifier.height(Dimens.spaceMd))
            }

            when (val phase = state.phase) {
                is LongTonePhase.Countdown -> CountdownDisplay(phase.secondsLeft)
                is LongTonePhase.Running -> RunningPanel(state)
                is LongTonePhase.Finished -> ResultCard(state, phase.result)
                else -> Unit
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

/**
 * The note to play right now, in letters and on the staff, with the note that follows beside it at
 * half the size - no caption needed, the smaller pair reads as "coming next" on its own.
 */
@Composable
private fun NoteHeader(state: LongToneUiState, detectedHighlightColor: Color) {
    // Notation shows what the player reads, so every note is transposed for the instrument.
    val currentNote = state.currentNote
    val written = state.transposition.writtenFor(currentNote)
    val detected = state.measurements.pitch?.detectedNote?.let(state.transposition::writtenFor)
    val nextNote = state.progress?.upcomingNotes?.firstOrNull()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            NotePresentation(
                note = written,
                label = state.writtenLabel(currentNote),
                showName = state.noteDisplay.showsName,
                showStaff = state.noteDisplay.showsStaff,
                color = MaterialTheme.colorScheme.primary,
                staffColor = MaterialTheme.colorScheme.onSurface,
                nameStyle = numericLarge,
                secondaryNote = detected.takeIf { it != written },
                secondaryColor = detectedHighlightColor,
            )
            if (nextNote != null) {
                NotePresentation(
                    note = state.transposition.writtenFor(nextNote),
                    label = state.writtenLabel(nextNote),
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
        Text(
            text = if (state.transposition == Transposition.CONCERT) {
                "${state.instrumentName} - ${"%.1f".format(currentNote.frequencyHz())} Hz"
            } else {
                "${state.instrumentName} - sounds ${currentNote.label}, " +
                    "${"%.1f".format(currentNote.frequencyHz())} Hz"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ExerciseSetup(
    state: LongToneUiState,
    onSelectPattern: (ExercisePattern) -> Unit,
    onSelectRootNote: (Note) -> Unit,
    onSelectBeats: (Beats) -> Unit,
    onSetTempo: (Int) -> Unit,
    onSelectNoteDisplay: (NoteDisplay) -> Unit,
) {
    val selection = state.selection

    SectionHeader("Exercise")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(Dimens.cardPadding)) {
            state.patterns.forEach { pattern ->
                SelectableRow(
                    title = pattern.name,
                    supportingText = pattern.notesFrom(selection.rootNote)
                        .joinToString(" ") { state.writtenLabel(it) },
                    selected = pattern == selection.pattern,
                    onClick = { onSelectPattern(pattern) },
                )
            }
        }
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("Starting note")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        items(state.selectableNotes, key = { it.midiNumber }) { note ->
            FilterChip(
                selected = note == selection.rootNote,
                onClick = { onSelectRootNote(note) },
                label = { Text(state.writtenLabel(note)) },
            )
        }
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("Note length")
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        state.beatOptions.forEach { beats ->
            FilterChip(
                selected = beats == selection.beatsPerNote,
                onClick = { onSelectBeats(beats) },
                label = { Text("${beats.value.roundToInt()} beats") },
            )
        }
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("Tempo")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        SecondaryButton(text = "-5", onClick = { onSetTempo(selection.tempo.bpm - 5) })
        Text(
            text = "${selection.tempo.bpm} BPM",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        SecondaryButton(text = "+5", onClick = { onSetTempo(selection.tempo.bpm + 5) })
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("Note display")
    NoteDisplayPicker(
        options = state.noteDisplayOptions,
        selected = state.noteDisplay,
        label = { it.shortName },
        onSelect = onSelectNoteDisplay,
    )

    Spacer(Modifier.height(Dimens.spaceSm))
    Text(
        text = "${selection.beatsPerNote.value.roundToInt()} beats at ${selection.tempo.bpm} BPM - " +
            "${"%.1f".format(selection.secondsPerNote)} s per note, " +
            "${selection.pattern.noteCount} notes, ${formatDuration(selection.totalSeconds)} total",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CountdownDisplay(secondsLeft: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.spectrumHeight),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = secondsLeft.toString(),
                style = numericLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = "Get ready",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RunningPanel(state: LongToneUiState) {
    val measurements = state.measurements
    val progress = state.progress

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        if (progress != null) {
            ExercisePositionRow(state, progress)
            LinearProgressIndicator(
                progress = { progress.noteFraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricDisplay(
                label = "Elapsed",
                value = "%.1f".format(progress?.noteElapsedSeconds ?: 0f),
                unit = "s",
            )
            MetricDisplay(
                label = "Level",
                value = measurements.rmsDbfs.roundToInt().toString(),
                unit = "dB",
            )
            MetricDisplay(
                label = "Peak",
                value = measurements.peakDbfs.roundToInt().toString(),
                unit = "dB",
            )
        }
        LevelMeter(
            fraction = measurements.levelFraction,
            contentDescription = "Input level ${measurements.rmsDbfs.roundToInt()} decibels",
        )
        Text(
            text = if (measurements.signalPresent) "Signal detected" else "No signal - play louder",
            style = MaterialTheme.typography.labelLarge,
            color = if (measurements.signalPresent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        PitchPanel(state)
        SpectrumView(
            bands = measurements.spectrumBands,
            maxFrequencyHz = measurements.displayMaxFrequencyHz,
        )
        SpectrumAxisLabels(maxFrequencyHz = measurements.displayMaxFrequencyHz)
    }
}

/** Position in the exercise; the note that follows is shown in the header instead. */
@Composable
private fun ExercisePositionRow(state: LongToneUiState, progress: ExerciseProgress) {
    Text(
        text = "${state.selection.pattern.name}  " +
            "${progress.displayPosition} / ${progress.noteCount}",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Cents meter plus its caption. Purely a rendering of [PitchMeasurement]; no thresholds live here. */
@Composable
private fun PitchPanel(state: LongToneUiState) {
    val pitch = state.measurements.pitch
    val inTune = pitch != null && !pitch.offTarget && abs(pitch.cents) <= IN_TUNE_CENTS

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
        CentsMeter(
            cents = pitch?.cents,
            range = state.centsDisplayRange,
            inTune = inTune,
            offTarget = pitch?.offTarget == true,
            contentDescription = pitchDescription(state, pitch, inTune),
        )
        CentsMeterLabels(range = state.centsDisplayRange)
        Text(
            text = pitchDescription(state, pitch, inTune),
            style = MaterialTheme.typography.labelLarge,
            color = when {
                pitch == null || pitch.offTarget -> MaterialTheme.colorScheme.onSurfaceVariant
                inTune -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.tertiary
            },
        )
    }
}

private fun pitchDescription(
    state: LongToneUiState,
    pitch: PitchMeasurement?,
    inTune: Boolean,
): String = when {
    pitch == null -> "Listening..."
    pitch.offTarget ->
        "${state.writtenLabel(pitch.detectedNote)} detected - not the note you are on"

    inTune -> "In tune"
    else -> "${formatCents(pitch.cents)} - ${if (pitch.cents > 0) "sharp" else "flat"}"
}

/** Deviation treated as in tune by the UI copy; the meter itself stays continuous. */
private const val IN_TUNE_CENTS = 5f

/** The upcoming note is drawn at half the size of the one being played. */
private const val NEXT_NOTE_SCALE = 0.5f
private val NextNoteStaffSpacing = 5.dp

/** Signed cents, e.g. "+7 ct" or "-12 ct". */
private fun formatCents(cents: Float): String {
    val rounded = cents.roundToInt()
    return if (rounded > 0) "+$rounded ct" else "$rounded ct"
}

private fun formatDuration(seconds: Float): String {
    val total = seconds.roundToInt()
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun ResultCard(state: LongToneUiState, result: LongToneResult) {
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
                text = if (result.completed) "Exercise finished" else "Stopped early",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MetricDisplay(
                    label = "Played",
                    value = "%.1f".format(result.heldSeconds),
                    unit = "s",
                )
                MetricDisplay(
                    label = "Notes",
                    value = "${result.steps.count { it.completed }}/" +
                        "${state.selection.pattern.noteCount}",
                )
                MetricDisplay(
                    label = "Sounding",
                    value = (result.steadyFraction * 100).roundToInt().toString(),
                    unit = "%",
                )
            }
            Text(
                text = "Average level ${result.averageLevelDbfs.roundToInt()} dB - " +
                    "tone sounding for ${"%.1f".format(result.soundingSeconds)} s",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Average pitch: " + (result.averagePitchCents?.let(::formatCents) ?: "-"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Pitch stability: " +
                    (result.pitchStabilityCents?.let { "+/-${it.roundToInt()} ct" } ?: "-"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (result.steps.size > 1) {
                Spacer(Modifier.height(Dimens.spaceXs))
                result.steps.forEachIndexed { index, step ->
                    Text(
                        text = "${index + 1}. ${state.writtenLabel(step.note)}  " +
                            "${"%.1f".format(step.heldSeconds)} s  " +
                            (step.averagePitchCents?.let(::formatCents) ?: "-"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Actions(
    state: LongToneUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        when (state.phase) {
            is LongTonePhase.Countdown, is LongTonePhase.Running -> PrimaryButton(
                text = "Stop",
                onClick = onStop,
                modifier = Modifier.weight(1f),
            )

            is LongTonePhase.Finished -> {
                PrimaryButton(
                    text = "Practice again",
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "Change exercise",
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                )
            }

            else -> PrimaryButton(
                text = "Start",
                onClick = onStart,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ErrorCard(error: AudioCaptureError, onOpenAppSettings: (() -> Unit)?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.spaceMd),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            Text(
                text = when (error) {
                    AudioCaptureError.PermissionDenied ->
                        "Microphone access is needed to listen to your playing."

                    AudioCaptureError.DeviceUnavailable ->
                        "The microphone is in use by another app."

                    AudioCaptureError.InitializationFailed ->
                        "The microphone could not be started. Try again."

                    AudioCaptureError.UnsupportedConfiguration ->
                        "This device does not support the required audio format."

                    is AudioCaptureError.ReadFailed ->
                        "Recording stopped unexpectedly. Try again."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (error == AudioCaptureError.PermissionDenied && onOpenAppSettings != null) {
                SecondaryButton(text = "Open app settings", onClick = onOpenAppSettings)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LongToneScreenPreview() {
    SaxTrainerTheme {
        LongToneScreen(
            state = LongToneUiState(
                selection = ExerciseSelection(
                    pattern = SequenceCatalog.ascendingThirds,
                    rootNote = SequenceCatalog.defaultRoot,
                    beatsPerNote = Beats.of(4),
                    tempo = Tempo(60),
                ),
                patterns = SequenceCatalog.patterns,
                selectableNotes = Note.range(
                    SequenceCatalog.defaultRoot.transposedBy(-12),
                    SequenceCatalog.defaultRoot.transposedBy(3),
                ),
                beatOptions = listOf(Beats.of(2), Beats.of(4), Beats.of(8)),
                tempoRange = Tempo.SUPPORTED_RANGE,
                transposition = Transposition.B_FLAT,
                instrumentName = "Tenor saxophone",
            ),
            onSelectPattern = {},
            onSelectRootNote = {},
            onSelectBeats = {},
            onSetTempo = {},
            onSelectNoteDisplay = {},
            onStart = {},
            onStop = {},
            onReset = {},
            onBack = {},
        )
    }
}
