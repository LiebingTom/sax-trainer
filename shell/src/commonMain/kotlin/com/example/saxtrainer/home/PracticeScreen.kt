package com.example.saxtrainer.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.saxtrainer.core.designsystem.component.FeatureCard
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.learning.TrainerId

/**
 * The whole catalogue of practice tools, grouped by the skill they train.
 *
 * The groups are the landing page's own, moved here unchanged: the headings are what say which tool
 * answers which question, and that is worth a screen of its own now that the landing page leads
 * with what the player reaches for daily.
 *
 * This is also the only place a tool is starred. [favorites] holds the names of the starred ones -
 * the same set the settings keep - so a star drawn here and a card shown on the landing page can
 * never disagree.
 */
@Composable
fun PracticeScreen(
    onEntryClick: (route: String) -> Unit,
    favorites: Set<String>,
    onToggleFavorite: (TrainerId, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    practice: List<HomeGroup> = practiceGroups,
) {
    ScreenScaffold(
        title = "Practice",
        modifier = modifier,
        onBack = onBack,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        practice.forEach { group ->
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            group.entries.forEach { entry ->
                FeatureCard(
                    title = entry.title,
                    description = entry.description,
                    icon = entry.icon,
                    onClick = { onEntryClick(entry.route) },
                    trailing = {
                        FavoriteStar(
                            entry = entry,
                            starred = entry.trainerId?.name in favorites,
                            onToggle = onToggleFavorite,
                        )
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PracticeScreenPreview() {
    SaxTrainerTheme {
        PracticeScreen(
            onEntryClick = {},
            favorites = setOf(TrainerId.METRONOME.name),
            onToggleFavorite = { _, _ -> },
            onBack = {},
        )
    }
}
