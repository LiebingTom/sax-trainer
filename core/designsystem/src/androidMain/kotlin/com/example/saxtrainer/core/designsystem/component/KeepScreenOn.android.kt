package com.example.saxtrainer.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** `View.keepScreenOn`, the same flag any video player sets - scoped to this composable's lifetime. */
@Composable
actual fun KeepScreenOn(keepAwake: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }
}
