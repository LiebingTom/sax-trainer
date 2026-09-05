package com.example.saxtrainer.feature.longtones

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.theme.Dimens
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Live magnitude spectrum. Receives bands that are already reduced for display, so it performs no
 * signal processing itself.
 */
@Composable
fun SpectrumView(
    bands: FloatArray,
    maxFrequencyHz: Float,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val background = MaterialTheme.colorScheme.surfaceContainerHighest

    Canvas(
        // Height comes from the caller: on a running exercise it takes the space left over, and
        // anywhere else it falls back to a readable minimum.
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.spectrumHeight)
            .background(background, MaterialTheme.shapes.medium)
            .clearAndSetSemantics {
                contentDescription = "Live frequency spectrum up to ${maxFrequencyHz.roundToInt()} hertz"
            },
    ) {
        drawGrid(gridColor)
        if (bands.isEmpty()) return@Canvas

        val barSpacing = 1.dp.toPx()
        val barWidth = ((size.width - barSpacing * (bands.size - 1)) / bands.size).coerceAtLeast(1f)
        for (index in bands.indices) {
            val normalised = normalizeMagnitude(bands[index])
            val barHeight = size.height * normalised
            if (barHeight <= 0f) continue
            drawRect(
                color = barColor,
                topLeft = Offset(
                    x = index * (barWidth + barSpacing),
                    y = size.height - barHeight,
                ),
                size = Size(barWidth, barHeight),
            )
        }
    }
}

/** Axis captions for the displayed range. */
@Composable
fun SpectrumAxisLabels(maxFrequencyHz: Float, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(top = Dimens.spaceXs)) {
        val style = MaterialTheme.typography.labelSmall
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(text = "0 Hz", style = style, color = color, modifier = Modifier.weight(1f))
        Text(
            text = "${(maxFrequencyHz / 2).roundToInt()} Hz",
            style = style,
            color = color,
            modifier = Modifier.weight(1f),
        )
        Text(text = "${maxFrequencyHz.roundToInt()} Hz", style = style, color = color)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(color: Color) {
    val lines = 3
    for (line in 1..lines) {
        val y = size.height * line / (lines + 1f)
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
    }
}

/**
 * Maps a linear magnitude to 0..1 on a decibel scale, which matches how loudness is perceived and
 * keeps quiet partials visible.
 */
private fun normalizeMagnitude(magnitude: Float): Float {
    if (magnitude <= 0f) return 0f
    val db = 20f * log10(magnitude)
    return ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f)
}

private const val FLOOR_DB = -70f
