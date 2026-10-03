package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.music.NotatedDuration
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.OctaveNumbering
import com.example.saxtrainer.core.music.NoteValue
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import com.example.saxtrainer.core.music.TrebleStaff
import com.example.saxtrainer.core.music.bars

/**
 * One row of a page: the bars that stand on it, and where they start in the whole piece.
 *
 * [sequence] is a piece of music in its own right - the bars of this row and nothing else - which is
 * what lets one row be drawn by the ordinary [MusicalStaffView]. [firstStep] is the only thing that
 * ties it back to the whole: the caller numbers notes by the piece, the row numbers them by itself,
 * and this is the difference between the two.
 */
data class StaffSystem(val firstStep: Int, val sequence: ToneSequence) {

    /** Steps of the whole piece that stand on this row. */
    val steps: IntRange get() = firstStep..(firstStep + sequence.steps.size - 1)
}

/**
 * The rows a piece is written on, at a given width.
 *
 * Whole bars only, and as many of them as fit: a bar is never split across two rows, and a row never
 * begins anywhere but at a bar line. Where even one bar is wider than the width offered it still
 * gets a row of its own - a row of one overfull bar reads better than one cut in half.
 *
 * Worked out without a canvas, from [StaffLayoutBuilder] alone, so the packing can be checked
 * without drawing anything.
 *
 * @param widthSpaces how much room a row has, in staff spaces.
 * @param firstPrefix the block at the head of the first row, which normally carries a time
 *   signature as well as a clef.
 * @param laterPrefix the block on every row after it. Giving it the same width as [firstPrefix]
 *   keeps every row's music starting in the same place, which is what makes a page look like one.
 */
fun systemsOf(
    sequence: ToneSequence,
    widthSpaces: Float,
    firstPrefix: StaffPrefix,
    laterPrefix: StaffPrefix,
): List<StaffSystem> {
    val bars = sequence.bars()
    val systems = mutableListOf<StaffSystem>()
    var first = 0

    while (first < bars.size) {
        val prefix = if (systems.isEmpty()) firstPrefix else laterPrefix
        var count = 1
        while (
            first + count < bars.size &&
            spacesOf(sequence, bars, first, count + 1, prefix) <= widthSpaces
        ) {
            count++
        }

        val steps = bars[first].first..bars[first + count - 1].last
        systems += StaffSystem(
            firstStep = steps.first,
            sequence = sequence.copy(steps = sequence.steps.slice(steps)),
        )
        first += count
    }

    return systems
}

/** How wide [count] bars from [first] come out, with [prefix] in front of them. */
private fun spacesOf(
    sequence: ToneSequence,
    bars: List<IntRange>,
    first: Int,
    count: Int,
    prefix: StaffPrefix,
): Float {
    val steps = bars[first].first..bars[first + count - 1].last
    val row = sequence.copy(steps = sequence.steps.slice(steps))
    return StaffLayoutBuilder.of(row, prefix).widthSpaces
}

/**
 * A piece of music as a page: whole bars laid out in rows, standing still while it is played.
 *
 * The alternative - one long line that scrolls under a fixed cursor - shows the note being played
 * and nothing else about the music. A player reading a sequence needs to see where they are *in the
 * piece*: the bar they are in, the bar coming next, the shape of the phrase. So the notes do not
 * move. The one thing that moves is the highlight, which travels along music that stays where it
 * was put, exactly as a player's eye travels along a printed page.
 *
 * How many bars go on a row is not fixed. The staff is sized so that [barsPerSystem] bars of the
 * music actually in front of it fill the width offered - within [minLineSpacing] and [lineSpacing],
 * so it never becomes unreadably small or comically large - and then each row takes as many whole
 * bars as that size allows. Simple 4/4 exercises of four quarter notes therefore come out at about
 * two bars a row on a phone, and more on a wider screen, without either number being written down
 * anywhere.
 *
 * Longer pieces are paged rather than scrolled: [systemsPerPage] rows are shown at a time, and the
 * page turns when the note being played is on another one. A page turn is a jump, not a slide,
 * because that is the only movement a reader can follow while playing.
 *
 * [highlightIndex] and [noteColor] are indexed by step of the whole piece, not of a row - a caller
 * never has to know the music was broken up at all.
 */
@Composable
fun MusicalPageView(
    sequence: ToneSequence,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    noteColor: (index: Int) -> Color? = { null },
    naming: NoteNaming = NoteNaming.INTERNATIONAL,
    /** Which number each octave is shown with, wherever a note is named. */
    octaves: OctaveNumbering = OctaveNumbering.SCIENTIFIC,
    color: Color = LocalContentColor.current,
    staffColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    /** The largest the staff is drawn, and the size it takes when the music is short enough. */
    lineSpacing: Dp = DefaultPageLineSpacing,
    /** The smallest it shrinks to in order to fit a row of bars, before it gives up and fits fewer. */
    minLineSpacing: Dp = MinPageLineSpacing,
    /** How many bars a row aims to hold; the width available has the last word. */
    barsPerSystem: Int = DefaultBarsPerSystem,
    /** Rows shown at once. [Int.MAX_VALUE] puts the whole piece on one page, however long it is. */
    systemsPerPage: Int = DefaultSystemsPerPage,
    /** A second note shown beside the one at that index - see [MusicalStaffView]'s own parameter. */
    secondaryNote: (index: Int) -> Note? = { null },
    secondaryColor: Color = staffColor,
    /** The second note as written - see [MusicalStaffView]'s own parameter. */
    secondaryWritten: (index: Int) -> SpelledNote? = { index ->
        secondaryNote(index)?.let { SpelledNote.of(it, sequence.accidentalStyle) }
    },
) {
    val firstPrefix = StaffPrefix.treble(sequence.timeSignature)
    // A clef on every row, a time signature only on the first - and the same room kept for it
    // throughout, so every row's music begins at the same place down the page.
    val laterPrefix = StaffPrefix(
        clef = true,
        timeSignature = null,
        widthSpaces = firstPrefix.widthSpaces,
    )

    BoxWithConstraints(modifier = modifier) {
        // A parent offering unbounded width has no edge to fit to; a sensible page width is a
        // better answer than an infinite one.
        val width = if (maxWidth.isFinite) maxWidth else lineSpacing * FallbackWidthSpaces

        val spacing = remember(sequence, width, lineSpacing, minLineSpacing, barsPerSystem) {
            spacingFor(sequence, width, lineSpacing, minLineSpacing, barsPerSystem, firstPrefix)
        }
        val systems = remember(sequence, width, spacing) {
            systemsOf(sequence, width / spacing, firstPrefix, laterPrefix)
        }
        // Sized once for the whole piece: every row is then the same height, whatever happens to
        // be written on it, and the page does not jog as it turns.
        //
        // Widened for whichever secondary notes there are, not just the primary sequence's own:
        // without this a secondary note far enough from its primary in pitch would fall outside the
        // row's own vertical extent and land on the row above or below it rather than beside the
        // note it belongs next to.
        val stepRange = remember(sequence, secondaryWritten) {
            val primary = StaffLayoutBuilder.of(sequence, firstPrefix).stepRange
            sequence.steps.indices.fold(primary) { range, index ->
                val written = secondaryWritten(index) ?: return@fold range
                val placement = TrebleStaff.placementOf(written)
                range.including(extentOf(listOf(placement)))
            }
        }

        val pages = systems.chunked(systemsPerPage.coerceIn(1, systems.size))
        val page = pages.indexOfFirst { rows ->
            highlightIndex != null && rows.any { highlightIndex in it.steps }
        }.coerceAtLeast(0)

        Column(verticalArrangement = Arrangement.spacedBy(SystemGap)) {
            pages[page].forEach { system ->
                MusicalStaffView(
                    sequence = system.sequence,
                    modifier = Modifier.fillMaxWidth(),
                    highlightIndex = highlightIndex
                        ?.takeIf { it in system.steps }
                        ?.minus(system.firstStep),
                    noteColor = { index -> noteColor(system.firstStep + index) },
                    color = color,
                    staffColor = staffColor,
                    highlightColor = highlightColor,
                    lineSpacing = spacing,
                    // Nothing scrolls on a page: the whole row is already on the screen.
                    autoScrollToHighlight = false,
                    naming = naming,
                    octaves = octaves,
                    prefix = if (system.firstStep == 0) firstPrefix else laterPrefix,
                    fillWidth = true,
                    // Only the last row of the last page ends the piece; the others are carried on.
                    closingBar = system.firstStep == systems.last().firstStep,
                    stepRange = stepRange,
                    secondaryNote = { index -> secondaryNote(system.firstStep + index) },
                    secondaryColor = secondaryColor,
                    secondaryWritten = { index -> secondaryWritten(system.firstStep + index) },
                )
            }
        }
    }
}

/**
 * How big to draw the staff so that a row of [barsPerSystem] bars fills [width].
 *
 * Sizing the notes to the page rather than the page to the notes is what printed music does, and it
 * is the only way "about two bars to a row" can hold on a phone and on a tablet at once. The bars
 * measured are the real ones at the start of this piece, so a piece of long notes is given the same
 * treatment as a piece of short ones without either being special-cased.
 */
private fun spacingFor(
    sequence: ToneSequence,
    width: Dp,
    lineSpacing: Dp,
    minLineSpacing: Dp,
    barsPerSystem: Int,
    prefix: StaffPrefix,
): Dp {
    val bars = sequence.bars()
    val wanted = spacesOf(sequence, bars, 0, minOf(barsPerSystem, bars.size), prefix)
    if (wanted <= 0f) return lineSpacing
    return (width / wanted).coerceIn(minOf(minLineSpacing, lineSpacing), lineSpacing)
}

/**
 * [MusicalPageView] with no paging at all - every row of the piece drawn at once - bounded to at
 * most [maxHeight] and scrolling internally past it rather than growing into whatever sits below.
 *
 * A phrase or a scale pattern shown whole this way never pushes the screen's own action buttons
 * off the bottom: short units - most of them - never scroll, since their rows fit well inside
 * [maxHeight] and a scroll container shorter than its content does nothing visible. Only a unit
 * with more bars than a phone screen can show a row of falls back to scrolling, the way a page of
 * sheet music would be turned by hand rather than shrunk to fit.
 */
@Composable
fun BoundedMusicalPage(
    sequence: ToneSequence,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    noteColor: (index: Int) -> Color? = { null },
    naming: NoteNaming = NoteNaming.INTERNATIONAL,
    /** Which number each octave is shown with, wherever a note is named. */
    octaves: OctaveNumbering = OctaveNumbering.SCIENTIFIC,
    color: Color = LocalContentColor.current,
    staffColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    lineSpacing: Dp = DefaultPageLineSpacing,
    minLineSpacing: Dp = MinPageLineSpacing,
    barsPerSystem: Int = DefaultBarsPerSystem,
    maxHeight: Dp = DefaultBoundedPageMaxHeight,
    /** A second note shown beside the one at that index - see [MusicalStaffView]'s own parameter. */
    secondaryNote: (index: Int) -> Note? = { null },
    secondaryColor: Color = staffColor,
    /** The second note as written - see [MusicalStaffView]'s own parameter. */
    secondaryWritten: (index: Int) -> SpelledNote? = { index ->
        secondaryNote(index)?.let { SpelledNote.of(it, sequence.accidentalStyle) }
    },
) {
    Column(modifier = modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
        MusicalPageView(
            sequence = sequence,
            modifier = Modifier.fillMaxWidth(),
            highlightIndex = highlightIndex,
            noteColor = noteColor,
            naming = naming,
            octaves = octaves,
            color = color,
            staffColor = staffColor,
            highlightColor = highlightColor,
            lineSpacing = lineSpacing,
            minLineSpacing = minLineSpacing,
            barsPerSystem = barsPerSystem,
            systemsPerPage = Int.MAX_VALUE,
            secondaryNote = secondaryNote,
            secondaryColor = secondaryColor,
            secondaryWritten = secondaryWritten,
        )
    }
}

/** Room for about four rows before a very long unit starts scrolling instead of growing. */
private val DefaultBoundedPageMaxHeight = 320.dp

/** Big enough to read from where a saxophone puts the player, small enough for two bars of it. */
private val DefaultPageLineSpacing = 13.dp

/** Below this the heads stop being legible at arm's length, so a row loses a bar instead. */
private val MinPageLineSpacing = 7.dp

/** Between one row of the page and the next. */
private val SystemGap = 10.dp

/** What a row aims for; two bars of four quarters is the reading exercise this app is built on. */
private const val DefaultBarsPerSystem = 2

/** As much music as fits comfortably above the controls, before the page has to turn. */
private const val DefaultSystemsPerPage = 4

/** A page's worth of width, for the rare parent that offers no edge to fit to. */
private const val FallbackWidthSpaces = 40f

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun MusicalPageViewPreview() {
    val quarter = NotatedDuration(NoteValue.QUARTER).beats()
    fun note(name: NoteName) = ToneStep(Note(name, 4), quarter)
    val page = ToneSequence(
        steps = listOf(
            note(NoteName.C), note(NoteName.D), note(NoteName.E), note(NoteName.G),
            note(NoteName.G), note(NoteName.E), note(NoteName.D), note(NoteName.C),
            note(NoteName.C), ToneStep.rest(quarter), note(NoteName.D), note(NoteName.E),
            note(NoteName.G), ToneStep.rest(quarter), note(NoteName.E), ToneStep.rest(quarter),
        ),
    )
    SaxTrainerTheme {
        MusicalPageView(sequence = page, highlightIndex = 5)
    }
}
