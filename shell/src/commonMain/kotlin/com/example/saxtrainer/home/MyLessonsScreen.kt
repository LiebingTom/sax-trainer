package com.example.saxtrainer.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.saxtrainer.core.designsystem.component.FeatureCard
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.icon.Assignment
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.settings.UserLessonBook
import com.example.saxtrainer.core.settings.minusGroup
import com.example.saxtrainer.core.settings.movedGroup
import com.example.saxtrainer.core.settings.plusGroup
import com.example.saxtrainer.core.settings.renamedGroup
import com.example.saxtrainer.learning.savedSummaryOf

/**
 * The groups the player keeps their saved practices in, one card each - the way Lessons lists its
 * units. A group opens [MyLessonGroupScreen], which lists what is in it.
 *
 * Drawn from the book rather than from the curriculum's units, so a group the player has made but
 * not filled yet is still here to be opened, renamed or thrown away.
 *
 * The pen in the title bar switches the list to managing the groups themselves: move, rename,
 * delete, and make a new one. Nothing is opened while managing.
 */
@Composable
fun MyLessonsScreen(
    book: UserLessonBook,
    onWrite: (UserLessonBook) -> Unit,
    onOpenGroup: (groupId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var managing by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<GroupNaming?>(null) }

    ScreenScaffold(
        title = "My Lessons",
        onBack = onBack,
        modifier = modifier,
        titleActions = { ManageToggle(managing, "groups") { managing = it } },
        header = ScreenHeader.Description(
            if (managing) {
                "Move, rename and remove groups. Deleting a group deletes what is saved in it."
            } else {
                "Your saved practices, in the groups you put them in."
            },
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        actions = if (managing) {
            {
                SecondaryButton(
                    text = "New group",
                    onClick = { naming = GroupNaming.New },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            null
        },
    ) {
        Spacer(Modifier.height(Dimens.spaceSm))

        if (book.lessons.isEmpty()) {
            Text(
                text = "Set a practice up the way you like it, then tap the ribbon at the top " +
                    "of it to keep it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Dimens.spaceSm))
        }

        book.groups.forEachIndexed { index, group ->
            FeatureCard(
                title = group.name,
                description = savedSummaryOf(book.lessonsOf(group.id).size),
                // The mark Lessons gives its units: a group is the player's own unit.
                icon = IconSource.Vector(SaxTrainerIcons.Assignment),
                onClick = { if (!managing) onOpenGroup(group.id) },
                trailing = if (managing) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ManageButtons(
                                name = group.name,
                                canMoveUp = index > 0,
                                canMoveDown = index < book.groups.lastIndex,
                                onMove = { by -> onWrite(book.movedGroup(group.id, by)) },
                                onRename = { naming = GroupNaming.Rename(group.id, group.name) },
                                onDelete = { onWrite(book.minusGroup(group.id)) },
                                // Every saved practice lives in a group, so the last one stays.
                                canDelete = book.groups.size > 1,
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }
        Spacer(Modifier.height(Dimens.spaceLg))
    }

    when (val dialog = naming) {
        null -> Unit
        is GroupNaming.New -> NameDialog(
            title = "New group",
            initial = "",
            onConfirm = { name ->
                onWrite(book.plusGroup(name))
                naming = null
            },
            onCancel = { naming = null },
        )

        is GroupNaming.Rename -> NameDialog(
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

/** Which group name is being asked for, or none. */
private sealed interface GroupNaming {
    data object New : GroupNaming

    data class Rename(val id: String, val name: String) : GroupNaming
}
