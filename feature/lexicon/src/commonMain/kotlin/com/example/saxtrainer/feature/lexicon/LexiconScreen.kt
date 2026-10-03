package com.example.saxtrainer.feature.lexicon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.component.MusicalNoteView
import com.example.saxtrainer.core.designsystem.component.PracticeTitleActions
import com.example.saxtrainer.core.designsystem.component.SaxophoneFingeringView
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.fingeringHeightOf
import com.example.saxtrainer.core.designsystem.component.writtenStepRange
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName

/**
 * The fingering lexicon, stateless.
 *
 * Fixed at the top, the note and its grips, so they never move while another note is looked up;
 * below, every note to look up, one row per octave; at the foot, a semitone down or up.
 */
@Composable
fun LexiconScreen(
    state: LexiconUiState,
    onSelect: (Note) -> Unit,
    onLower: () -> Unit,
    onHigher: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: ((Boolean) -> Unit)? = null,
) {
    ScreenScaffold(
        title = TITLE,
        onBack = onBack,
        modifier = modifier,
        titleActions = { PracticeTitleActions(TITLE, isFavorite, onToggleFavorite, null) },
        subject = { NoteAndFingerings(state) },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                SecondaryButton(
                    text = "Lower",
                    onClick = onLower,
                    enabled = state.canGoLower,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "Higher",
                    onClick = onHigher,
                    enabled = state.canGoHigher,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        verticalArrangement = Arrangement.Top,
    ) {
        NoteChips(state, onSelect)
    }
}

/**
 * The note on the staff - each spelling on its own staff with its name beneath - and every grip
 * for it, each captioned. Sized for the most grips any note has and the widest range of the
 * staff, so nothing here changes size from one note to the next.
 */
@Composable
private fun NoteAndFingerings(state: LexiconUiState) {
    val spellings = state.spellings
    // Room on the staff for every note the lexicon can show, in either spelling.
    val stepRange = remember(state.notes) { writtenStepRange(state.notes.flatMap(::spellingsOf)) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        // Each spelling on a staff of its own with its name beneath, so a sharp and a flat read as
        // the two notes they are rather than two heads crowded onto one staff.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
            verticalAlignment = Alignment.Top,
        ) {
            spellings.forEach { written ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MusicalNoteView(
                        written = written,
                        primaryColor = MaterialTheme.colorScheme.primary,
                        staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        stepRange = stepRange,
                        naming = state.noteNaming,
                        octaves = state.octaveNumbering,
                    )
                    Text(
                        text = state.noteNaming.labelOf(written, state.octaveNumbering),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(Dimens.spaceMd))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(fingeringHeightOf(FingeringWidth) + CaptionHeight),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd, Alignment.CenterHorizontally),
        ) {
            state.fingerings.all.forEachIndexed { index, fingering ->
                val caption = state.captionOf(index)
                Column(
                    modifier = Modifier.width(FingeringWidth + Dimens.spaceMd),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
                ) {
                    SaxophoneFingeringView(
                        fingering = fingering,
                        width = FingeringWidth,
                        pressedColor = MaterialTheme.colorScheme.primary,
                        contentDescription = "Fingering for ${state.title}: $caption",
                    )
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/** Every note to look up, a chip per key, one row per octave. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteChips(state: LexiconUiState, onSelect: (Note) -> Unit) {
    state.notes.groupBy { it.octave }.forEach { (octave, notes) ->
        SectionHeader("Octave ${state.octaveNumbering.numberOf(octave)}")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            notes.forEach { note ->
                FilterChip(
                    selected = note == state.current,
                    onClick = { onSelect(note) },
                    label = { Text(state.chipLabel(note)) },
                )
            }
        }
        Spacer(Modifier.height(Dimens.spaceSm))
    }
}

private const val TITLE = "Fingering Lexicon"

/** One grip's width: three of them side by side fit a phone's width with room to spare. */
private val FingeringWidth = 72.dp

/** Two lines of caption under a grip, so a long one does not push the row taller. */
private val CaptionHeight = 44.dp

@Preview(showBackground = true)
@Composable
private fun LexiconScreenPreview() {
    val note = Note(NoteName.A_SHARP, 4)
    SaxTrainerTheme {
        LexiconScreen(
            state = LexiconUiState(
                notes = SaxophoneFingerings.supportedNotes,
                current = note,
                fingerings = SaxophoneFingerings.fingeringsFor(note)!!,
            ),
            onSelect = {},
            onLower = {},
            onHigher = {},
            onBack = {},
        )
    }
}
