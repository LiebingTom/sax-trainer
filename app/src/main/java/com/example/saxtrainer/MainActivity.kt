package com.example.saxtrainer

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SaxTrainerApplication).container
        // Opened from the reed session's notification: straight to the logbook. Not on a restore,
        // which brings back whatever screen was open instead.
        if (savedInstanceState == null) requestScreensIfAsked(intent)
        setContent {
            SaxTrainerApp(
                container = container,
                onAppearanceChanged = ::applyAppearance,
                reedLogbookRequests = reedLogbookOpens,
                metronomeRequests = metronomeOpens,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestScreensIfAsked(intent)
    }

    override fun onStart() {
        super.onStart()
        // A session left running while the app was closed in the background gets its notification
        // back now that the app is in front, where Android allows starting it.
        (application as SaxTrainerApplication).reedSessionSync.refresh()
    }

    override fun onStop() {
        super.onStop()
        // The last guard against a click ticking on unseen: playing on in the background is only
        // allowed while its notification is up, and it may not be - refused, or not started yet.
        (application as SaxTrainerApplication).metronomeSync.stopIfUnseen()
    }

    /** Held until the app's navigation is there to take it. */
    private val reedLogbookRequests = Channel<Unit>(Channel.CONFLATED)
    private val reedLogbookOpens = reedLogbookRequests.receiveAsFlow()

    private val metronomeRequests = Channel<Unit>(Channel.CONFLATED)
    private val metronomeOpens = metronomeRequests.receiveAsFlow()

    private fun requestScreensIfAsked(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_REED_LOGBOOK, false) == true) {
            intent.removeExtra(EXTRA_OPEN_REED_LOGBOOK)
            reedLogbookRequests.trySend(Unit)
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_METRONOME, false) == true) {
            intent.removeExtra(EXTRA_OPEN_METRONOME)
            metronomeRequests.trySend(Unit)
        }
    }

    /**
     * Makes what Android draws around the app follow the app's theme rather than the device's.
     *
     * Without it a player who picks light on a dark phone gets white status bar icons on a light
     * screen. The window background is what shows for a moment when the window resizes, as when the
     * keyboard opens; the scrim is what three-button navigation draws its buttons on.
     */
    private fun applyAppearance(darkTheme: Boolean, background: Color) {
        val scrim = background.copy(alpha = NAVIGATION_SCRIM_ALPHA).toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(scrim, scrim) { darkTheme },
        )
        window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
    }

    companion object {
        /** Set by the reed session's notification: open the reed logbook. */
        const val EXTRA_OPEN_REED_LOGBOOK = "com.example.saxtrainer.extra.OPEN_REED_LOGBOOK"

        /** Set by the metronome's notification: show the open metronome. */
        const val EXTRA_OPEN_METRONOME = "com.example.saxtrainer.extra.OPEN_METRONOME"

        private const val NAVIGATION_SCRIM_ALPHA = 0.8f
    }
}
