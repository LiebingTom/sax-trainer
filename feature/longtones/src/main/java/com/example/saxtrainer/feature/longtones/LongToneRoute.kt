package com.example.saxtrainer.feature.longtones

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioPermission
import com.example.saxtrainer.core.settings.SettingsRepository

/**
 * Stateful entry point of the long tone feature.
 *
 * The microphone permission is requested here, when the user actually starts an exercise, and never
 * on the landing page.
 */
@Composable
fun LongToneRoute(
    audioCapture: AudioCapture,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LongToneViewModel = viewModel {
        LongToneViewModel(audioCapture, settingsRepository)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.start()
    }

    // Recording must not continue while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.stop()
    }

    LongToneScreen(
        state = state,
        onSelectNote = viewModel::selectNote,
        onSelectDuration = viewModel::selectDuration,
        onStart = {
            if (AudioPermission.isGranted(context)) {
                permissionDenied = false
                viewModel.start()
            } else {
                permissionLauncher.launch(AudioPermission.RECORD_AUDIO)
            }
        },
        onStop = viewModel::stop,
        onReset = viewModel::reset,
        onBack = onBack,
        modifier = modifier,
        permissionDenied = permissionDenied,
        onOpenAppSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            )
        },
    )
}
