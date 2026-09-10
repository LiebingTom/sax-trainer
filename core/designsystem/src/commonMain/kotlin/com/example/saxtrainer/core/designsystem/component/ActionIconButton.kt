package com.example.saxtrainer.core.designsystem.component

import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A control drawn as an icon alone: move, rename, delete, edit.
 *
 * It keeps a colour of its own rather than taking the one around it. A card that cannot be started
 * - a saved practice while the list is being managed, say - dims everything on it, and would
 * otherwise dim the very controls on it that still work. Full strength while it can be tapped;
 * dimmed only when it cannot.
 *
 * [contentDescription] is required: with no word on screen it is all a screen reader has, and it
 * should name the item as well as the action, or a column of identical icons reads out the same.
 */
@Composable
fun ActionIconButton(
    icon: IconSource,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha),
        ),
    ) {
        InlineIcon(icon = icon, contentDescription = contentDescription)
    }
}

/** Material's own strength for a control that cannot be used right now. */
private const val DisabledAlpha = 0.38f
