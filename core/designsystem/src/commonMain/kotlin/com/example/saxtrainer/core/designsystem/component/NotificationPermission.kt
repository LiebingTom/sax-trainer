package com.example.saxtrainer.core.designsystem.component

import androidx.compose.runtime.Composable

/**
 * Asks once for leave to show a notification for something running - a reed session, the
 * metronome - where the platform needs that asked: Android 13 and later. What asked runs either
 * way; without leave it simply has no notification.
 */
@Composable
expect fun rememberNotificationPermissionAsker(): () -> Unit
