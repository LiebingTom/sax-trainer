package com.example.saxtrainer.core.designsystem.component

import androidx.compose.runtime.Composable

/**
 * Keeps the screen from dimming or locking itself while [keepAwake] is true.
 *
 * On for as long as an exercise is actually running, and never otherwise: a player mid-phrase or
 * mid-sequence has both hands on an instrument and cannot tap the screen to wake it back up, but a
 * setup screen or a finished result should sleep exactly as any other screen does. Turning [keepAwake]
 * off, or leaving the composition, always lets the screen sleep normally again - this never holds
 * anything past its own call site.
 */
@Composable
expect fun KeepScreenOn(keepAwake: Boolean)
