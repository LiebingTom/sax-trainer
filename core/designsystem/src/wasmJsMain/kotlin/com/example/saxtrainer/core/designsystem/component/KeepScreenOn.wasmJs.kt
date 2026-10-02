package com.example.saxtrainer.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * The Screen Wake Lock API, best effort: not every browser has it, the tab can lose the lock on
 * its own (switching away, minimising) with no callback this needs to react to, and none of that is
 * a failure worth reporting - the screen only ever dims a little sooner than asked, never later.
 */
@Composable
actual fun KeepScreenOn(keepAwake: Boolean) {
    DisposableEffect(keepAwake) {
        if (keepAwake) requestWakeLock()
        onDispose { releaseWakeLock() }
    }
}

@JsFun(
    """() => {
        if (!navigator.wakeLock) return;
        navigator.wakeLock.request('screen').then(
            (lock) => { window.__saxKeepAwake = lock; },
            () => {},
        );
    }""",
)
private external fun requestWakeLock()

@JsFun(
    """() => {
        if (window.__saxKeepAwake) {
            window.__saxKeepAwake.release().catch(() => {});
            window.__saxKeepAwake = null;
        }
    }""",
)
private external fun releaseWakeLock()
