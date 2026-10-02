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
import com.example.saxtrainer.core.music.SpelledNote

/**
 * Picks a handful of written notes out of everything the instrument can play.
 *
 * Every note exercise offers the same choice, so the list lives here rather than in whichever
 * trainer happened to need it first. It holds no selection of its own: [chosen] is the caller's
 * draft, and nothing is decided until Apply.
 *
 * A pitch that can be written more than one way is offered once per spelling - D sharp and E flat
 * are separate rows - and each such row says which others it sounds the same as, so choosing a
 * spelling is ticking the row that is written the way it should be read.
 */
@Composable
fun NoteChoiceDialog(
    notes: List<SpelledNote>,
    chosen: Set<SpelledNote>,
    label: (SpelledNote) -> String,
    onToggle: (SpelledNote) -> Unit,
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
                    val sameKey = notes.filter { it != note && it.note == note.note }
                    CheckableRow(
                        title = label(note),
                        checked = note in chosen,
                        onCheckedChange = { onToggle(note) },
                        supportingText = sameKey.takeIf { it.isNotEmpty() }
                            ?.joinToString(prefix = "Same key as ", transform = label),
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
