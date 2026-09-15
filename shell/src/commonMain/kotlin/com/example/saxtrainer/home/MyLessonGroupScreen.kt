package com.example.saxtrainer.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.saxtrainer.core.designsystem.component.ActionIconButton
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.icon.Edit
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.core.settings.UserLessonBook
import com.example.saxtrainer.core.settings.UserLessonGroup
import com.example.saxtrainer.core.settings.minusLesson
import com.example.saxtrainer.core.settings.movedLesson
import com.example.saxtrainer.core.settings.renamedLesson
import com.example.saxtrainer.feature.learning.LessonCard
import com.example.saxtrainer.learning.lessonIdOf
import com.example.saxtrainer.learning.trainerNameOf

/**
 * The practices saved in one group of My Lessons, in the order the player put them in - the way a
 * unit of Lessons lists its lessons.
 *
 * Tapping one plays it; the pen on it opens its setup to be adjusted first. The pen in the title bar
 * switches the list to managing it: move, rename, delete. Nothing is played while managing.
 */
@Composable
fun MyLessonGroupScreen(
    book: UserLessonBook,
    group: UserLessonGroup,
    onWrite: (UserLessonBook) -> Unit,
    onStart: (LessonId) -> Unit,
    onEdit: (LessonId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var managing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Pair<String, String>?>(null) }
    val saved = book.lessonsOf(group.id)

    ScreenScaffold(
        title = group.name,
        onBack = onBack,
        modifier = modifier,
        titleActions = { ManageToggle(managing, group.name) { managing = it } },
        header = ScreenHeader.Description(
            if (managing) {
                "Move, rename and remove. Nothing here is started while you are managing."
            } else {
                "Tap to practise. Edit opens the setup it was saved with."
            },
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        Spacer(Modifier.height(Dimens.spaceSm))

        if (saved.isEmpty()) {
            Text(
                text = EMPTY_GROUP_HINT,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        saved.forEachIndexed { position, lesson ->
            // A trainer this version does not know about is drawn and can be deleted, but cannot be
            // run: the practice is still the player's, it is simply not one this build can offer.
            val trainer = trainerNameOf(lesson.trainerId)
            val id = lessonIdOf(lesson.id)

            LessonCard(
                title = lesson.name,
                subtitle = trainer ?: "Saved by a newer version",
                // The practice it was saved from, with that practice's own icon.
                icon = TrainerId.entries.firstOrNull { it.name == lesson.trainerId }
                    ?.let(::practiceIconOf),
                onClick = { if (!managing && trainer != null) onStart(id) },
                enabled = !managing && trainer != null,
                trailing = {
                    if (managing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ManageButtons(
                                name = lesson.name,
                                canMoveUp = position > 0,
                                canMoveDown = position < saved.lastIndex,
                                onMove = { by -> onWrite(book.movedLesson(lesson.id, by)) },
                                onRename = { renaming = lesson.id to lesson.name },
                                onDelete = { onWrite(book.minusLesson(lesson.id)) },
                            )
                        }
                    } else if (trainer != null) {
                        ActionIconButton(
                            icon = IconSource.Vector(SaxTrainerIcons.Edit),
                            contentDescription = "Edit ${lesson.name}",
                            onClick = { onEdit(id) },
                        )
                    }
                },
            )
        }
        Spacer(Modifier.height(Dimens.spaceLg))
    }

    renaming?.let { (id, name) ->
        NameDialog(
            title = "Rename lesson",
            initial = name,
            onConfirm = { newName ->
                onWrite(book.renamedLesson(id, newName))
                renaming = null
            },
            onCancel = { renaming = null },
        )
    }
}

private const val EMPTY_GROUP_HINT =
    "Nothing saved here yet. Set a practice up the way you like it, then tap the ribbon at the " +
        "top of it and choose this group."
