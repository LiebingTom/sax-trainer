package com.example.saxtrainer.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.saxtrainer.core.designsystem.component.FeatureCard
import com.example.saxtrainer.core.designsystem.component.FeatureTile
import com.example.saxtrainer.core.designsystem.component.TileGrid
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme

/**
 * Landing page. Deliberately free of any feature implementation detail: it renders a list of
 * [HomeEntry] and reports the route that was tapped.
 *
 * It leads with what the player reaches for daily and then points at the rest: the starred tools,
 * then the catalogue, the curriculum and the settings. [favorites] holds the names of the starred
 * tools and is resolved against the catalogue here, so the cards can only ever be tools that exist.
 *
 * No permission is requested here - the microphone is only requested inside the tool that needs it.
 */
@Composable
fun HomeScreen(
    onEntryClick: (route: String) -> Unit,
    favorites: Set<String>,
    modifier: Modifier = Modifier,
    practice: List<HomeGroup> = practiceGroups,
    app: List<HomeEntry> = appEntries,
) {
    val starred = favoriteEntriesOf(favorites, practice)

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = Dimens.screenPadding,
                end = Dimens.screenPadding,
                top = innerPadding.calculateTopPadding() + Dimens.spaceLg,
                bottom = innerPadding.calculateBottomPadding() + Dimens.spaceLg,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            item { HomeHeader() }
            item { SectionHeader("Favorites") }
            if (starred.isEmpty()) {
                // One line rather than an empty card: nothing starred is an ordinary state on a
                // fresh install, and it should cost a sentence, not a screenful.
                item { FavoritesHint() }
            } else {
                // Squares, icon only: these are the tools the player reaches for daily and knows
                // by sight, so a row of them says more at a glance than a list of cards.
                item {
                    TileGrid(items = starred) { entry, tileModifier ->
                        FeatureTile(
                            icon = entry.icon,
                            contentDescription = entry.title,
                            onClick = { onEntryClick(entry.route) },
                            modifier = tileModifier,
                        )
                    }
                }
            }
            item { SectionHeader("App") }
            items(app, key = { it.id }) { entry ->
                FeatureCard(
                    title = entry.title,
                    description = entry.description,
                    icon = entry.icon,
                    onClick = { onEntryClick(entry.route) },
                )
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
        Text(text = "Sax Trainer", style = MaterialTheme.typography.displaySmall)
        Text(
            text = "Daily practice tools for saxophone",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FavoritesHint() {
    Text(
        text = "Tap the star at the top of a practice to keep it here.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    SaxTrainerTheme {
        HomeScreen(
            onEntryClick = {},
            favorites = emptySet(),
        )
    }
}
