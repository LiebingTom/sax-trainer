package com.example.saxtrainer.core.designsystem.component

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/**
 * `View.keepScreenOn`, the same flag any video player sets - held for as long as any screen asks.
 *
 * Counted rather than set and cleared: every screen shares the activity's one view, and while one
 * screen navigates to the next both are composed at once. A screen leaving must not clear the flag
 * the screen arriving has just set - as a lesson starting a practice by itself used to, letting the
 * phone sleep in the middle of it.
 */
@Composable
actual fun KeepScreenOn(keepAwake: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, keepAwake) {
        if (!keepAwake) return@DisposableEffect onDispose {}
        ScreenAwakeHolders.acquire(view.rootView)
        onDispose { ScreenAwakeHolders.release(view.rootView) }
    }
}

private object ScreenAwakeHolders {
    private val holders = WeakHashMap<View, Int>()

    fun acquire(view: View) {
        val count = (holders[view] ?: 0) + 1
        holders[view] = count
        view.keepScreenOn = true
    }

    fun release(view: View) {
        val count = ((holders[view] ?: 1) - 1).coerceAtLeast(0)
        if (count == 0) holders.remove(view) else holders[view] = count
        view.keepScreenOn = count > 0
    }
}
