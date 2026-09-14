package com.example.saxtrainer

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SaxTrainerApplication).container
        setContent {
            SaxTrainerApp(container = container, onAppearanceChanged = ::applyAppearance)
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

    private companion object {
        const val NAVIGATION_SCRIM_ALPHA = 0.8f
    }
}
