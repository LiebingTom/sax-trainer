package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.saxtrainer.core.music.ToneSequence

/**
 * A quick look at what a phrase or a pattern actually sounds like, before picking it.
 *
 * Read-only: this is a preview, not the setup screen for the thing it is showing. A pattern's
 * [sequence] is resolved by the caller against whatever scale, root and octave are currently
 * chosen, so the notes shown here are the ones that would actually be practised - not the pattern's
 * degrees in the abstract.
 */
@Composable
fun NotesPreviewDialog(title: String, sequence: ToneSequence, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // A page, one bar to a row, rather than one long line to scroll sideways through.
            BoundedMusicalPage(
                sequence = sequence,
                modifier = Modifier.fillMaxWidth(),
                staffColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
