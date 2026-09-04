package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.saxtrainer.core.designsystem.theme.Dimens

/**
 * Decouples a feature from how its icon is drawn. A feature supplies an [IconSource]; only
 * [FeatureIcon] knows about sizing, container shape and tint, so adding or replacing an icon never
 * touches a card or a screen layout.
 *
 * Still a sealed interface with a single case rather than a plain [ImageVector]: what an icon *is*
 * stays the caller's word, so a second kind - an animated mark, a photograph of a real fingering -
 * becomes a case here rather than a change at every call site.
 */
sealed interface IconSource {
    data class Vector(val image: ImageVector) : IconSource
}

/** Icon inside its rounded container. The only place icon geometry and tint are defined. */
@Composable
fun FeatureIcon(
    icon: IconSource,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = modifier
            .size(Dimens.iconContainerSize)
            .background(containerColor, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        val iconModifier = Modifier.size(Dimens.iconSize)
        when (icon) {
            is IconSource.Vector -> Icon(
                imageVector = icon.image,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = iconModifier,
            )
        }
    }
}
