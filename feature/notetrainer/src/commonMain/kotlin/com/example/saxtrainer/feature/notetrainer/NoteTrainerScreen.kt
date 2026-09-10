package com.example.saxtrainer.feature.notetrainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.example.saxtrainer.core.designsystem.component.MusicalStaffView
import com.example.saxtrainer.core.designsystem.component.NoteChoiceDialog
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.SaxophoneFingeringView
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.ScreenTiming
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.SelectableRow
import com.example.saxtrainer.core.designsystem.component.StaffPrefix
import com.example.saxtrainer.core.designsystem.component.fingeringHeightOf
import com.example.saxtrainer.core.designsystem.component.reservedStepRange
import com.example.saxtrainer.core.designsystem.component.staffStepRange
import com.example.saxtrainer.core.designsystem.text.toFixed
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.FeedbackColors
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.ToneStep
import com.example.saxtrainer.core.music.conventionalStyle
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.Dp
import com.example.saxtrainer.core.designsystem.component.fingeringWidthOf

/**
 * The note trainer, stateless.
 *
 * While a note is being asked for the screen holds one thing: the note, drawn on the staff. No
 * dashboard, because the player is looking at their hands and the horn is in their mouth.
 */
@Composable
fun NoteTrainerScreen(
    state: NoteTrainerUiState,
    onSelectNoteSet: (NoteSet) -> Unit,
    onEditCustomNotes: () -> Unit,
    onToggleCustomNote: (Note) -> Unit,
    onSelectAllCustomNotes: () -> Unit,
    onClearCustomNotes: () -> Unit,
    onApplyCustomNotes: () -> Unit,
    onCancelCustomNotes: () -> Unit,
    onRandomChange: (Boolean) -> Unit,
    onRoundsChange: (Int) -> Unit,
    onShowFingeringHintChange: (Boolean) -> Unit,
    onShowNoteNameChange: (Boolean) -> Unit,
    onTargetChange: (Int) -> Unit,
    onHoldChange: (Beats) -> Unit,
    onTempoChange: (Int) -> Unit,
    onStart: () -> Unit,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToLessons: (() -> Unit)? = null,
) {
    // The note being read, kept apart from everything that answers it.
    val noteSubject: (@Composable ColumnScope.() -> Unit)? =
        if (state.phase is NoteTrainerPhase.Running) ({ NoteSubject(state) }) else null

    // The fingering hint is sized from the screen rather than fixed, so it cannot be the thing that
    // pushes what the player is being asked to do off the bottom. Measured out here, once, because
    // the panel it is drawn in sits inside a column that scrolls - and a scrolling column has no
    // height to offer anybody.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val hintWidth = fingeringWidthOf(
            (maxHeight * HintHeightShare).coerceIn(MinHintHeight, MaxHintHeight),
        )

        ScreenScaffold(
            title = "Note Trainer",
            onBack = onBack,
            modifier = modifier,
            header = headerOf(state),
            subject = noteSubject,
            timing = timingOf(state),
            actions = {
                Actions(state, onStart, onTogglePause, onSkip, onStop, onReset)
            },
        ) {
            Spacer(Modifier.height(Dimens.spaceSm))

            when (val phase = state.phase) {
                is NoteTrainerPhase.Idle -> if (state.isPreconfigured) {
                    ReadyPanel(state)
                } else {
                    Setup(
                        state = state,
                        onSelectNoteSet = onSelectNoteSet,
                        onEditCustomNotes = onEditCustomNotes,
                        onRandomChange = onRandomChange,
                        onRoundsChange = onRoundsChange,
                        onShowFingeringHintChange = onShowFingeringHintChange,
                        onShowNoteNameChange = onShowNoteNameChange,
                        onTargetChange = onTargetChange,
                        onHoldChange = onHoldChange,
                        onTempoChange = onTempoChange,
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

                is NoteTrainerPhase.CountIn -> CountInPanel(phase.beatsRemaining, state.isPaused)
                is NoteTrainerPhase.Running -> RunningPanel(state, hintWidth)
                is NoteTrainerPhase.Finished -> ResultCard(state, phase.result)
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
}

@Composable
private fun Setup(
    state: NoteTrainerUiState,
    onSelectNoteSet: (NoteSet) -> Unit,
    onEditCustomNotes: () -> Unit,
    onRandomChange: (Boolean) -> Unit,
    onRoundsChange: (Int) -> Unit,
    onShowFingeringHintChange: (Boolean) -> Unit,
    onShowNoteNameChange: (Boolean) -> Unit,
    onTargetChange: (Int) -> Unit,
    onHoldChange: (Beats) -> Unit,
    onTempoChange: (Int) -> Unit,
) {
    SectionHeader("Notes")
    state.noteSets.forEach { set ->
        SelectableRow(
            title = set.displayName,
            supportingText = state.summaryOf(set, SaxophoneFingerings),
            selected = set == state.noteSet,
            onClick = {
                if (set == NoteSet.CUSTOM) onEditCustomNotes() else onSelectNoteSet(set)
            },
        )
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    SwitchRow(
        title = "Mixed order",
        supportingText = "Read the note instead of reciting the scale",
        checked = state.isRandom,
        onCheckedChange = onRandomChange,
    )
    SwitchRow(
        title = "Show the fingering",
        supportingText = "A hint while you look for the note; off, the grip is yours to recall",
        checked = state.showFingeringHint,
        onCheckedChange = onShowFingeringHintChange,
    )
    SwitchRow(
        title = "Show the note name",
        supportingText = "Under the staff, for when you want it. Reading the notation is the point",
        checked = state.showNoteName,
        onCheckedChange = onShowNoteNameChange,
    )

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("Rounds")
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        state.roundOptions.forEach { rounds ->
            FilterChip(
                selected = rounds == state.rounds,
                onClick = { onRoundsChange(rounds) },
                label = { Text("$rounds") },
            )
        }
    }

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("Quick is")
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        state.targetOptions.forEach { seconds ->
            FilterChip(
                selected = seconds == state.targetSeconds,
                onClick = { onTargetChange(seconds) },
                label = { Text("$seconds s") },
            )
        }
    }
    Text(
        text = "Only for the score. A note waits as long as you need it to.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(Dimens.spaceSm))
    SectionHeader("Hold the note")
    // Two rows on purpose. Four of these in one row leaves the last chip too little width and it
    // wraps its own label instead; deciding where the break goes keeps every label on one line.
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        state.holdOptions.chunked(HOLD_OPTIONS_PER_ROW).forEach { options ->
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                options.forEach { beats ->
                    FilterChip(
                        selected = beats == state.holdBeats,
                        onClick = { onHoldChange(beats) },
                        label = { Text(holdLabelOf(beats), maxLines = 1) },
                    )
                }
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
        text = "Until correct moves on as soon as you find the note. A hold asks you to keep it " +
            "sounding, and starts again if you lose it - " +
            "${state.tempo.secondsFor(Beats.of(1)).toFixed(1)} s to a beat at this tempo.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (state.microphoneUnavailable) {
        Spacer(Modifier.height(Dimens.spaceSm))
        Text(
            text = "This exercise listens to what you play, so it needs the microphone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    } else if (state.activeNotes.isEmpty()) {
        Spacer(Modifier.height(Dimens.spaceSm))
        Text(
            text = "Choose at least one note to practise.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
            text = "$beatsRemaining",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = if (paused) "Paused" else "Get ready",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One note, as large as it will go, and as little else as possible. */
@Composable
private fun RunningPanel(state: NoteTrainerUiState, hintWidth: Dp) {
    val progress = state.progress ?: return

    val correctColor = FeedbackColors.correct
    val wrongColor = FeedbackColors.incorrect
    val noteColor = if (progress.isCorrect) correctColor else MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        if (state.showNoteName) {
            Text(
                text = state.nameOf(progress.currentNote),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            // A size up from the rest of the panel: this line is what the player is reading while
            // they play, from as far away as the horn puts them from the screen.
            text = feedbackOf(state, progress),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = when (progress.listening) {
                Listening.CORRECT -> correctColor
                Listening.WRONG_PITCH -> wrongColor
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        // How much of the hold is left, which is the one thing the words cannot say.
        //
        // Above the fingering rather than below it, and given its strip of screen whether or not
        // there is a hold running. The hint is a tall diagram and the least important thing here,
        // so it is what may be scrolled to; what the player is being asked to do right now has to
        // be on screen without looking for it.
        if (progress.isHolding && !state.isPaused) {
            LinearProgressIndicator(
                progress = { progress.holdFraction ?: 0f },
                modifier = Modifier.fillMaxWidth().height(HoldBarHeight),
                color = correctColor,
            )
        } else {
            Spacer(Modifier.height(HoldBarHeight))
        }

        if (state.showFingeringHint) {
            if (progress.fingering != null) {
                SaxophoneFingeringView(
                    fingering = progress.fingering,
                    width = hintWidth,
                    pressedColor = MaterialTheme.colorScheme.onSurface,
                    contentDescription = "Fingering for ${state.nameOf(progress.currentNote)}",
                )
            } else {
                Spacer(Modifier.height(fingeringHeightOf(hintWidth)))
            }
        }
    }
}

/**
 * What the screen says about what it is hearing, always naming the note it heard.
 *
 * The note being asked for is on the staff above; this says what is actually coming out of the
 * horn, so that a wrong note is something the player can recognise rather than merely be told
 * about. [SuccessTick] marks the moment it landed, and stays up for as long as the note does.
 */
private fun feedbackOf(state: NoteTrainerUiState, progress: NoteTrainerProgress): String {
    if (state.isPaused) return "Paused"

    val heard = progress.detectedNote?.let(state::nameOf)
    return when {
        progress.landed -> {
            val landed = "$SuccessTick Correct, this is ${state.nameOf(progress.currentNote)}"
            progress.lastReactionSeconds?.let { "$landed - ${it.toFixed(1)} s" } ?: landed
        }

        // Right, but not yet kept for as long as was asked. With no hold to keep, the note is
        // already on its way out and there is nothing to ask the player to do.
        progress.listening == Listening.CORRECT ->
            heard?.let { if (state.holdsUntilCorrect) "This is $it" else "This is $it - hold it" }
                ?: "Hold it"

        progress.listening == Listening.WRONG_PITCH ->
            heard?.let { "This is $it - keep on" } ?: "Not that one"

        // Something was played but no pitch could be read from it, which is not the same as
        // playing the wrong note and is not treated like one.
        progress.listening == Listening.UNCLEAR -> "Keep the tone going"
        else -> "Play it"
    }
}

/** How a hold reads on the setup screen; zero is no hold at all. */
private fun holdLabelOf(beats: Beats): String = when {
    beats <= Beats.ZERO -> "Until correct"
    beats == Beats.of(1) -> "1 beat"
    else -> "${beats.value.toInt()} beats"
}

/** What a ready-made exercise is about to ask for. It was decided elsewhere; nothing to change. */
@Composable
private fun ReadyPanel(state: NoteTrainerUiState) {
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(state: NoteTrainerUiState, result: NoteTrainerResult) {
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
                text = if (result.completed) "Note practice complete" else "Stopped early",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${result.correctCount} of ${result.attempts.size} played",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.medianReactionSeconds?.let { median ->
                Text(
                    text = "Usually ${median.toFixed(1)} s, " +
                        "${result.withinTarget(state.targetSeconds)} inside " +
                        "${state.targetSeconds} s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val correctColor = FeedbackColors.correct
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                result.attempts.forEach { attempt ->
                    Text(
                        text = state.nameOf(attempt.note),
                        style = MaterialTheme.typography.titleMedium,
                        color = when {
                            attempt.skipped -> MaterialTheme.colorScheme.onSurfaceVariant
                            attempt.wrongTries == 0 -> correctColor
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Actions(
    state: NoteTrainerUiState,
    onStart: () -> Unit,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        when (state.phase) {
            is NoteTrainerPhase.Idle -> PrimaryButton(
                text = "Start",
                onClick = onStart,
                enabled = state.canStart,
                modifier = Modifier.weight(1f),
            )

            is NoteTrainerPhase.CountIn -> {
                PauseButton(state, onTogglePause)
                SecondaryButton(text = "Stop", onClick = onStop, modifier = Modifier.weight(1f))
            }

            is NoteTrainerPhase.Running -> {
                PauseButton(state, onTogglePause)
                SecondaryButton(text = "Skip", onClick = onSkip, modifier = Modifier.weight(1f))
                SecondaryButton(text = "Stop", onClick = onStop, modifier = Modifier.weight(1f))
            }

            is NoteTrainerPhase.Finished -> {
                PrimaryButton(
                    text = "Again",
                    onClick = onStart,
                    enabled = state.canStart,
                    modifier = Modifier.weight(1f),
                )
                if (!state.isPreconfigured) {
                    SecondaryButton(
                        text = "Change notes",
                        onClick = onReset,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.PauseButton(state: NoteTrainerUiState, onTogglePause: () -> Unit) {
    PrimaryButton(
        text = if (state.isPaused) "Continue" else "Pause",
        onClick = onTogglePause,
        modifier = Modifier.weight(1f),
    )
}

private val NoteStaffSpacing = 22.dp
private val HoldBarHeight = 6.dp

/**
 * How much of the screen the fingering hint may take.
 *
 * It is the least important thing on the panel - the note is on the staff above it and the words in
 * between say what is being heard - so it gets a share of the screen rather than a size of its own.
 * The floor keeps it readable on a small phone; the ceiling stops it swelling into the whole of a
 * tablet.
 */
private const val HintHeightShare = 0.22f
private val MinHintHeight = 110.dp
private val MaxHintHeight = 200.dp

/** What says the note landed. A mark rather than a word, so it reads before it is read. */
private const val SuccessTick = "✓"

@Preview(showBackground = true)
@Composable
private fun NoteTrainerSetupPreview() {
    SaxTrainerTheme {
        NoteTrainerScreen(
            state = NoteTrainerUiState(
                activeNotes = listOf(Note(NoteName.C, 4), Note(NoteName.D, 4)),
                targetOptions = listOf(2, 4, 8),
                holdOptions = listOf(0, 1, 2, 3).map(Beats::of),
            ),
            onSelectNoteSet = {},
            onEditCustomNotes = {},
            onToggleCustomNote = {},
            onSelectAllCustomNotes = {},
            onClearCustomNotes = {},
            onApplyCustomNotes = {},
            onCancelCustomNotes = {},
            onRandomChange = {},
            onRoundsChange = {},
            onShowFingeringHintChange = {},
            onShowNoteNameChange = {},
            onTargetChange = {},
            onHoldChange = {},
            onTempoChange = {},
            onStart = {},
            onTogglePause = {},
            onSkip = {},
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
private fun headerOf(state: NoteTrainerUiState): ScreenHeader = when (state.phase) {
    NoteTrainerPhase.Idle -> ScreenHeader.Description(
        "Read a note, find the fingering and play it. The app listens and says what it heard.",
    )

    is NoteTrainerPhase.CountIn -> ScreenHeader.Question("Get ready")
    NoteTrainerPhase.Running -> ScreenHeader.Question("Play the note you see")
    is NoteTrainerPhase.Finished -> ScreenHeader.Question("How did that go?")
}

/**
 * How far through the exercise, for the bar across the foot of the screen.
 *
 * Deliberately no seconds: a note here waits for the player rather than the other way round, so
 * there is nothing running out to draw. The bar measures the run instead.
 */
private fun timingOf(state: NoteTrainerUiState): ScreenTiming? {
    val progress = state.progress ?: return null
    if (state.phase !is NoteTrainerPhase.Running) return null

    return ScreenTiming(
        fraction = sessionFractionOf(progress.displayPosition, progress.noteCount),
        step = progress.displayPosition,
        stepCount = progress.noteCount,
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
 * The note being read, and nothing else.
 *
 * A line of music one note long, so the note is engraved by the same code that engraves an
 * exercise: a clef in front of it, a written value, and a staff that runs the width of the screen.
 * The height is reserved for the widest note the exercise can ask for, so it does not resize
 * between notes and the player's eye stays where it was.
 *
 * The written value is the hold the player chose, because that is exactly what a note's length
 * tells a reader: keep this sounding for two beats and it is a half note. The staff carries no time
 * signature, though - one note is not a bar of music, and claiming a metre for it would say more
 * than the exercise knows.
 *
 * No closing bar: this is a note to read, not a piece that ends. What is being heard is said in
 * words underneath, which is where it has always been said.
 */
@Composable
private fun NoteSubject(state: NoteTrainerUiState) {
    val progress = state.progress ?: return
    val noteColor =
        if (progress.isCorrect) FeedbackColors.correct else MaterialTheme.colorScheme.primary

    val note = progress.currentNote
    val held = state.holdBeats
    val sequence = remember(note, held) {
        if (held > Beats.ZERO) {
            ToneSequence(
                steps = listOf(ToneStep(note, held)),
                accidentalStyle = note.conventionalStyle(),
            )
        } else {
            // "Until correct" asks for no length at all, so the note is written as a plain quarter:
            // something to read, rather than a claim about how long to keep it.
            ToneSequence.of(note)
        }
    }

    MusicalStaffView(
        sequence = sequence,
        modifier = Modifier.fillMaxWidth(),
        color = noteColor,
        staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
        lineSpacing = NoteStaffSpacing,
        naming = state.noteNaming,
        prefix = StaffPrefix.Treble,
        fillWidth = true,
        closingBar = false,
        centerMusic = true,
        reserveAccidentals = true,
        stepRange = reservedStepRange(state.activeNotes),
    )
}

/** How many holds go on one line. Two, so the longest label has room to stay on one line. */
private const val HOLD_OPTIONS_PER_ROW = 2
