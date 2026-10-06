package com.example.saxtrainer.core.designsystem.component

import androidx.compose.runtime.Composable

/**
 * Hands a text file to the platform to keep: on Android the system's save dialog, so the player
 * picks the folder; in a browser an ordinary download.
 *
 * Remembered rather than called, because Android can only ask for a place to save from a
 * composable that was there before the request - and a call can come from any button.
 */
fun interface TextFileSaver {
    fun save(fileName: String, mimeType: String, text: String)
}

/** The platform's way of saving a file, for as long as the calling composable is shown. */
@Composable
expect fun rememberTextFileSaver(): TextFileSaver
