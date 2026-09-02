package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.numericMedium

/** Label + monospaced value, used for BPM, elapsed time, RMS and peak read-outs. */
@Composable
fun MetricDisplay(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
) {
    val spoken = buildString {
        append(label)
        append(": ")
        append(value)
        if (unit != null) append(" $unit")
    }
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (unit == null) value else "$value $unit",
            style = numericMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
