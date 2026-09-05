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
