package com.example.saxtrainer.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.saxtrainer.core.designsystem.component.ActionIconButton
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.core.designsystem.component.InlineIcon
import com.example.saxtrainer.core.designsystem.icon.Delete
import com.example.saxtrainer.core.designsystem.icon.Edit
import com.example.saxtrainer.core.designsystem.icon.MoveDown
import com.example.saxtrainer.core.designsystem.icon.MoveUp
import com.example.saxtrainer.core.designsystem.icon.Rename
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons

/*
 * The controls both My Lessons screens manage their lists with: the groups on one, the practices in
 * a group on the other. One set, so moving, renaming and deleting look and behave the same at
 * either level.
 */

/**
 * The pen in the title bar: switches the list between being used and being managed.
 *
 * A toggle rather than a button, so it shows which way round the screen is - lit while managing.
 */
@Composable
internal fun ManageToggle(managing: Boolean, what: String, onChange: (Boolean) -> Unit) {
    IconToggleButton(checked = managing, onCheckedChange = onChange) {
        InlineIcon(
            icon = IconSource.Vector(SaxTrainerIcons.Edit),
            contentDescription = if (managing) "Done managing $what" else "Manage $what",
        )
    }
}

/**
 * Up, down, rename and delete for one item of a managed list.
 *
 * Up and down rather than a drag handle: two taps say exactly what one drag does, work the same in
 * a browser as under a thumb, and can be checked without a device.
 */
@Composable
internal fun ManageButtons(
    name: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean = true,
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
    ActionIconButton(
        icon = IconSource.Vector(SaxTrainerIcons.Rename),
        contentDescription = "Rename $name",
        onClick = onRename,
    )
    ActionIconButton(
        icon = IconSource.Vector(SaxTrainerIcons.Delete),
        contentDescription = "Delete $name",
        onClick = onDelete,
        enabled = canDelete,
    )
}

/** Asks for a name: a new group's, or a new one for something that has one. */
@Composable
internal fun NameDialog(
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
