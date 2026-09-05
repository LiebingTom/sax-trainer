package com.example.saxtrainer.feature.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.FeedbackColors
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.learning.LessonStep
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.learning.TrainerLaunch
import kotlin.math.roundToInt

/**
 * The lessons of one path, in teaching order.
 *
 * Stateless: everything it draws is in [state], and tapping a lesson simply reports it. What that
 * lesson then runs is decided by whoever handed the curriculum in.
 */
@Composable
fun LessonListScreen(
    state: LearningUiState,
    onLessonClick: (LessonId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        title = state.title,
        onBack = onBack,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        Spacer(Modifier.height(Dimens.spaceSm))
        Text(
            text = state.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.summary,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Dimens.spaceXs))

        state.lessons.forEach { item ->
            LessonCard(item = item, onClick = { onLessonClick(item.id) })
        }
        Spacer(Modifier.height(Dimens.spaceLg))
    }
}

@Composable
private fun LessonCard(item: LessonItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = item.unlocked,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Dimens.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            ) {
                Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val note = statusOf(item)
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Badge(item)
        }
    }
}

/** What has happened to this lesson so far, or what it is going to ask for. */
private fun statusOf(item: LessonItem): String? = when {
    !item.unlocked -> "Finish the level before this one first"
    // A lesson of several exercises is worth saying so before it is started.
    item.isMultiStep && item.attempts == 0 -> "${item.stepCount} exercises"
    item.bestScore != null -> "Best: ${(item.bestScore * 100).roundToInt()}% played correctly"
    item.attempts > 0 -> if (item.attempts == 1) "Tried once" else "Tried ${item.attempts} times"
    else -> null
}

@Composable
private fun Badge(item: LessonItem) {
    val correct = FeedbackColors.correct
    when {
        item.completed -> Text(
            text = "Done",
            style = MaterialTheme.typography.labelLarge,
            color = correct,
        )

        !item.unlocked -> Text(
            text = "Locked",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> Unit
    }
}

@Preview(showBackground = true)
@Composable
private fun LessonListPreview() {
    val launch = object : TrainerLaunch {}
    fun lesson(id: String, title: String, subtitle: String) = Lesson(
        id = LessonId(id),
        title = title,
        subtitle = subtitle,
        steps = listOf(LessonStep(TrainerId.FINGERING, launch)),
    )

    SaxTrainerTheme {
        LessonListScreen(
            state = LearningUiState(
                title = "Fingering",
                description = "Learn the grips a few notes at a time.",
                lessons = listOf(
                    LessonItem(lesson("1", "Level 1", "C, D and E"), true, true, 2, 0.9f),
                    LessonItem(lesson("2", "Level 2", "Adds F, G and A"), false, true, 1, null),
                    LessonItem(lesson("3", "Level 3", "The first octave"), false, false, 0, null),
                ),
            ),
            onLessonClick = {},
            onBack = {},
        )
    }
}
