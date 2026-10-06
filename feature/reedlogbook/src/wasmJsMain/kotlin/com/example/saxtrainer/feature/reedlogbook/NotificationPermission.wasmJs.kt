package com.example.saxtrainer.feature.reedlogbook

import androidx.compose.runtime.Composable

/** The web shows a running session on the page only, so there is nothing to ask. */
@Composable
actual fun rememberNotificationPermissionAsker(): () -> Unit = {}
