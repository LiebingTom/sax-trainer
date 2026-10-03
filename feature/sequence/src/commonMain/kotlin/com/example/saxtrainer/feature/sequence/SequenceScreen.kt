package com.example.saxtrainer.feature.sequence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.saxtrainer.core.designsystem.component.MusicalPageView
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.PracticeTitleActions
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.ScreenTiming
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.StepSlider
import com.example.saxtrainer.core.designsystem.component.SelectableRow
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.FeedbackColors
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.designsystem.theme.numericLarge
import com.example.saxtrainer.core.exercise.NoteTiming
import com.example.saxtrainer.core.exercise.SequenceCategory
import com.example.saxtrainer.core.exercise.SequenceLibrary
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.Transposition
import kotlin.math.ceil

/** Stateless sequence trainer UI. Everything it needs is in [state]. */
@Composable
fun SequenceScreen(
    state: SequenceUiState,
    onSelectSequence: (String) -> Unit,
    onTempoChange: (Int) -> Unit,
    onAddInverseChange: (Boolean) -> Unit,
    onRepeatsChange: (Int) -> Unit,
    onMetronomeChange: (Boolean) -> Unit,
    onCheckPitchChange: (Boolean) -> Unit,
    onCheckTimingChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToLessons: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: ((Boolean) -> Unit)? = null,
) {
    ScreenScaffold(
        title = "Sequence Trainer",
        onBack = onBack,
        modifier = modifier,
        // Starring and saving are about the practice as it is set up, not something to reach for
        // while playing.
        titleActions = {
            if (!state.isRunning) {
                PracticeTitleActions("Sequence Trainer", isFavorite, onToggleFavorite, onAddToLessons)
            }
        },
        showTrail = !state.isRunning,
        keepScreenOn = state.isRunning,
        header = headerOf(state),
        timing = timingOf(state),
        actions = {
            Actions(state, onStart, onStop, onReset, onBack)
        },
    ) {
        Spacer(Modifier.height(Dimens.spaceSm))

        when (val phase = state.phase) {
            is SequencePhase.Idle -> if (state.isPreconfigured) {
                ReadyPanel(state)
            } else {
                Setup(
                    state = state,
                    onSelectSequence = onSelectSequence,
                    onTempoChange = onTempoChange,
                    onAddInverseChange = onAddInverseChange,
                    onRepeatsChange = onRepeatsChange,
                    onMetronomeChange = onMetronomeChange,
                    onCheckPitchChange = onCheckPitchChange,
                    onCheckTimingChange = onCheckTimingChange,
                )
            }

            is SequencePhase.CountIn -> CountInPanel(state, phase)
            is SequencePhase.Running -> RunningPanel(state)
            is SequencePhase.Finished -> ResultPanel(state, phase.result)
        }
    }
}

@Composable
private fun Setup(
    state: SequenceUiState,
    onSelectSequence: (String) -> Unit,
    onTempoChange: (Int) -> Unit,
    onAddInverseChange: (Boolean) -> Unit,
    onRepeatsChange: (Int) -> Unit,
    onMetronomeChange: (Boolean) -> Unit,
    onCheckPitchChange: (Boolean) -> Unit,
    onCheckTimingChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        SequenceCategory.entries.forEach { category ->
            val entries = state.library.filter { it.category == category }
            if (entries.isEmpty()) return@forEach

            SectionHeader(category.displayName)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = Dimens.cardPadding)) {
                    entries.forEach { definition ->
                        SelectableRow(
                            title = definition.name,
                            supportingText = definition.description,
                            selected = definition.id == state.selectedId,
                            onClick = { onSelectSequence(definition.id) },
                        )
                    }
                }
            }
        }

        SectionHeader("Tempo")
        Text(
            text = "${state.tempo.bpm} BPM",
            style = numericLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value = state.tempo.bpm.toFloat(),
            onValueChange = { onTempoChange(it.toInt()) },
            valueRange = state.tempoRange.first.toFloat()..state.tempoRange.last.toFloat(),
        )

        SectionHeader("How much")
        SwitchRow(
            title = "Add inverse",
            supportingText = "Play the notes back down to where they started.",
            checked = state.addInverse,
            onCheckedChange = onAddInverseChange,
        )
        StepSlider(
            title = "Repeat",
            supportingText = "${state.repeats} times through, " +
                "${state.sequence.notes.size} notes in all",
            value = state.repeats,
            options = state.repeatRange.toList(),
            valueLabel = { "$it" },
            onValueChange = onRepeatsChange,
        )

        SectionHeader("While you play")
        SwitchRow(
            title = "Metronome",
            supportingText = "A click on every beat, and a bar of count-in.",
            checked = state.metronome,
            enabled = state.metronomeAvailable,
            onCheckedChange = onMetronomeChange,
        )
        SwitchRow(
            title = "Check pitch",
            supportingText = "Marks a note when you play it. Uses the microphone.",
            checked = state.checkPitch,
            onCheckedChange = onCheckPitchChange,
        )
        SwitchRow(
            title = "Check timing",
            supportingText = "Says whether each note landed on the beat. Needs the click and the microphone.",
            checked = state.checkTiming,
            enabled = state.checkPitch,
            onCheckedChange = onCheckTimingChange,
        )
        if (state.checkUnavailable) {
            Text(
                text = "The microphone is not available, so nothing is being checked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** A ready-made exercise waiting for Start; there is nothing here to change. */
@Composable
private fun ReadyPanel(state: SequenceUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        Text(
            text = state.sequence.name ?: "Ready",
            style = MaterialTheme.typography.titleMedium,
        )
        MusicalPageView(
            sequence = state.sequence,
            modifier = Modifier.fillMaxWidth(),
            naming = state.noteNaming,
            octaves = state.octaveNumbering,
            // Nothing is being played yet, so there is no note to turn the page to: all of it is
            // shown, and the screen scrolls if the piece is a long one.
            systemsPerPage = Int.MAX_VALUE,
        )
        Text(
            text = "${state.tempo.bpm} BPM",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CountInPanel(state: SequenceUiState, phase: SequencePhase.CountIn) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        Text(text = "${phase.beatsRemaining}", style = numericLarge)
        Text(
            text = "Counting you in",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MusicalPageView(
            sequence = state.sequence,
            modifier = Modifier.fillMaxWidth(),
            naming = state.noteNaming,
            octaves = state.octaveNumbering,
        )
    }
}

@Composable
private fun RunningPanel(state: SequenceUiState) {
    val progress = state.progress ?: return
    val correctColor = FeedbackColors.correct

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        MusicalPageView(
            sequence = state.sequence,
            modifier = Modifier.fillMaxWidth(),
            naming = state.noteNaming,
            octaves = state.octaveNumbering,
            highlightIndex = progress.noteIndex,
            noteColor = { index ->
                when {
                    index == progress.noteIndex && progress.isCorrect -> correctColor
                    index < progress.noteIndex && index in progress.playedCorrectly -> correctColor
                    else -> null
                }
            },
        )

        Text(
            text = buildString {
                // A rest is a thing to be doing, not an absence of one: it is said so plainly,
                // and nothing is held up against it.
                append(progress.currentNote?.let(state::nameOf) ?: "Rest")
                if (state.checkPitch && progress.currentNote != null) {
                    append("  -  ")
                    val heard = progress.detectedNote
                    append(if (heard == null) "nothing heard" else "heard ${state.nameOf(heard)}")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (progress.isCorrect) correctColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultPanel(state: SequenceUiState, result: SequenceResult) {
    val correctColor = FeedbackColors.correct

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
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
                    text = if (result.completed) "Sequence complete" else "Stopped early",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = buildString {
                        append(result.attempts.size)
                        append(" of ")
                        append(result.plannedNoteCount)
                        append(" notes played")
                        if (result.checkedPitch) {
                            append(", ")
                            append(result.correctCount)
                            append(" correct")
                        }
                        if (result.checkedTiming) {
                            append(", ")
                            append(result.onTimeCount)
                            append(" on time")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val wrongColor = FeedbackColors.incorrect
        MusicalPageView(
            sequence = state.sequence,
            modifier = Modifier.fillMaxWidth(),
            naming = state.noteNaming,
            octaves = state.octaveNumbering,
            // The whole piece, so the player can look over everything they just played.
            systemsPerPage = Int.MAX_VALUE,
            noteColor = { index ->
                // By step, not by position in the list: rests are not graded, so they leave gaps
                // in the numbering of what is.
                result.attempts.firstOrNull { it.index == index }?.let { attempt ->
                    when (attempt.pitchCorrect) {
                        true -> correctColor
                        false -> wrongColor
                        null -> null
                    }
                }
            },
        )

        if (result.checkedTiming) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                result.attempts.forEach { attempt ->
                    Text(
                        text = "${state.nameOf(attempt.note)} " +
                            (attempt.timing?.displayName ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = timingColor(attempt.timing, correctColor),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun timingColor(timing: NoteTiming?, correctColor: Color): Color = when (timing) {
    NoteTiming.ON_TIME -> correctColor
    // Played, just not on the beat: full-strength text rather than a second accent.
    NoteTiming.EARLY, NoteTiming.LATE -> MaterialTheme.colorScheme.onSurface
    NoteTiming.NOT_PLAYED, null -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun Actions(
    state: SequenceUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        when (state.phase) {
            is SequencePhase.Running, is SequencePhase.CountIn -> PrimaryButton(
                text = "Stop",
                onClick = onStop,
                modifier = Modifier.weight(1f),
            )

            is SequencePhase.Finished -> {
                PrimaryButton(
                    text = "Play again",
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                )
                if (state.isPreconfigured) {
                    SecondaryButton("Done", onBack, modifier = Modifier.weight(1f))
                } else {
                    SecondaryButton("Change sequence", onReset, modifier = Modifier.weight(1f))
                }
            }

            is SequencePhase.Idle -> PrimaryButton(
                text = "Start",
                onClick = onStart,
                enabled = state.canStart,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SequenceSetupPreview() {
    SaxTrainerTheme {
        SequenceScreen(
            state = SequenceUiState(
                library = SequenceLibrary.all,
                selectedId = SequenceLibrary.cMajorUp.id,
                sequence = SequenceLibrary.cMajorUp.build(),
                tempo = Tempo(80),
                transposition = Transposition.TENOR_B_FLAT,
                instrumentName = "Tenor saxophone",
            ),
            onSelectSequence = {},
            onTempoChange = {},
            onAddInverseChange = {},
            onRepeatsChange = {},
            onMetronomeChange = {},
            onCheckPitchChange = {},
            onCheckTimingChange = {},
            onStart = {},
            onStop = {},
            onReset = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SequenceRunningPreview() {
    val definition = SequenceLibrary.dottedSteps
    SaxTrainerTheme {
        SequenceScreen(
            state = SequenceUiState(
                phase = SequencePhase.Running,
                library = SequenceLibrary.all,
                selectedId = definition.id,
                sequence = definition.build(),
                checkPitch = true,
                progress = SequenceProgress(
                    noteIndex = 2,
                    noteCount = definition.noteCount,
                    passIndex = 0,
                    passCount = 1,
                    currentNote = definition.steps[2].note,
                    elapsedBeats = definition.build().startBeatOf(2),
                    totalBeats = definition.build().totalBeats,
                    detectedNote = definition.steps[2].note,
                    isCorrect = true,
                    playedCorrectly = setOf(0, 1),
                ),
            ),
            onSelectSequence = {},
            onTempoChange = {},
            onAddInverseChange = {},
            onRepeatsChange = {},
            onMetronomeChange = {},
            onCheckPitchChange = {},
            onCheckTimingChange = {},
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
private fun headerOf(state: SequenceUiState): ScreenHeader = when (state.phase) {
    SequencePhase.Idle -> ScreenHeader.Description(
        "Play a scale or a pattern in time, with an optional click to play along to.",
    )

    is SequencePhase.CountIn -> ScreenHeader.Question("Get ready")
    SequencePhase.Running -> ScreenHeader.Question("Play along, in time")
    is SequencePhase.Finished -> ScreenHeader.Question("How did that go?")
}

/**
 * How long is left on the note, for the bar across the foot of the screen.
 *
 * Null whenever nothing is being timed, which takes the bar off the screen rather than leaving an
 * empty one behind.
 */
private fun timingOf(state: SequenceUiState): ScreenTiming? {
    val progress = state.progress ?: return null
    if (state.phase !is SequencePhase.Running) return null

    // The sequence is measured in beats; what is left of it only becomes seconds at the tempo.
    val left = Beats(progress.noteEndBeats.value - progress.elapsedBeats.value)
    val remaining = state.tempo.secondsFor(left).coerceAtLeast(0.0)
    // The thick bar is the note being played; the whole run runs underneath it, exactly as it
    // does on the other trainers that work through a list of notes.
    val noteBeats = Beats(progress.noteEndBeats.value - progress.noteStartBeats.value)
    return ScreenTiming(
        fraction = progress.noteFraction,
        sessionFraction = progress.fraction,
        step = progress.displayPosition,
        stepCount = progress.noteCount,
        secondsLeft = ceil(remaining).toInt(),
        secondsAtMost = ceil(state.tempo.secondsFor(noteBeats)).toInt(),
    )
}
