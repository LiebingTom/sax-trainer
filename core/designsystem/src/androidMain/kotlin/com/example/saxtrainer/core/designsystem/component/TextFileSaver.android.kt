package com.example.saxtrainer.core.designsystem.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The system's "save as" dialog: the player chooses where the file goes - Downloads, a cloud
 * folder - and the app only ever writes to that one document. No storage permission, and no file
 * provider to declare.
 */
@Composable
actual fun rememberTextFileSaver(): TextFileSaver {
    val context = LocalContext.current
    // What to write once a place is chosen. Held outside the launcher: the dialog only answers
    // with where, never with what.
    val pending = remember { arrayOfNulls<String>(1) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val text = pending[0]
        pending[0] = null
        if (uri == null || text == null) return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.encodeToByteArray()) }
    }
    return remember(createDocument) {
        TextFileSaver { fileName, _, text ->
            pending[0] = text
            createDocument.launch(fileName)
        }
    }
}
