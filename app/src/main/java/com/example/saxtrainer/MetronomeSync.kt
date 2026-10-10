package com.example.saxtrainer

import android.content.Context
import android.util.Log
import com.example.saxtrainer.core.audio.metronome.ActiveMetronome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the metronome's notification there for exactly as long as the metronome screen is open:
 * shown when it opens, gone when it is left. What the notification says, the service keeps up to
 * date itself.
 */
class MetronomeSync(
    private val context: Context,
    private val active: ActiveMetronome,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            active.summary
                .map { it != null }
                .distinctUntilChanged()
                .collect { open -> if (open) show() else MetronomeService.dismiss(context) }
        }
    }

    /**
     * Stops the click when the app goes to the background without the notification up - refused by
     * Android, or not yet started - so it never plays on where it cannot be seen and stopped.
     */
    fun stopIfUnseen() {
        if (active.isRunning && !MetronomeService.isShowing) active.stop()
    }

    private fun show() {
        try {
            MetronomeService.show(context)
        } catch (refused: IllegalStateException) {
            // Only opened from a screen in front, so this should not happen; if it does, the click
            // simply stops with the app in the background - see stopIfUnseen.
            Log.i(TAG, "Metronome notification refused", refused)
        }
    }

    private companion object {
        const val TAG = "MetronomeSync"
    }
}
