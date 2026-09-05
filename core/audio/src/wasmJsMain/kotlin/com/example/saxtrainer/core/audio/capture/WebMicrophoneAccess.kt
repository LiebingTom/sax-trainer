package com.example.saxtrainer.core.audio.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.saxtrainer.core.audio.web.errorNameOf
import com.example.saxtrainer.core.audio.web.queryMicrophonePermission
import com.example.saxtrainer.core.audio.web.requestMicrophone
import com.example.saxtrainer.core.audio.web.stopTracks
import com.example.saxtrainer.core.audio.web.streamOf

/**
 * One per screen, like Android's - though the browser needs no launcher to register, so this is
 * only a `remember`.
 */
@Composable
actual fun rememberMicrophoneAccess(): MicrophoneAccess = remember { WebMicrophoneAccess() }

/**
 * [MicrophoneAccess] for the browser.
 *
 * Same rule as on Android: this answers "may this page record?" and never hands out a stream.
 * [WebAudioCapture] opens the microphone itself when its flow is collected and stops the tracks when
 * collection ends. The browser prompts once per origin, so the user still sees exactly one prompt
 * even though `getUserMedia` is reached twice.
 *
 * Nothing here runs before a trainer's Start button: the permission query does not prompt, and the
 * only call that can is [request], which a route makes at the moment capture is about to begin.
 */
class WebMicrophoneAccess : MicrophoneAccess {

    /**
     * What the browser last said. The web answers asynchronously and [MicrophoneAccess.isGranted] is
     * synchronous, so this starts pessimistic and is corrected by the standing query below and by
     * every [request]. Saying "not yet" wrongly costs a query; saying "yes" wrongly would open a
     * screen that then cannot record.
     */
    private var granted = false

    init {
        refresh()
    }

    override val isGranted: Boolean get() = granted

    override fun request(onResult: (Boolean) -> Unit) {
        if (granted) {
            onResult(true)
            return
        }
        queryMicrophonePermission().then<JsAny?> { state ->
            when (state.toString()) {
                GRANTED -> {
                    granted = true
                    onResult(true)
                }

                DENIED -> {
                    granted = false
                    onResult(false)
                }

                // "prompt", or a browser with no Permissions API for the microphone. Asking is the
                // only way to find out, and asking is exactly what the user just pressed Start for.
                else -> ask(onResult)
            }
            null
        }
    }

    /** Nowhere to send the user: a browser's site permissions are not reachable from the page. */
    override fun openSettings() = Unit

    private fun ask(onResult: (Boolean) -> Unit) {
        requestMicrophone().then<JsAny?> { result ->
            val failure = errorNameOf(result)
            if (failure == null) {
                // Handed straight back: the capture opens its own stream, and holding this one open
                // would be the screen quietly recording while it waits for a Start it already had.
                stopTracks(streamOf(result))
                granted = true
                onResult(true)
            } else {
                granted = false
                onResult(false)
            }
            null
        }
    }

    private fun refresh() {
        queryMicrophonePermission().then<JsAny?> { state ->
            granted = state.toString() == GRANTED
            null
        }
    }

    private companion object {
        const val GRANTED = "granted"
        const val DENIED = "denied"
    }
}
