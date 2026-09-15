package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.saxtrainer.core.designsystem.theme.Dimens
import kotlin.math.roundToInt

/**
 * A setting chosen from a short run of values: a name, what the value means, the value itself, and
 * a slider that stops on each of [options] and nowhere between.
 *
 * The slider moves over positions in the list rather than over the values, so the stops are evenly
 * spaced whatever they are - 5, 10, 15 questions, or "until correct" followed by 1 to 16 beats.
 *
 * A [value] that is not one of [options] - a lesson written with a length the screen does not
 * offer - still reads as itself, with the thumb on the nearest stop above it.
 */
@Composable
fun <T : Comparable<T>> StepSlider(
    title: String,
    value: T,
    options: List<T>,
    valueLabel: (T) -> String,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    if (options.isEmpty()) return
    val position = options.indexOfFirst { it >= value }.let { if (it < 0) options.lastIndex else it }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = valueLabel(value),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = position.toFloat(),
            onValueChange = { moved ->
                // Reported once per stop rather than on every frame of a drag.
                val chosen = options[moved.roundToInt().coerceIn(options.indices)]
                if (chosen != value) onValueChange(chosen)
            },
            valueRange = 0f..options.lastIndex.coerceAtLeast(1).toFloat(),
            steps = (options.lastIndex - 1).coerceAtLeast(0),
            enabled = options.size > 1,
        )
    }
}
