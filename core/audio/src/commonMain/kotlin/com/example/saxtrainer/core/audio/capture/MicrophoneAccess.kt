package com.example.saxtrainer.core.audio.capture

import androidx.compose.runtime.Composable

/**
 * Whether this app may record, and how to ask.
 *
 * Deliberately about permission and nothing else: it never hands out a stream. Opening and closing
 * the microphone is [AudioCapture]'s job, for as long as its flow is collected. The two are separate
 * because their lifetimes are - permission outlives a screen, a recording does not - and because a
 * screen asking "may I?" should not accidentally be holding the device open.
 *
 * Four trainers used to repeat the same launcher, the same granted-check and, in one case, the same
 * hand-built intent to the app's settings page. This is that, once.
 *
 * How a screen gets one is [rememberMicrophoneAccess], which every platform answers its own way.
 */
interface MicrophoneAccess {
    /**
     * Read afresh each time: the answer can change while the app is in the background.
     *
     * The browser answers this asynchronously, so the web implementation reports what it last
     * learned and only ever says yes once it knows. [request] is the reliable question on both
     * platforms, and it is what every caller already uses.
     */
    val isGranted: Boolean

    /**
     * Asks, if asking is needed. [onResult] is called either way - immediately and with `true` when
     * permission is already held, so a caller never has to check first.
     */
    fun request(onResult: (Boolean) -> Unit)

    /**
     * Opens wherever the user can grant permission by hand, for when they have refused once and the
     * system will no longer prompt. A no-op where the platform has no such place.
     */
    fun openSettings()
}

/**
 * The [MicrophoneAccess] for this screen. Safe to call whether or not the screen needs audio:
 * nothing is prompted for and no device is opened until [MicrophoneAccess.request] is called.
 *
 * A composable rather than a plain factory because Android's prompt is delivered to a
 * Compose-scoped result launcher, which can only be registered from a composition.
 */
@Composable
expect fun rememberMicrophoneAccess(): MicrophoneAccess
