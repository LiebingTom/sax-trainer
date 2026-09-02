package com.example.saxtrainer.core.designsystem.component

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.res.painterResource
import com.example.saxtrainer.core.designsystem.theme.Dimens

/**
 * Decouples a feature from how its icon is drawn. A feature supplies an [IconSource]; only
 * [FeatureIcon] knows about sizing, container shape and tint. Adding a hand-drawn icon later means
 * shipping a vector drawable and switching to [IconSource.Drawable] at the single definition site,
 * without touching any card or screen layout.
 */
sealed interface IconSource {
    data class Vector(val image: ImageVector) : IconSource

    data class Drawable(@param:DrawableRes val id: Int) : IconSource
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

            is IconSource.Drawable -> Icon(
                painter = painterResource(icon.id),
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = iconModifier,
            )
        }
    }
}
