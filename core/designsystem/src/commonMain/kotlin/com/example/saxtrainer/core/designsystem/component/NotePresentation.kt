package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.conventionalStyle

/**
 * A fixed footprint for one note.
 *
 * A note takes a different amount of room depending on how it is shown - "C#5" is wider than "A4",
 * and a note on a ledger line is taller than one inside the staff. Reserving the widest and tallest
 * case up front means the surrounding layout never moves, whether the presentation changes from note
 * to note or stays the same all exercise.
 */
data class NoteSlot(
    val width: Dp,
    val height: Dp,
    val stepRange: IntRange,
    /** True when the staff keeps room for a second note whether or not one is being shown. */
    val reservesSecondaryNote: Boolean = false,
)

/**
 * Room enough for any of [notes], however they turn out to be shown.
 *
 * The three `may` flags say which presentations can occur, because the answer differs: a screen that
 * always shows both needs the name and the staff side by side, while one that shows a different one
 * of the two per note only needs the wider of them.
 */
@Composable
fun rememberNoteSlot(
    notes: List<Note>,
    nameStyle: TextStyle,
    staffLineSpacing: Dp,
    mayShowName: Boolean = true,
    mayShowStaff: Boolean = true,
    mayShowBothAtOnce: Boolean = mayShowName && mayShowStaff,
    reserveSecondaryNote: Boolean = false,
    /** What the letters are called, because "Fis" is wider than "F sharp" is tall. */
    naming: NoteNaming = NoteNaming.INTERNATIONAL,
    /** How each note is spelled, which decides both its name and the line it sits on. */
    styleOf: (Note) -> AccidentalStyle = Note::conventionalStyle,
    /** Extra staff steps either side - see [reservedStepRange]. Zero fits the notes exactly. */
    stepMargin: Int = 0,
): NoteSlot {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    return remember(
        notes,
        nameStyle,
        staffLineSpacing,
        mayShowName,
        mayShowStaff,
        mayShowBothAtOnce,
        reserveSecondaryNote,
        naming,
        density,
        stepMargin,
    ) {
        val stepRange = reservedStepRange(notes, styleOf, stepMargin)
        val staffWidth = staffWidthOf(staffLineSpacing, reserveSecondaryNote)
        val staffHeight = staffHeightOf(stepRange, staffLineSpacing)

        val measured = notes.map {
            measurer.measure(AnnotatedString(naming.labelOf(it, styleOf(it))), nameStyle).size
        }
        val nameWidth = with(density) { measured.maxOf { it.width }.toDp() }
        val nameHeight = with(density) { measured.maxOf { it.height }.toDp() }

        val width = when {
            mayShowBothAtOnce -> nameWidth + Dimens.spaceMd + staffWidth
            mayShowName && mayShowStaff -> maxOf(nameWidth, staffWidth)
            mayShowStaff -> staffWidth
            else -> nameWidth
        }
        NoteSlot(
            width = width,
            height = maxOf(staffHeight, nameHeight),
            stepRange = stepRange,
            reservesSecondaryNote = reserveSecondaryNote,
        )
    }
}

/**
 * Shows a note as its name, as notation, or as both.
 *
 * Which of the two appears is passed in as plain booleans rather than as a settings type, so the
 * design system stays free of app configuration while every screen honours the same preference.
 *
 * Given a [slot] the note is centred inside that fixed footprint, which is what a screen wants when
 * the presentation is decided per note: the letter and the staff then occupy the same box and
 * nothing around them moves as one gives way to the other.
 */
@Composable
fun NotePresentation(
    note: Note,
    label: String,
    showName: Boolean,
    showStaff: Boolean,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    staffColor: Color = color,
    /** The five lines behind the note. Kept apart so the note itself can stand out against them. */
    staffLineColor: Color = staffColor,
    nameStyle: TextStyle = MaterialTheme.typography.displaySmall,
    staffLineSpacing: Dp = DefaultStaffSpacing,
    secondaryNote: Note? = null,
    secondaryColor: Color = staffColor,
    /** How the note is spelled, so the staff agrees with whatever [label] says. */
    accidentalStyle: AccidentalStyle = note.conventionalStyle(),
    slot: NoteSlot? = null,
) {
    // A preference that hides both would leave nothing on screen; the name is the safer fallback.
    val name = showName || !showStaff

    val content = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            if (name) {
                Text(text = label, style = nameStyle, color = color, maxLines = 1)
            }
            if (showStaff) {
                MusicalNoteView(
                    primaryNote = note,
                    primaryColor = staffColor,
                    staffColor = staffLineColor,
                    secondaryNote = secondaryNote,
                    secondaryColor = secondaryColor,
                    lineSpacing = staffLineSpacing,
                    accidentalStyle = accidentalStyle,
                    stepRange = slot?.stepRange,
                    reserveSecondaryWidth = slot?.reservesSecondaryNote == true,
                )
            }
        }
    }

    if (slot == null) {
        Box(modifier = modifier) { content() }
    } else {
        Box(
            modifier = modifier.size(width = slot.width, height = slot.height),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/**
 * The note being played now, and a cue for the one after it.
 *
 * The two are not the same kind of thing and are deliberately not drawn as if they were. What is
 * being played is music: it has a pitch, a length and a place on a staff, and it gets the width to
 * say so. What comes next is a heads-up so a player can get their fingers ready - it has no length
 * and no place in the bar yet, because it starts when the player finishes - so it is given as a
 * name, small, off to one side, with a rule between the two to say they are different things.
 *
 * Putting the next note on the staff instead would be a lie about time: the staff is a timeline,
 * and a note placed on it claims to begin at a particular beat, which makes the same note read
 * differently depending on what came before it.
 */
@Composable
fun CurrentAndNextNote(
    modifier: Modifier = Modifier,
    nextLabel: String = "Next",
    next: (@Composable () -> Unit)? = null,
    current: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        // The music takes whatever width is left over, so the staff runs as far as it can.
        //
        // Nothing here may ask this side for an intrinsic height. A staff measures itself against
        // the width it is offered and so is built on BoxWithConstraints, which cannot answer an
        // intrinsic measurement at all - it throws. That is why the rule below sizes itself against
        // the cue beside it rather than against the row as a whole.
        Box(modifier = Modifier.weight(1f)) { current() }

        if (next != null) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
            ) {
                VerticalDivider(modifier = Modifier.fillMaxHeight())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    next()
                    Caption(text = nextLabel)
                }
            }
        }
    }
}

/**
 * A next note, given as its name.
 *
 * The one representation that claims nothing about time, which is why it is the only one a note
 * that has not started yet is allowed. Deliberately not configurable: if a next note could be shown
 * as notation, a player could read ahead by letter and never have to read the staff at all.
 */
@Composable
fun NextNoteName(
    name: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
) {
    Text(text = name, style = style, color = color, maxLines = 1, modifier = modifier)
}

/** Says which of the two columns is which, under the one that needs saying. */
@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

private val DefaultStaffSpacing = 10.dp
