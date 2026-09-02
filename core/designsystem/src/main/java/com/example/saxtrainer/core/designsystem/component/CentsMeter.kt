package com.example.saxtrainer.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.theme.Dimens
import kotlin.math.roundToInt

/**
 * Centered tuning meter: 0 in the middle, flat to the left, sharp to the right.
 *
 * A null [cents] is the inactive state - the scale stays visible but no marker is drawn, so the
 * player is never shown a stale or invented reading. [inTune] and [offTarget] only affect colour,
 * because deciding what counts as in tune or as a different note is the caller's business.
 */
@Composable
fun CentsMeter(
    cents: Float?,
    modifier: Modifier = Modifier,
    range: Float = DEFAULT_RANGE_CENTS,
    inTune: Boolean = false,
    offTarget: Boolean = false,
    contentDescription: String? = null,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val tickColor = MaterialTheme.colorScheme.outlineVariant
    val centreColor = MaterialTheme.colorScheme.outline
    val markerColor = when {
        cents == null || offTarget -> MaterialTheme.colorScheme.outline
        inTune -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }

    // Smooths the marker between the ~25 updates per second the analysis produces.
    val position by animateFloatAsState(
        targetValue = (cents ?: 0f).coerceIn(-range, range) / range,
        label = "centsPosition",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(MeterHeight)
            .background(trackColor, MaterialTheme.shapes.small)
            .then(
                if (contentDescription != null) {
                    Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
    ) {
        val centreX = size.width / 2f
        drawTicks(tickColor, centreColor)

        if (cents == null) return@Canvas

        val markerWidth = MarkerWidth.toPx()
        val x = centreX + position * (size.width / 2f - markerWidth)
        drawRect(
            color = markerColor,
            topLeft = Offset(x - markerWidth / 2f, 0f),
            size = Size(markerWidth, size.height),
        )
    }
}

/** Scale captions for a [CentsMeter] of the same range. */
@Composable
fun CentsMeterLabels(
    modifier: Modifier = Modifier,
    range: Float = DEFAULT_RANGE_CENTS,
) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier.fillMaxWidth().padding(top = Dimens.spaceXs)) {
        Text(text = "-${range.roundToInt()} ct", style = style, color = color)
        Text(
            text = "0",
            style = style,
            color = color,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(text = "+${range.roundToInt()} ct", style = style, color = color)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTicks(
    tickColor: Color,
    centreColor: Color,
) {
    val centreX = size.width / 2f
    // Quarter marks either side, plus a taller line in the middle.
    for (fraction in listOf(-0.5f, 0.5f)) {
        val x = centreX + fraction * size.width / 2f
        drawLine(
            color = tickColor,
            start = Offset(x, size.height * 0.25f),
            end = Offset(x, size.height * 0.75f),
            strokeWidth = 2f,
        )
    }
    drawLine(
        color = centreColor,
        start = Offset(centreX, 0f),
        end = Offset(centreX, size.height),
        strokeWidth = 3f,
    )
}

private val MeterHeight = 36.dp
private val MarkerWidth = 6.dp
private const val DEFAULT_RANGE_CENTS = 50f
