package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.saxtrainer.core.designsystem.icon.DropDown
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.theme.Dimens

/**
 * A closed field: a label, its current value, and a tap that opens whatever picks the value - a
 * dialog, most often. The same shape wherever a setting is a choice too open-ended for a row of
 * options to sit on screen at once, so a setup screen reads as one family of fields rather than a
 * different widget for every kind of choice.
 */
@Composable
fun FieldRow(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = label, onClick = onClick)
                .padding(Dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = value, style = MaterialTheme.typography.titleMedium)
            }
            InlineIcon(IconSource.Vector(SaxTrainerIcons.DropDown), contentDescription = null)
        }
    }
}
