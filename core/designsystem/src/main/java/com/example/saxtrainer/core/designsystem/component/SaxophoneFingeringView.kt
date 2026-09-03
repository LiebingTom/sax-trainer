package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.fingering.Fingering
import com.example.saxtrainer.core.fingering.SaxKey
import com.example.saxtrainer.core.fingering.TenorSaxophoneFingerings
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName

/**
 * Draws a saxophone fingering: pressed keys filled, open keys outlined.
 *
 * Everything is generated from [SaxophoneKeyLayout] and the [Fingering], so any note a chart knows
 * can be shown without an image per note. The view holds no fingering knowledge of its own - it is
 * given which keys are down - and no feature knowledge, so a trainer, a tuner or a reference chart
 * can all use it.
 */
@Composable
fun SaxophoneFingeringView(
    fingering: Fingering,
    modifier: Modifier = Modifier,
    width: Dp = DefaultWidth,
    pressedColor: Color = MaterialTheme.colorScheme.onSurface,
    openColor: Color = MaterialTheme.colorScheme.outline,
    bodyColor: Color = MaterialTheme.colorScheme.outlineVariant,
    contentDescription: String? = null,
) {
    Canvas(
        modifier = modifier
            .size(width = width, height = width * SaxophoneDiagramMetrics.HEIGHT_RATIO)
            .then(
                if (contentDescription != null) {
                    Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
    ) {
        drawBody(bodyColor)

        val strokeWidth = SaxophoneDiagramMetrics.strokeWidth(size.width)
        SaxophoneDiagramMetrics.resolve(size.width, size.height).forEach { resolved ->
            drawKey(
                geometry = resolved.geometry,
                pressed = fingering.isPressed(resolved.key),
                strokeWidth = strokeWidth,
                color = if (fingering.isPressed(resolved.key)) pressedColor else openColor,
            )
        }
    }
}

/**
 * The line the main keys sit on, hinting at the body of the instrument, and a break between the two
 * hands so the upper and lower halves read as left and right.
 */
private fun DrawScope.drawBody(color: Color) {
    SaxophoneDiagramMetrics.bodyGuides(size.width, size.height).forEach { guide ->
        drawLine(
            color = color,
            start = Offset(guide.startX, guide.startY),
            end = Offset(guide.endX, guide.endY),
            strokeWidth = SaxophoneDiagramMetrics.bodyStrokeWidth(size.width),
            pathEffect = if (guide.dashed) {
                PathEffect.dashPathEffect(floatArrayOf(size.width * 0.04f, size.width * 0.04f))
            } else {
                null
            },
        )
    }
}

private fun DrawScope.drawKey(
    geometry: KeyGeometry,
    pressed: Boolean,
    strokeWidth: Float,
    color: Color,
) {
    when (geometry) {
        is KeyGeometry.Ellipse -> if (pressed) {
            drawCircle(
                color = color,
                radius = geometry.radius,
                center = Offset(geometry.centreX, geometry.centreY),
            )
        } else {
            drawCircle(
                color = color,
                radius = geometry.radius,
                center = Offset(geometry.centreX, geometry.centreY),
                style = Stroke(width = strokeWidth),
            )
        }

        is KeyGeometry.Bar -> {
            val topLeft = Offset(geometry.left, geometry.top)
            val rectSize = Size(geometry.width, geometry.height)
            val corner = CornerRadius(geometry.cornerRadius)
            if (pressed) {
                drawRoundRect(color = color, topLeft = topLeft, size = rectSize, cornerRadius = corner)
            } else {
                drawRoundRect(
                    color = color,
                    topLeft = topLeft,
                    size = rectSize,
                    cornerRadius = corner,
                    style = Stroke(width = strokeWidth),
                )
            }
        }
    }
}

private val DefaultWidth = 96.dp

@Preview(showBackground = true)
@Composable
private fun SaxophoneFingeringViewPreview() {
    SaxTrainerTheme {
        SaxophoneFingeringView(
            fingering = TenorSaxophoneFingerings.fingeringFor(Note(NoteName.G, 4))!!,
            contentDescription = "Fingering for G",
        )
    }
}

/** Every key at once, to check that nothing in the layout overlaps or falls off the panel. */
@Preview(showBackground = true)
@Composable
private fun SaxophoneFingeringViewAllKeysPreview() {
    SaxTrainerTheme {
        SaxophoneFingeringView(
            fingering = Fingering(SaxKey.entries.toSet()),
            width = 140.dp,
        )
    }
}

/**
 * The whole chart: every note the database knows, standard fingerings first and then the
 * alternates, so the diagram can be read against a printed chart at a glance.
 */
@Preview(showBackground = true, heightDp = 2400, widthDp = 420)
@Composable
private fun SaxophoneFingeringChartPreview() {
    SaxTrainerTheme {
        LazyVerticalGrid(columns = GridCells.Fixed(4)) {
            TenorSaxophoneFingerings.supportedNotes.forEach { note ->
                val fingerings = TenorSaxophoneFingerings.fingeringsFor(note)!!
                fingerings.all.forEachIndexed { index, fingering ->
                    item {
                        Column(
                            modifier = Modifier.padding(Dimens.spaceXs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                                Text(
                                    text = note.label,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                if (index > 0) {
                                    Text(
                                        text = "alt $index",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            SaxophoneFingeringView(fingering = fingering, width = 72.dp)
                        }
                    }
                }
            }
        }
    }
}
