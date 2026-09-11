package com.example.saxtrainer.feature.longtones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.audio.capture.AudioCaptureError
import com.example.saxtrainer.core.designsystem.component.CentsMeter
import com.example.saxtrainer.core.designsystem.component.CentsMeterLabels
import com.example.saxtrainer.core.designsystem.component.LevelMeter
import com.example.saxtrainer.core.designsystem.component.MetricDisplay
import com.example.saxtrainer.core.designsystem.component.DEFAULT_STAFF_MARGIN
import com.example.saxtrainer.core.designsystem.component.NoteDisplayPicker
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.ScreenTiming
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.SelectableRow
import com.example.saxtrainer.core.designsystem.text.toFixed
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
import kotlin.math.ceil
import kotlin.math.roundToInt
import com.example.saxtrainer.core.designsystem.component.MusicalStaffView
import com.example.saxtrainer.core.designsystem.component.StaffNoteStyle
import com.example.saxtrainer.core.designsystem.component.StaffPrefix
import com.example.saxtrainer.core.designsystem.component.reservedStepRange
import com.example.saxtrainer.core.music.TimeSignature
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import com.example.saxtrainer.core.music.conventionalStyle
import com.example.saxtrainer.core.designsystem.component.CurrentAndNextNote
import com.example.saxtrainer.core.designsystem.component.NextNoteName
import androidx.compose.material3.Switch

/** Stateless long tone UI. All measurements arrive ready to render in [state]. */
@Composable
fun LongToneScreen(
    state: LongToneUiState,
    onSelectPattern: (ExercisePattern) -> Unit,
    onSelectRootNote: (Note) -> Unit,
    onSelectBeats: (Beats) -> Unit,
    onSelectNoteDisplay: (NoteDisplay) -> Unit,
    onShowNextNote: (Boolean) -> Unit,
    onStart: () -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToLessons: (() -> Unit)? = null,
    permissionDenied: Boolean = false,
    onOpenAppSettings: (() -> Unit)? = null,
    detectedNoteColor: Color = MaterialTheme.colorScheme.error,
) {
    ScreenScaffold(
        title = "Long Tones",
        onBack = onBack,
        modifier = modifier,
        header = headerOf(state),
        // The note being held is the subject; the meters that measure it belong below.
        subject = { NoteHeader(state, detectedNoteColor) },
        // While it runs the readings size themselves to the screen; the setup list still scrolls.
        scrollable = state.phase !is LongTonePhase.Running,
        timing = timingOf(state),
        actions = {
            Actions(
                state = state,
                onStart = onStart,
                onTogglePause = onTogglePause,
                onStop = onStop,
                onReset = onReset,
            )
        },
    ) {
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

        if (!state.isBusy && state.isPreconfigured) {
            ReadyPanel(state)
            Spacer(Modifier.height(Dimens.spaceMd))
        }

        if (!state.isBusy && !state.isPreconfigured) {
            ExerciseSetup(
                state = state,
                onSelectPattern = onSelectPattern,
                onSelectRootNote = onSelectRootNote,
                onSelectBeats = onSelectBeats,
                onSelectNoteDisplay = onSelectNoteDisplay,
                onShowNextNote = onShowNextNote,
            )
            if (onAddToLessons != null) {
                Spacer(Modifier.height(Dimens.spaceMd))
                SecondaryButton(
                    text = "Add to your lessons",
                    onClick = onAddToLessons,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Dimens.spaceMd))
        }

        when (val phase = state.phase) {
            is LongTonePhase.Countdown -> CountdownDisplay(phase.beatsRemaining, state.isPaused)
            is LongTonePhase.Running -> RunningPanel(state)
            is LongTonePhase.Finished -> ResultCard(state, phase.result)
            else -> Unit
        }
    }
}

/**
 * What a ready-made exercise is about to ask for.
 *
 * The counterpart of the setup panel for an exercise that arrived decided: it says what will be
 * played rather than offering to change it, because what to practise was somebody else's choice and
 * inviting the player to undo it by accident helps nobody.
 */
@Composable
private fun ReadyPanel(state: LongToneUiState) {
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
                text = state.selection.pattern.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = state.selection.notes.joinToString(" ") { state.writtenLabel(it) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    val nextNote = state.progress?.upcomingNotes?.firstOrNull()?.takeIf { state.showNextNote }
    // Before the exercise runs there is no schedule yet, so the setting speaks for itself.
    val currentDisplay = state.progress?.noteDisplay ?: state.noteDisplay

    val currentColor = MaterialTheme.colorScheme.primary

    // These beats are real music: the player picks how many a tone lasts and at what tempo, so the
    // duration is written out as the note value it actually is - four beats a whole note, two a
    // half - and a tone that runs past a bar line is split there and tied, as it would be on paper.
    val writtenNotes = remember(state.selection.notes, state.transposition) {
        state.selection.notes.map(state.transposition::writtenFor)
    }
    val beats = state.selection.beatsPerNote
    val sequence = remember(written, beats) {
        ToneSequence(
            steps = listOf(ToneStep(written, beats)),
            timeSignature = NOTE_HEADER_TIME_SIGNATURE,
            // Spelled the way the note is usually written, as the label beside it always was.
            accidentalStyle = written.conventionalStyle(),
        )
    }

    CurrentAndNextNote(
        modifier = Modifier.fillMaxWidth(),
        next = nextNote?.let { { NextNoteName(state.writtenLabel(it)) } },
    ) {
        MusicalStaffView(
            sequence = sequence,
            modifier = Modifier.fillMaxWidth(),
            color = currentColor,
            staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
            lineSpacing = CurrentNoteStaffSpacing,
            naming = state.noteNaming,
            prefix = StaffPrefix.treble(NOTE_HEADER_TIME_SIGNATURE),
            // The name-or-notation setting arrives as a per-note choice, which is the only shape
            // the design system understands - it never learns what a setting is.
            noteStyle = {
                if (currentDisplay.showsStaff) StaffNoteStyle.HEAD else StaffNoteStyle.NAME
            },
            labelBackground = MaterialTheme.colorScheme.surface,
            fillWidth = true,
            closingBar = false,
            centerMusic = true,
            reserveAccidentals = true,
            // Sized for every note of the exercise plus room around it, so neither a change of
            // presentation nor the heard note appearing beside the target moves the staff.
            stepRange = reservedStepRange(writtenNotes),
            // What the microphone is hearing, held up beside what was asked for.
            secondaryNote = { detected.takeIf { it != written } },
            secondaryColor = detectedHighlightColor,
        )
    }
}

/**
 * The bar this practice counts in.
 *
 * Named rather than assumed: the notation model takes any time signature, and a practice that one
 * day counts in three or in six will say so here instead of having four written into the drawing.
 */
private val NOTE_HEADER_TIME_SIGNATURE = TimeSignature.FOUR_FOUR

/**
 * What the note on screen actually sounds like on the horn in the player's hands.
 *
 * Detail rather than subject: it belongs with the readings below, not beside the note itself,
 * which is why it reads left aligned like the rest of them.
 */
@Composable
private fun SoundingLine(state: LongToneUiState) {
    val currentNote = state.currentNote

    Text(
        text = if (state.transposition == Transposition.CONCERT) {
            "${state.instrumentName} - ${currentNote.frequencyHz().toFixed(1)} Hz"
        } else {
            "${state.instrumentName} - sounds ${state.soundingLabel(currentNote)}, " +
                "${currentNote.frequencyHz().toFixed(1)} Hz"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseSetup(
    state: LongToneUiState,
    onSelectPattern: (ExercisePattern) -> Unit,
    onSelectRootNote: (Note) -> Unit,
    onSelectBeats: (Beats) -> Unit,
    onSelectNoteDisplay: (NoteDisplay) -> Unit,
    onShowNextNote: (Boolean) -> Unit,
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
    // Wrapped at the edge of the screen rather than scrolled, so every note is in view at once.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        state.selectableNotes.forEach { note ->
            FilterChip(
                selected = note == selection.rootNote,
                onClick = { onSelectRootNote(note) },
                label = { Text(state.writtenLabel(note)) },
            )
        }
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("Note length")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        state.beatOptions.forEach { beats ->
            FilterChip(
                selected = beats == selection.beatsPerNote,
                onClick = { onSelectBeats(beats) },
                label = { Text("${beats.value.roundToInt()} beats", maxLines = 1) },
            )
        }
    }
    Text(
        text = "At ${selection.tempo.bpm} BPM",
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
        Switch(checked = state.showNextNote, onCheckedChange = onShowNextNote)
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("Note display")
    NoteDisplayPicker(
        options = state.noteDisplayOptions,
        selected = state.noteDisplay,
        label = { it.shortName },
        onSelect = onSelectNoteDisplay,
    )
}

@Composable
private fun CountdownDisplay(beatsRemaining: Int, paused: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.spectrumHeight),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
}

@Composable
private fun ColumnScope.RunningPanel(state: LongToneUiState) {
    val measurements = state.measurements
    val progress = state.progress

    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        if (progress != null) {
            ExercisePositionRow(state, progress)
        }
        SoundingLine(state)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricDisplay(
                label = "Elapsed",
                value = (progress?.noteElapsedSeconds ?: 0f).toFixed(1),
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
        // Whatever room is left over after the readings above it: the taller the screen, the taller
        // the spectrum, rather than a fixed block that has to be scrolled to on a small one.
        SpectrumView(
            bands = measurements.spectrumBands,
            maxFrequencyHz = measurements.displayMaxFrequencyHz,
            modifier = Modifier.weight(1f),
        )
        SpectrumAxisLabels(maxFrequencyHz = measurements.displayMaxFrequencyHz)
    }
}

/** Position in the exercise; the note that follows is shown in the header instead. */
@Composable
private fun ExercisePositionRow(state: LongToneUiState, progress: ExerciseProgress) {
    Text(
        text = state.selection.pattern.name,
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
private val CurrentNoteStaffSpacing = 12.dp

/** Signed cents, e.g. "+7 ct" or "-12 ct". */
private fun formatCents(cents: Float): String {
    val rounded = cents.roundToInt()
    return if (rounded > 0) "+$rounded ct" else "$rounded ct"
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
                    value = result.heldSeconds.toFixed(1),
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
                    "tone sounding for ${result.soundingSeconds.toFixed(1)} s",
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
                            "${step.heldSeconds.toFixed(1)} s  " +
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
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        when (state.phase) {
            // Pause takes the primary place while an attempt is on: stopping ends it, holding it
            // does not, and a long sequence of tones is exactly where that matters.
            is LongTonePhase.Countdown, is LongTonePhase.Running -> {
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
                transposition = Transposition.TENOR_B_FLAT,
                instrumentName = "Tenor saxophone",
            ),
            onSelectPattern = {},
            onSelectRootNote = {},
            onSelectBeats = {},
            onSelectNoteDisplay = {},
            onShowNextNote = {},
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
private fun headerOf(state: LongToneUiState): ScreenHeader = when (state.phase) {
    is LongTonePhase.Countdown -> ScreenHeader.Question("Get ready")
    LongTonePhase.Running -> ScreenHeader.Question("Hold it steady")
    is LongTonePhase.Finished -> ScreenHeader.Question("How did that go?")
    // Idle and Failed both leave the player choosing what to do next.
    else -> ScreenHeader.Description(
        "Hold each note steady, with the pitch and the level shown as you play.",
    )
}

/**
 * How long is left on the note, for the bar across the foot of the screen.
 *
 * Null whenever nothing is being timed, which takes the bar off the screen rather than leaving an
 * empty one behind.
 */
private fun timingOf(state: LongToneUiState): ScreenTiming? {
    val progress = state.progress ?: return null
    if (state.phase !is LongTonePhase.Running) return null

    val remaining = (progress.noteTargetSeconds - progress.noteElapsedSeconds).coerceAtLeast(0f)
    return ScreenTiming(
        fraction = progress.noteFraction,
        sessionFraction = sessionFractionOf(progress.displayPosition, progress.noteCount),
        step = progress.displayPosition,
        stepCount = progress.noteCount,
        secondsLeft = ceil(remaining).toInt(),
        secondsAtMost = ceil(progress.noteTargetSeconds).toInt(),
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
