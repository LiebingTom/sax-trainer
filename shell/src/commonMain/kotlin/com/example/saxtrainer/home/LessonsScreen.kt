package com.example.saxtrainer.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.saxtrainer.core.designsystem.component.FeatureCard
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme

/**
 * The curriculum's units, one card each, exactly as the landing page used to list them.
 *
 * No stars: a unit is a topic rather than a tool, and only a practice tool can be starred.
 */
@Composable
fun LessonsScreen(
    onEntryClick: (route: String) -> Unit,
    units: List<HomeEntry>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        title = "Lessons",
        modifier = modifier,
        onBack = onBack,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        units.forEach { entry ->
            FeatureCard(
                title = entry.title,
                description = entry.description,
                icon = entry.icon,
                onClick = { onEntryClick(entry.route) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LessonsScreenPreview() {
    SaxTrainerTheme {
        LessonsScreen(onEntryClick = {}, units = emptyList(), onBack = {})
    }
}
