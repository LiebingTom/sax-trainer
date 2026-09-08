package com.example.saxtrainer.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.SelectableRow
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.UserLessonBook
import com.example.saxtrainer.core.settings.plusGroup
import com.example.saxtrainer.core.settings.plusLesson

/**
 * Naming and filing the practice a trainer is currently set up for.
 *
 * The configuration itself never passes through here: the trainer has already written it down as
 * its own last setup, and this is handed that [setup]. All the player is asked for is a name and
 * somewhere to put it.
 */
@Composable
fun AddToLessonsScreen(
    book: UserLessonBook,
    setup: ExerciseSetup,
    trainerId: String,
    onSave: (UserLessonBook) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf(book.groups.first().id) }
    var newGroup by remember { mutableStateOf("") }

    // A group typed in here is made and used in one go, so the player never has to leave, make a
    // group, come back and set the exercise up again.
    val save = {
        val filed = if (newGroup.isBlank()) {
            book to groupId
        } else {
            val grown = book.plusGroup(newGroup)
            grown to grown.groups.last().id
        }
        onSave(filed.first.plusLesson(name, filed.second, trainerId, setup))
    }

    ScreenScaffold(
        title = "Add to your lessons",
        onBack = onBack,
        modifier = modifier,
        header = ScreenHeader.Description(
            "Keeps this practice exactly as it is set up now, under a name of your own.",
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                PrimaryButton(
                    text = "Save",
                    onClick = save,
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "Cancel",
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        Spacer(Modifier.height(Dimens.spaceSm))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Dimens.spaceSm))
        SectionHeader("Group")

        book.groups.forEach { group ->
            SelectableRow(
                title = group.name,
                selected = group.id == groupId && newGroup.isBlank(),
                onClick = {
                    groupId = group.id
                    newGroup = ""
                },
            )
        }

        OutlinedTextField(
            value = newGroup,
            onValueChange = { newGroup = it },
            singleLine = true,
            label = { Text("Or a new group") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Dimens.spaceLg))
    }
}
