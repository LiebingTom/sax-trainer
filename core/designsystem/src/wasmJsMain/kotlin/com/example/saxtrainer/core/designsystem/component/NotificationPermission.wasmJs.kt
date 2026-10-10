package com.example.saxtrainer.core.designsystem.component

import androidx.compose.runtime.Composable

/** The web shows what is running on the page only, so there is nothing to ask. */
@Composable
actual fun rememberNotificationPermissionAsker(): () -> Unit = {}
