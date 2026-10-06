package com.example.saxtrainer.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Asks before doing something that cannot be taken back - deleting a timed session, marking a reed
 * broken.
 *
 * Kept for what is truly lost by one tap: a list that is only rearranged, or managed behind the pen
 * on purpose, does not need it.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
