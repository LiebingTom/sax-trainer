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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.saxtrainer.core.audio.capture.AudioCaptureError
import com.example.saxtrainer.core.designsystem.component.LevelMeter
import com.example.saxtrainer.core.designsystem.component.MetricDisplay
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.designsystem.theme.numericLarge
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.Transposition
import kotlin.math.roundToInt

/** Stateless long tone UI. All measurements arrive ready to render in [state]. */
@Composable
fun LongToneScreen(
    state: LongToneUiState,
    onSelectNote: (Note) -> Unit,
    onSelectDuration: (Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    permissionDenied: Boolean = false,
    onOpenAppSettings: (() -> Unit)? = null,
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

            TargetHeader(state)
            Spacer(Modifier.height(Dimens.spaceMd))

            if (!state.isBusy) {
                SectionHeader("Target note")
                NotePicker(state = state, onSelectNote = onSelectNote)
                Spacer(Modifier.height(Dimens.spaceSm))
                SectionHeader("Duration")
                DurationPicker(state = state, onSelectDuration = onSelectDuration)
                Spacer(Modifier.height(Dimens.spaceMd))
            }

            when (val phase = state.phase) {
                is LongTonePhase.Countdown -> CountdownDisplay(phase.secondsLeft)
                is LongTonePhase.Running -> RunningPanel(state)
                is LongTonePhase.Finished -> ResultCard(phase.result)
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

@Composable
private fun TargetHeader(state: LongToneUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.writtenLabel(state.targetNote),
            style = numericLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (state.transposition == Transposition.CONCERT) {
                "${state.instrumentName} - ${"%.1f".format(state.targetNote.frequencyHz())} Hz"
            } else {
                "${state.instrumentName} - sounds ${state.targetNote.label}, " +
                    "${"%.1f".format(state.targetNote.frequencyHz())} Hz"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NotePicker(state: LongToneUiState, onSelectNote: (Note) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        items(state.selectableNotes, key = { it.midiNumber }) { note ->
            FilterChip(
                selected = note == state.targetNote,
                onClick = { onSelectNote(note) },
                label = { Text(state.writtenLabel(note)) },
            )
        }
    }
}

@Composable
private fun DurationPicker(state: LongToneUiState, onSelectDuration: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        state.durationOptions.forEach { seconds ->
            FilterChip(
                selected = seconds == state.targetDurationSeconds,
                onClick = { onSelectDuration(seconds) },
                label = { Text("$seconds s") },
            )
        }
    }
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
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricDisplay(
                label = "Elapsed",
                value = "%.1f".format(state.elapsedSeconds),
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
        SpectrumView(
            bands = measurements.spectrumBands,
            maxFrequencyHz = measurements.displayMaxFrequencyHz,
        )
        SpectrumAxisLabels(maxFrequencyHz = measurements.displayMaxFrequencyHz)
    }
}

@Composable
private fun ResultCard(result: LongToneResult) {
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
                text = if (result.completed) "Target reached" else "Stopped early",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MetricDisplay(
                    label = "Held",
                    value = "%.1f".format(result.heldSeconds),
                    unit = "s",
                )
                MetricDisplay(
                    label = "Target",
                    value = result.targetSeconds.toString(),
                    unit = "s",
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
                    text = "Change target",
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
                targetNote = Note(NoteName.G, 3),
                selectableNotes = Note.range(Note(NoteName.C, 3), Note(NoteName.C, 5)),
                transposition = Transposition.B_FLAT,
                instrumentName = "Tenor saxophone",
                targetDurationSeconds = 15,
                durationOptions = listOf(8, 15, 30),
            ),
            onSelectNote = {},
            onSelectDuration = {},
            onStart = {},
            onStop = {},
            onReset = {},
            onBack = {},
        )
    }
}
