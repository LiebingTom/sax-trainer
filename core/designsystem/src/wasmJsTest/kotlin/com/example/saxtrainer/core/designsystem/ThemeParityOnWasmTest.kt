package com.example.saxtrainer.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.designsystem.theme.ThemeSeeds
import com.example.saxtrainer.core.designsystem.theme.saxTrainerColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What colour the browser actually paints.
 *
 * The theme is one file in `commonMain`, it takes no dynamic colour and both platforms call the
 * same `SaxTrainerTheme`, so the palette cannot differ by construction. What could still differ is
 * everything between the palette and the screen: Material 3 filling a slot the theme leaves unset,
 * or Skiko putting the canvas in a colour space of its own.
 *
 * So these read pixels back off the Skiko surface rather than reading the colour scheme. A pixel
 * equal to the generated scheme's colour, exactly, says the whole path from the theme to the browser
 * canvas is the identity - which is what "the same colours as Android" means.
 *
 * The one input that legitimately differs between the two is which of the palettes is chosen:
 * `isSystemInDarkTheme()` reads the device's setting on Android and `prefers-color-scheme` in a
 * browser, and a browser set to light while the phone is set to dark is two right answers.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeParityOnWasmTest {

    @Test
    fun the_light_surface_is_painted_in_the_apps_own_colour() = runComposeUiTest {
        setContent { Painted(darkTheme = false) }

        assertEquals(
            brass(darkTheme = false).surface,
            backgroundPixel(),
            "the light surface is not the brand surface",
        )
    }

    @Test
    fun the_dark_surface_is_painted_in_the_apps_own_colour() = runComposeUiTest {
        setContent { Painted(darkTheme = true) }

        assertEquals(
            brass(darkTheme = true).surface,
            backgroundPixel(),
            "the dark surface is not the brand surface",
        )
    }

    @Test
    fun the_accent_is_the_brand_brass_in_both_schemes() = runComposeUiTest {
        // Material 3 fills any slot a scheme leaves unset with a baseline colour of its own, which
        // is what a "the web looks different" report usually turns out to be. The primary is set in
        // both schemes, and these are the two values the default seed generates for it.
        var light: Color? = null
        var dark: Color? = null
        setContent {
            SaxTrainerTheme(darkTheme = false) { light = MaterialTheme.colorScheme.primary }
            SaxTrainerTheme(darkTheme = true) { dark = MaterialTheme.colorScheme.primary }
        }

        assertEquals(
            brass(darkTheme = false).primary,
            light,
            "the light primary is not the brand brass",
        )
        assertEquals(
            brass(darkTheme = true).primary,
            dark,
            "the dark primary is not the brand brass",
        )
    }

    private fun brass(darkTheme: Boolean) = saxTrainerColorScheme(ThemeSeeds.Brass, darkTheme)

    /** The colour of the surface itself, sampled away from the text drawn on it. */
    private fun ComposeUiTest.backgroundPixel(): Color =
        onNodeWithText(MARKER).captureToImage().toPixelMap()[1, 1]

    @Composable
    private fun Painted(darkTheme: Boolean) {
        SaxTrainerTheme(darkTheme = darkTheme) {
            Surface {
                Box(Modifier.fillMaxSize()) { Text(MARKER) }
            }
        }
    }

    private companion object {
        /** Only there to be found: the capture is of the surface this sits on. */
        const val MARKER = "surface"
    }
}
