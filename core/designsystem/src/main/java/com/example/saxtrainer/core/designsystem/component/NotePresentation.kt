package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
    accidentalStyle: AccidentalStyle = AccidentalStyle.SHARPS,
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
        accidentalStyle,
        density,
    ) {
        val stepRange = staffStepRange(notes, accidentalStyle)
        val staffWidth = staffWidthOf(staffLineSpacing, reserveSecondaryNote)
        val staffHeight = staffHeightOf(stepRange, staffLineSpacing)

        val measured = notes.map { measurer.measure(AnnotatedString(it.label), nameStyle).size }
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
 * The note being played now and the one after it, told apart by a rule and a caption.
 *
 * Without them the two read as one line of music, which is confusing as soon as they are shown
 * differently - a letter beside a note on a staff. Both notes sit in their own column under a
 * caption of the same height, so the two drawings line up however tall the caption is, and the rule
 * runs the full height of the taller column.
 */
@Composable
fun CurrentAndNextNote(
    modifier: Modifier = Modifier,
    nextLabel: String = "Next",
    next: (@Composable () -> Unit)? = null,
    current: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Caption(text = null)
            current()
        }

        if (next != null) {
            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = Dimens.spaceXs),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Caption(text = nextLabel)
                next()
            }
        }
    }
}

/** An empty caption still takes its line, which is what keeps the two notes on the same baseline. */
@Composable
private fun Caption(text: String?) {
    Text(
        text = text.orEmpty(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

private val DefaultStaffSpacing = 10.dp
