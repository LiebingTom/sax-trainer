package com.example.saxtrainer.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.example.saxtrainer.core.designsystem.component.AnswerOption
import com.example.saxtrainer.core.designsystem.component.AnswerState
import com.example.saxtrainer.core.designsystem.component.CentsMeter
import com.example.saxtrainer.core.designsystem.component.FeatureCard
import com.example.saxtrainer.core.designsystem.component.FeatureIcon
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.core.designsystem.component.LevelMeter
import com.example.saxtrainer.core.designsystem.component.MusicalStaffView
import com.example.saxtrainer.core.designsystem.component.SaxophoneFingeringView
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.icon.LongTone
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.fingering.TenorSaxophoneFingerings
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The design system, composed for real in a browser.
 *
 * Compiling for Wasm only proves the types resolve. These cases put the theme and the drawing-heavy
 * components through an actual composition and layout on a Skiko canvas, which is the thing that
 * would break first if a component reached for something only Android has: a text measurer, a
 * density, a canvas path effect, a Material 3 window-size class.
 *
 * They live in `wasmJsTest` rather than `commonTest` because the Android half of this module has no
 * UI test host - `androidHostTest` is a plain JVM source set with no Android runtime - and the
 * milestone's question is whether the *web* can consume the design system.
 */
@OptIn(ExperimentalTestApi::class)
class DesignSystemOnWasmTest {

    private val note = Note(NoteName.A, 4)

    @Test
    fun the_theme_and_a_screen_of_components_compose_and_lay_out() = runComposeUiTest {
        setContent {
            SaxTrainerTheme(darkTheme = false) {
                ScreenScaffold(
                    title = "Long tones",
                    header = ScreenHeader.Question("Play an A"),
                    subject = {
                        MusicalStaffView(
                            sequence = ToneSequence(steps = listOf(ToneStep(note, Beats(1.0)))),
                        )
                    },
                    actions = { Text("Start") },
                ) {
                    SectionHeader("Tuning")
                    CentsMeter(cents = 12f, inTune = false, contentDescription = "twelve cents sharp")
                    LevelMeter(fraction = 0.4f, contentDescription = "level")
                    SaxophoneFingeringView(
                        fingering = TenorSaxophoneFingerings.fingeringsFor(note)!!.standard,
                        contentDescription = "the grip for A",
                    )
                }
            }
        }

        onNodeWithText("Long tones").assertIsDisplayed()
        onNodeWithText("Play an A").assertIsDisplayed()
        onNodeWithText("Tuning").assertIsDisplayed()
        onNodeWithContentDescription("twelve cents sharp").assertIsDisplayed()
        onNodeWithContentDescription("the grip for A").assertIsDisplayed()
    }

    @Test
    fun an_icon_drawn_from_kotlin_paths_reaches_the_canvas() = runComposeUiTest {
        // The M0 decision under test: icons are ImageVector code, so they need no resource loader
        // and no platform of their own. A drawable would not have got this far.
        setContent {
            SaxTrainerTheme {
                FeatureCard(
                    title = "Long tones",
                    description = "Hold a note steady",
                    icon = IconSource.Vector(SaxTrainerIcons.LongTone),
                    onClick = {},
                )
            }
        }

        onNodeWithText("Hold a note steady").assertIsDisplayed()
    }

    @Test
    fun a_component_still_responds_to_a_click() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaxTrainerTheme {
                Column {
                    AnswerOption(
                        onClick = { clicks++ },
                        state = AnswerState.NEUTRAL,
                        contentDescription = "the note A",
                    ) {
                        FeatureIcon(IconSource.Vector(SaxTrainerIcons.LongTone))
                    }
                }
            }
        }

        onNodeWithContentDescription("the note A").performClick()
        waitForIdle()

        assertEquals(1, clicks, "the click never reached the option")
    }

    @Test
    fun the_dark_palette_composes_too() = runComposeUiTest {
        // Dark mode is chosen by `isSystemInDarkTheme()` on Android; on the web the value comes from
        // a media query, so the branch is worth composing rather than assuming.
        setContent {
            SaxTrainerTheme(darkTheme = true) {
                SectionHeader("Practice")
            }
        }

        onNodeWithText("Practice").assertIsDisplayed()
    }
}
