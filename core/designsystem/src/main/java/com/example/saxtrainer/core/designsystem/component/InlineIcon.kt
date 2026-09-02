package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

private val InlineIconSize = 20.dp

/** Draws an [IconSource] inline (no container), e.g. inside a button or a list row. */
@Composable
fun InlineIcon(
    icon: IconSource,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val iconModifier = modifier.size(InlineIconSize)
    when (icon) {
        is IconSource.Vector -> Icon(
            imageVector = icon.image,
            contentDescription = contentDescription,
            tint = LocalContentColor.current,
            modifier = iconModifier,
        )

        is IconSource.Drawable -> Icon(
            painter = painterResource(icon.id),
            contentDescription = contentDescription,
            tint = LocalContentColor.current,
            modifier = iconModifier,
        )
    }
}
