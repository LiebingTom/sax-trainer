package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.OctaveNumbering
import com.example.saxtrainer.core.music.ScaleDegree
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep

/** How a piece of music is put in front of the player: notes on a staff, or numbers in the key. */
enum class NotationStyle { STAFF, NUMBERS }

/**
 * A piece of music in numbered notation: the key, then each note as its degree in it, bar by bar -
 * see [NumberedLayout] for how lengths are written. Octaves are dots: one above a number for the
 * octave above the root's, one below for the octave beneath.
 *
 * Whole bars go on a row, as many as fit, the way [MusicalPageView] lays out a staff.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NumberedPageView(
    sequence: ToneSequence,
    modifier: Modifier = Modifier,
    /** The key the numbers count in, said above them: "G major", "A blues". */
    keyLabel: String? = null,
    highlightIndex: Int? = null,
    noteColor: (index: Int) -> Color? = { null },
    color: Color = LocalContentColor.current,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    barLineColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val bars = remember(sequence) { NumberedLayout.of(sequence) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(KeyGap)) {
        keyLabel?.let {
            Text(text = it, style = MaterialTheme.typography.titleMedium, color = barLineColor)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            bars.forEachIndexed { index, bar ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BarLine(barLineColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(BeatGap)) {
                        bar.tokens.groupBy { it.beat }.values.forEach { beat ->
                            // One beat's tokens touch, so their underlines run on as one beam.
                            Row {
                                beat.forEach { token ->
                                    val tint = when {
                                        token.stepIndex == highlightIndex -> highlightColor
                                        else -> noteColor(token.stepIndex) ?: color
                                    }
                                    NumberedSymbolView(token, tint)
                                }
                            }
                        }
                    }
                    if (index == bars.lastIndex) BarLine(barLineColor)
                }
            }
        }
    }
}

/** A bar line: the space a bar begins with, drawn as a thin rule. */
@Composable
private fun BarLine(color: Color) {
    Text(
        text = "|",
        fontSize = SymbolSize,
        color = color,
        modifier = Modifier.padding(horizontal = BeatGap),
    )
}

/** One number, dash or rest, with its underlines, its dot and its octave dots. */
@Composable
private fun NumberedSymbolView(token: NumberedToken, color: Color) {
    val (text, octave) = when (val symbol = token.symbol) {
        is NumberedSymbol.Number -> (symbol.degree?.label ?: "?") to (symbol.degree?.octave ?: 0)
        NumberedSymbol.Dash -> "–" to 0
        NumberedSymbol.Rest -> "0" to 0
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .widthIn(min = SymbolWidth)
            .drawBehind {
                val stroke = LineWidth.toPx()
                val bottom = size.height - BelowRoom.toPx()
                repeat(token.underlines) { level ->
                    val y = bottom + LineGap.toPx() * (level + 1)
                    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
                }
                val dot = DotRadius.toPx()
                val step = DotGap.toPx()
                repeat(maxOf(octave, 0)) { level ->
                    drawCircle(color, dot, Offset(size.width / 2, AboveRoom.toPx() - step * (level + 1) + dot))
                }
                // Below the underlines, so a low eighth reads as low and short at once.
                val belowStart = bottom + LineGap.toPx() * (token.underlines + 1)
                repeat(maxOf(-octave, 0)) { level ->
                    drawCircle(color, dot, Offset(size.width / 2, belowStart + step * level + dot))
                }
            }
            .padding(top = AboveRoom, bottom = BelowRoom, start = SymbolPadding, end = SymbolPadding),
    ) {
        Text(
            text = if (token.dotted) "$text·" else text,
            fontSize = SymbolSize,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

/**
 * [NumberedPageView] bounded to at most [maxHeight], scrolling inside past it - the numbered twin of
 * [BoundedMusicalPage], for a unit shown whole above a screen's own buttons.
 */
@Composable
fun BoundedNumberedPage(
    sequence: ToneSequence,
    modifier: Modifier = Modifier,
    keyLabel: String? = null,
    highlightIndex: Int? = null,
    noteColor: (index: Int) -> Color? = { null },
    color: Color = LocalContentColor.current,
    barLineColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxHeight: Dp = BoundedNumberedMaxHeight,
) {
    Column(modifier = modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
        NumberedPageView(
            sequence = sequence,
            modifier = Modifier.fillMaxWidth(),
            keyLabel = keyLabel,
            highlightIndex = highlightIndex,
            noteColor = noteColor,
            color = color,
            barLineColor = barLineColor,
        )
    }
}

/**
 * A unit shown whole in whichever [style] the player chose: the one call a screen makes, so it never
 * has to know how either is drawn. [keyLabel] is only shown with numbers - a staff says its own key.
 */
@Composable
fun BoundedNotationPage(
    sequence: ToneSequence,
    style: NotationStyle,
    modifier: Modifier = Modifier,
    keyLabel: String? = null,
    naming: NoteNaming = NoteNaming.INTERNATIONAL,
    octaves: OctaveNumbering = OctaveNumbering.SCIENTIFIC,
    color: Color = LocalContentColor.current,
    staffColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    when (style) {
        NotationStyle.STAFF -> BoundedMusicalPage(
            sequence = sequence,
            modifier = modifier,
            naming = naming,
            octaves = octaves,
            color = color,
            staffColor = staffColor,
        )
        NotationStyle.NUMBERS -> BoundedNumberedPage(
            sequence = sequence,
            modifier = modifier,
            keyLabel = keyLabel,
            color = color,
            barLineColor = staffColor,
        )
    }
}

private val SymbolSize = 30.sp
private val SymbolWidth = 22.dp
private val SymbolPadding = 2.dp
private val BeatGap = 8.dp
private val RowGap = 12.dp
private val KeyGap = 8.dp

/** Room above a number for two octave dots, and below it for two underlines and two dots. */
private val AboveRoom = 12.dp
private val BelowRoom = 18.dp
private val LineGap = 4.dp
private val LineWidth = 1.5.dp
private val DotRadius = 2.dp
private val DotGap = 5.dp

private val BoundedNumberedMaxHeight = 560.dp

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun NumberedPageViewPreview() {
    fun step(name: NoteName, beats: Double, degree: ScaleDegree) = ToneStep(Note(name, 4), Beats(beats), degree = degree)
    val sequence = ToneSequence(
        steps = listOf(
            step(NoteName.A, 0.5, ScaleDegree(1)),
            step(NoteName.C, 0.5, ScaleDegree(3, -1)),
            step(NoteName.D, 1.0, ScaleDegree(4)),
            ToneStep.rest(Beats(2.0)),
            step(NoteName.A, 1.5, ScaleDegree(1, octave = 1)),
            step(NoteName.G, 0.5, ScaleDegree(7, -1)),
            step(NoteName.E, 2.0, ScaleDegree(5)),
            step(NoteName.A, 4.0, ScaleDegree(1)),
        ),
    )
    SaxTrainerTheme {
        NumberedPageView(sequence = sequence, keyLabel = "A blues", modifier = Modifier.padding(16.dp))
    }
}
