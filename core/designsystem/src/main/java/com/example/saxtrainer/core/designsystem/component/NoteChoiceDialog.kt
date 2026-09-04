package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.music.Note

/**
 * Picks a handful of notes out of everything the instrument can play.
 *
 * Every note exercise offers the same choice, so the list lives here rather than in whichever
 * trainer happened to need it first. It holds no selection of its own: [chosen] is the caller's
 * draft, and nothing is decided until Apply.
 */
@Composable
fun NoteChoiceDialog(
    notes: List<Note>,
    chosen: Set<Note>,
    label: (Note) -> String,
    onToggle: (Note) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    title: String = "Choose notes",
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                notes.forEach { note ->
                    CheckableRow(
                        title = label(note),
                        checked = note in chosen,
                        onCheckedChange = { onToggle(note) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                    SecondaryButton(text = "All", onClick = onSelectAll)
                    SecondaryButton(text = "None", onClick = onClearAll)
                }
            }
        },
        confirmButton = {
            // Nothing to practise is not a choice, so Apply waits for at least one note.
            TextButton(onClick = onApply, enabled = chosen.isNotEmpty()) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}
