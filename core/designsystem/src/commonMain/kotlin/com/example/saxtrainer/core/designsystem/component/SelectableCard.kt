package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.example.saxtrainer.core.designsystem.theme.Dimens

/**
 * Single-choice settings row, drawn as a card rather than a plain row - the same shape a lesson is
 * drawn in, so a list of things to pick between reads as one family with a list of things to open.
 *
 * [SelectableRow] is the flat version of this; use that one where a whole screen of them would make
 * a wall of cards feel heavy, and this one where the list sits inside its own window and can afford
 * the weight - a phrase or a pattern to choose between, say.
 */
@Composable
fun SelectableCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    /** Drawn at the far end, outside the selectable area - a preview button, say. */
    trailing: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                    .padding(Dimens.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                RadioButton(selected = selected, onClick = null)
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    if (supportingText != null) {
                        Text(
                            text = supportingText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (trailing != null) {
                Row(modifier = Modifier.padding(end = Dimens.spaceSm)) { trailing() }
            }
        }
    }
}
