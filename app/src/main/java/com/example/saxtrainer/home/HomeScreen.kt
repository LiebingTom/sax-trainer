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
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme

/**
 * Landing page. Deliberately free of any feature implementation detail: it renders a list of
 * [HomeEntry] and reports the route that was tapped.
 *
 * No permission is requested here - the microphone is only requested inside the tool that needs it.
 */
@Composable
fun HomeScreen(
    onEntryClick: (route: String) -> Unit,
    modifier: Modifier = Modifier,
    learning: List<HomeEntry> = learningEntries,
    practice: List<HomeGroup> = practiceGroups,
    app: List<HomeEntry> = appEntries,
) {
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
            if (learning.isNotEmpty()) {
                item { SectionHeader("Learning") }
                items(learning, key = { it.id }) { entry ->
                    FeatureCard(
                        title = entry.title,
                        description = entry.description,
                        icon = entry.icon,
                        onClick = { onEntryClick(entry.route) },
                    )
                }
            }
            item { SectionHeader("Practice") }
            practice.forEach { group ->
                // A heading per group: the tools differ by the question they answer, and that is
                // worth saying before the descriptions have to.
                item(key = "group-${group.title}") {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(group.entries, key = { it.id }) { entry ->
                    FeatureCard(
                        title = entry.title,
                        description = entry.description,
                        icon = entry.icon,
                        onClick = { onEntryClick(entry.route) },
                    )
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

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    SaxTrainerTheme {
        HomeScreen(onEntryClick = {})
    }
}
