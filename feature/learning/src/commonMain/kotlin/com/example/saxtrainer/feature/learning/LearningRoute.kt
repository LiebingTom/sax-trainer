package com.example.saxtrainer.feature.learning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.learning.LearningUnit
import com.example.saxtrainer.core.learning.LessonProgressRepository

/**
 * Stateful entry point of one learning unit.
 *
 * There is no ViewModel: the screen holds no state beyond the progress it is given, and the only
 * decision it makes - which lesson was tapped - is reported straight out. Which trainer that lesson
 * starts is resolved by the caller, which is the only place that can see both.
 */
@Composable
fun LearningRoute(
    unit: LearningUnit,
    progressRepository: LessonProgressRepository,
    isUnlocked: (Lesson) -> Boolean,
    onStartLesson: (LessonId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens a lesson's configuration on the trainer's own setup screen, without running it. */
    onEditLesson: ((LessonId) -> Unit)? = null,
    /** The icon of the practice behind a lesson; see [LessonListScreen]. */
    iconOf: (Lesson) -> IconSource? = { null },
) {
    val progress by progressRepository.progress.collectAsStateWithLifecycle()

    LessonListScreen(
        state = learningUiStateOf(unit = unit, progress = progress, isUnlocked = isUnlocked),
        onLessonClick = onStartLesson,
        onEditLesson = onEditLesson,
        iconOf = iconOf,
        onBack = onBack,
        modifier = modifier,
    )
}
