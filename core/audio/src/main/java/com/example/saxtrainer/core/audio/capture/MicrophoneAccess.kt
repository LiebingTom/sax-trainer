package com.example.saxtrainer.core.audio.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

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
 */
interface MicrophoneAccess {
    /** Read afresh each time: the answer can change while the app is in the background. */
    val isGranted: Boolean

    /**
     * Asks, if asking is needed. [onResult] is called either way - immediately and with `true` when
     * permission is already held, so a caller never has to check first.
     */
    fun request(onResult: (Boolean) -> Unit)

    /**
     * Opens wherever the user can grant permission by hand, for when they have refused once and the
     * system will no longer prompt.
     */
    fun openSettings()
}

/** The [MicrophoneAccess] for this screen. Safe to call whether or not the screen needs audio. */
@Composable
fun rememberMicrophoneAccess(): MicrophoneAccess {
    val context = LocalContext.current

    // The system delivers its answer to the launcher, not to whoever asked, so the caller's callback
    // waits here in between. At most one request is ever in flight - the prompt is modal.
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val answer = pending
        pending = null
        answer?.invoke(granted)
    }

    return remember(context, launcher) {
        AndroidMicrophoneAccess(context) { onResult ->
            pending = onResult
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

private class AndroidMicrophoneAccess(
    private val context: Context,
    private val prompt: ((Boolean) -> Unit) -> Unit,
) : MicrophoneAccess {

    override val isGranted: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun request(onResult: (Boolean) -> Unit) {
        if (isGranted) onResult(true) else prompt(onResult)
    }

    override fun openSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
        )
    }
}
