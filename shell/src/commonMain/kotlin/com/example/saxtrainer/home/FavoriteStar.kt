package com.example.saxtrainer.home

import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.core.designsystem.component.InlineIcon
import com.example.saxtrainer.core.designsystem.icon.Favorite
import com.example.saxtrainer.core.designsystem.icon.FavoriteOff
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.learning.TrainerId

/**
 * The star on a practice card.
 *
 * Filled once the tool is starred and hollow while it is not, because colour alone does not say
 * which way round a toggle is. It carries its own description rather than joining the card's: the
 * card says where it goes, the star says what it does.
 */
@Composable
fun FavoriteStar(
    entry: HomeEntry,
    starred: Boolean,
    onToggle: (TrainerId, Boolean) -> Unit,
) {
    val trainerId = entry.trainerId ?: return
    IconToggleButton(
        checked = starred,
        onCheckedChange = { wanted -> onToggle(trainerId, wanted) },
    ) {
        InlineIcon(
            icon = IconSource.Vector(
                if (starred) SaxTrainerIcons.Favorite else SaxTrainerIcons.FavoriteOff,
            ),
            contentDescription = if (starred) {
                "Remove ${entry.title} from favorites"
            } else {
                "Favorite ${entry.title}"
            },
        )
    }
}

