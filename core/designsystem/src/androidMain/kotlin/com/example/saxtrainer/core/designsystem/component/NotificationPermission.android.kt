package com.example.saxtrainer.core.designsystem.component

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** `POST_NOTIFICATIONS`, asked for only on Android 13 and later and only while not yet granted. */
@Composable
actual fun rememberNotificationPermissionAsker(): () -> Unit {
    val context = LocalContext.current
    // Whatever the answer, what asked for it runs; there is nothing to do with it here.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(launcher) {
        {
            val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needed) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
