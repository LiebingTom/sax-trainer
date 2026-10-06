package com.example.saxtrainer

import android.content.Context
import android.util.Log
import com.example.saxtrainer.core.reed.ReedLogbookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the notification in step with the logbook: shown while a session runs, gone when none does.
 *
 * It watches the stored logbook rather than being told by the screens, so a session started or
 * stopped anywhere - the logbook, the landing page, the notification's own Stop - is reflected the
 * same way. [refresh] puts it back after the app was closed in the background with a session
 * running, which is why the activity calls it whenever it comes to the front.
 */
class ReedSessionSync(
    private val context: Context,
    private val logbook: ReedLogbookRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            logbook.logbook
                // What the notification shows: which session, on which reed as described now.
                .map { book -> book.runningSession?.let { it to book.reedOf(it.reedId) } }
                .distinctUntilChanged()
                .collect { running -> if (running != null) show() else ReedSessionService.dismiss(context) }
        }
    }

    /** Shows the notification again if a session is running and it is not showing. */
    fun refresh() {
        if (logbook.logbook.value.runningSession != null) show()
    }

    private fun show() {
        try {
            ReedSessionService.refresh(context)
        } catch (refused: IllegalStateException) {
            // Android does not let an app in the background start a foreground service. The session
            // itself is stored and keeps counting; the notification comes back with the app.
            Log.i(TAG, "Reed session notification deferred until the app is in front", refused)
        }
    }

    private companion object {
        const val TAG = "ReedSessionSync"
    }
}
