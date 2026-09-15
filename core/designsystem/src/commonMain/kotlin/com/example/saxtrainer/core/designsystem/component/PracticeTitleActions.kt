package com.example.saxtrainer.core.designsystem.component

import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import com.example.saxtrainer.core.designsystem.icon.AddToLessons
import com.example.saxtrainer.core.designsystem.icon.Favorite
import com.example.saxtrainer.core.designsystem.icon.FavoriteOff
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons

/**
 * What a practice offers about itself, at the end of its title bar: the star that keeps it on the
 * landing page, and the ribbon that keeps its current setup in My Lessons.
 *
 * The one place either is offered, so a player looking to star a tool or save a setup always looks
 * in the same corner, whichever practice they are in. Each is left out where it has no business:
 * [onToggleFavorite] and [onAddToLessons] are null inside a lesson, which is running somebody's
 * exercise rather than one of the player's own tools; editing a lesson brings the ribbon back but
 * never the star. Practices leave both out while an exercise is under way.
 *
 * The star is filled once starred and hollow while not, because colour alone does not say which way
 * round a toggle is. Both descriptions name the practice as well as the action.
 */
@Composable
fun PracticeTitleActions(
    name: String,
    favorite: Boolean,
    onToggleFavorite: ((Boolean) -> Unit)?,
    onAddToLessons: (() -> Unit)?,
) {
    if (onToggleFavorite != null) {
        IconToggleButton(checked = favorite, onCheckedChange = onToggleFavorite) {
            InlineIcon(
                icon = IconSource.Vector(
                    if (favorite) SaxTrainerIcons.Favorite else SaxTrainerIcons.FavoriteOff,
                ),
                contentDescription = if (favorite) {
                    "Remove $name from favorites"
                } else {
                    "Favorite $name"
                },
            )
        }
    }
    if (onAddToLessons != null) {
        IconButton(onClick = onAddToLessons) {
            InlineIcon(
                icon = IconSource.Vector(SaxTrainerIcons.AddToLessons),
                contentDescription = "Add $name to My Lessons",
            )
        }
    }
}
