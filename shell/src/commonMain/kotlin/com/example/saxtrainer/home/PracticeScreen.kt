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

/**
 * The whole catalogue of practice tools, grouped by the skill they train.
 *
 * The groups are the landing page's own, moved here unchanged: the headings are what say which tool
 * answers which question, and that is worth a screen of its own now that the landing page leads
 * with what the player reaches for daily.
 *
 * Nothing is starred from here: a tool is starred from its own title bar, the one place that is
 * offered.
 */
@Composable
fun PracticeScreen(
    onEntryClick: (route: String) -> Unit,
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
            onBack = {},
        )
    }
}
