package com.example.saxtrainer.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.icon.Delete
import com.example.saxtrainer.core.designsystem.icon.Edit
import com.example.saxtrainer.core.designsystem.icon.MoveDown
import com.example.saxtrainer.core.designsystem.icon.MoveUp
import com.example.saxtrainer.core.designsystem.icon.Rename
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.settings.UserLesson
import com.example.saxtrainer.core.settings.UserLessonBook
import com.example.saxtrainer.core.settings.UserLessonGroup
import com.example.saxtrainer.core.settings.minusGroup
import com.example.saxtrainer.core.settings.minusLesson
import com.example.saxtrainer.core.settings.movedGroup
import com.example.saxtrainer.core.settings.movedLesson
import com.example.saxtrainer.core.settings.plusGroup
import com.example.saxtrainer.core.settings.renamedGroup
import com.example.saxtrainer.feature.learning.LessonCard
import com.example.saxtrainer.learning.lessonIdOf
import com.example.saxtrainer.learning.trainerNameOf

/**
 * The practices the player saved, in the groups they put them in.
 *
 * One screen rather than a list of groups leading to a screen each: groups are one level deep and
 * there are a handful of them, so a heading per group says everything a second screen would, and
 * managing them is one mode of one list rather than two.
 *
 * Drawn from the book rather than from the curriculum's units, so a group the player has made but
 * not filled yet is still here to be renamed, filled or thrown away.
 */
@Composable
fun MyLessonsScreen(
    book: UserLessonBook,
    onWrite: (UserLessonBook) -> Unit,
    onStart: (LessonId) -> Unit,
    onEdit: (LessonId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var managing by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<NamingDialog?>(null) }

    ScreenScaffold(
        title = "My Lessons",
        onBack = onBack,
        modifier = modifier,
        header = ScreenHeader.Description(
            if (managing) {
                "Move, rename and remove. Nothing here is started while you are managing."
            } else {
                "Tap to practise. Edit opens the setup it was saved with."
            },
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                SecondaryButton(
                    text = if (managing) "Done" else "Manage lessons",
                    onClick = { managing = !managing },
                    modifier = Modifier.weight(1f),
                )
                if (managing) {
                    SecondaryButton(
                        text = "New group",
                        onClick = { naming = NamingDialog.NewGroup },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) {
        Spacer(Modifier.height(Dimens.spaceSm))

        if (book.lessons.isEmpty()) {
            Text(
                text = "Set a practice up the way you like it, then use " +
                    "\"Add to your lessons\" to keep it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Dimens.spaceMd))
        }

        book.groups.forEachIndexed { index, group ->
            GroupHeading(
                group = group,
                managing = managing,
                canMoveUp = index > 0,
                canMoveDown = index < book.groups.lastIndex,
                canDelete = book.groups.size > 1,
                onMove = { by -> onWrite(book.movedGroup(group.id, by)) },
                onRename = { naming = NamingDialog.RenameGroup(group.id, group.name) },
                onDelete = { onWrite(book.minusGroup(group.id)) },
            )

            val saved = book.lessonsOf(group.id)
            if (saved.isEmpty()) {
                Text(
                    text = "Nothing saved here yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            saved.forEachIndexed { position, lesson ->
                SavedLessonCard(
                    lesson = lesson,
                    managing = managing,
                    canMoveUp = position > 0,
                    canMoveDown = position < saved.lastIndex,
                    onStart = { onStart(lessonIdOf(lesson.id)) },
                    onEdit = { onEdit(lessonIdOf(lesson.id)) },
                    onMove = { by -> onWrite(book.movedLesson(lesson.id, by)) },
                    onDelete = { onWrite(book.minusLesson(lesson.id)) },
                )
            }
            Spacer(Modifier.height(Dimens.spaceMd))
        }
        Spacer(Modifier.height(Dimens.spaceLg))
    }

    when (val dialog = naming) {
        null -> Unit
        is NamingDialog.NewGroup -> NameDialog(
            title = "New group",
            initial = "",
            onConfirm = { name ->
                onWrite(book.plusGroup(name))
                naming = null
            },
            onCancel = { naming = null },
        )

        is NamingDialog.RenameGroup -> NameDialog(
            title = "Rename group",
            initial = dialog.name,
            onConfirm = { name ->
                onWrite(book.renamedGroup(dialog.id, name))
                naming = null
            },
            onCancel = { naming = null },
        )
    }
}

/** Which name is being asked for, or none. The only state this screen keeps of its own. */
private sealed interface NamingDialog {
    data object NewGroup : NamingDialog

    data class RenameGroup(val id: String, val name: String) : NamingDialog
}

@Composable
private fun GroupHeading(
    group: UserLessonGroup,
    managing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onMove: (Int) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader(text = group.name, modifier = Modifier.weight(1f))
        if (managing) {
            MoveButtons(group.name, canMoveUp, canMoveDown, onMove)
            ActionIconButton(
                icon = IconSource.Vector(SaxTrainerIcons.Rename),
                contentDescription = "Rename ${group.name}",
                onClick = onRename,
            )
            ActionIconButton(
                icon = IconSource.Vector(SaxTrainerIcons.Delete),
                contentDescription = "Delete ${group.name}",
                onClick = onDelete,
                enabled = canDelete,
            )
        }
    }
}

@Composable
private fun SavedLessonCard(
    lesson: UserLesson,
    managing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    // A trainer this version does not know about is drawn and can be deleted, but cannot be run:
    // the practice is still the player's, it is simply not one this build can offer.
    val trainer = trainerNameOf(lesson.trainerId)

    LessonCard(
        title = lesson.name,
        subtitle = trainer ?: "Saved by a newer version",
        onClick = { if (!managing && trainer != null) onStart() },
        enabled = !managing && trainer != null,
        trailing = {
            if (managing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoveButtons(lesson.name, canMoveUp, canMoveDown, onMove)
                    ActionIconButton(
                        icon = IconSource.Vector(SaxTrainerIcons.Delete),
                        contentDescription = "Delete ${lesson.name}",
                        onClick = onDelete,
                    )
                }
            } else if (trainer != null) {
                ActionIconButton(
                    icon = IconSource.Vector(SaxTrainerIcons.Edit),
                    contentDescription = "Edit ${lesson.name}",
                    onClick = onEdit,
                )
            }
        },
    )
}

/**
 * Up and down, rather than a drag handle.
 *
 * Two taps say exactly what one drag does, work the same in a browser as under a thumb, and can be
 * checked without a device - and the order they produce is the same order a handle would.
 */
@Composable
private fun MoveButtons(
    name: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
) {
    ActionIconButton(
        icon = IconSource.Vector(SaxTrainerIcons.MoveUp),
        contentDescription = "Move $name up",
        onClick = { onMove(-1) },
        enabled = canMoveUp,
    )
    ActionIconButton(
        icon = IconSource.Vector(SaxTrainerIcons.MoveDown),
        contentDescription = "Move $name down",
        onClick = { onMove(1) },
        enabled = canMoveDown,
    )
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
