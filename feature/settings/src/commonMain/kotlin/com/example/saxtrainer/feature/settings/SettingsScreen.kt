package com.example.saxtrainer.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.saxtrainer.core.designsystem.component.CheckableRow
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.SelectableRow
import com.example.saxtrainer.core.designsystem.component.ThemeSwatch
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.settings.Instrument
import com.example.saxtrainer.core.settings.NoteDisplay
import com.example.saxtrainer.core.settings.ThemeColor
import com.example.saxtrainer.core.settings.ThemeMode

/**
 * Global settings. Sections are independent, so a future setting (tuning reference, audio input) is
 * added as one more section without touching the existing ones.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onInstrumentSelected: (Instrument) -> Unit,
    onNoteDisplaySelected: (NoteDisplay) -> Unit,
    onNoteNamingSelected: (NoteNaming) -> Unit,
    onCountInSelected: (Int) -> Unit,
    onCountInClickChanged: (Boolean) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onThemeColorSelected: (ThemeColor) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(title = "Settings", onBack = onBack, modifier = modifier) {
        Spacer(Modifier.height(Dimens.spaceSm))
        SectionHeader("Instrument")
        Text(
            text = "Used by every tool to show note names in the pitch you read.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.spaceSm))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                state.instruments.forEach { instrument ->
                    SelectableRow(
                        title = instrument.displayName,
                        supportingText = instrument.description,
                        selected = instrument == state.settings.instrument,
                        onClick = { onInstrumentSelected(instrument) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Dimens.spaceLg))

        SectionHeader("Note naming")
        Text(
            text = "What the note letters are called, wherever a note name is shown. The " +
                "pitches themselves do not change.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.spaceSm))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                state.noteNamings.forEach { naming ->
                    SelectableRow(
                        title = naming.displayName,
                        supportingText = naming.description,
                        selected = naming == state.settings.noteNaming,
                        onClick = { onNoteNamingSelected(naming) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Dimens.spaceLg))

        SectionHeader("Count-in")
        Text(
            text = "Beats counted off before a practice starts. Practices that count " +
                "themselves in - Long Tones, Sequences - keep their own count.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.spaceSm))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                state.countInChoices.forEach { beats ->
                    SelectableRow(
                        title = countInTitle(beats),
                        supportingText = countInDescription(beats),
                        selected = beats == state.settings.countInBeats,
                        onClick = { onCountInSelected(beats) },
                    )
                }
                CheckableRow(
                    title = "Count out loud",
                    supportingText = "Clicks the count on the metronome. Off, it is only shown.",
                    checked = state.settings.countInClick,
                    onCheckedChange = onCountInClickChanged,
                    enabled = state.settings.countInBeats > 0,
                )
            }
        }
        Spacer(Modifier.height(Dimens.spaceLg))

        SectionHeader("Note display")
        Text(
            text = "How notes are shown by default. Each tool can be set differently while " +
                "you practise.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.spaceSm))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                state.noteDisplays.forEach { display ->
                    SelectableRow(
                        title = display.displayName,
                        supportingText = display.description,
                        selected = display == state.settings.noteDisplay,
                        onClick = { onNoteDisplaySelected(display) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Dimens.spaceLg))

        SectionHeader("Appearance")
        Text(
            text = "Light or dark, and the main colour every other colour in the app is taken " +
                "from.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.spaceSm))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                state.themeModes.forEach { mode ->
                    SelectableRow(
                        title = mode.displayName,
                        supportingText = mode.description,
                        selected = mode == state.settings.themeMode,
                        onClick = { onThemeModeSelected(mode) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Dimens.spaceSm))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                state.themeColors.forEach { color ->
                    SelectableRow(
                        title = color.displayName,
                        selected = color == state.settings.themeColor,
                        onClick = { onThemeColorSelected(color) },
                        trailing = { ThemeSwatch(seed = color.seed) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Dimens.spaceLg))
    }
}

private fun countInTitle(beats: Int): String =
    if (beats == 0) "No count-in" else "$beats beats"

private fun countInDescription(beats: Int): String = when (beats) {
    0 -> "Practices begin the moment you press Start"
    // The count is what a player says out loud, so a bar of four reads as the familiar one. The
    // quiet beats before it are mentioned because they are time the player waits through; how many
    // is `leadInBeatsFor` in core:exercise, not worth a module dependency to say exactly.
    4 -> "A quiet moment, then counted 4, 3, 2, 1 before the first note"
    else -> "A quiet moment, then counted down from $beats before the first note"
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SaxTrainerTheme {
        SettingsScreen(
            state = SettingsUiState(),
            onInstrumentSelected = {},
            onNoteDisplaySelected = {},
            onNoteNamingSelected = {},
            onCountInSelected = {},
            onCountInClickChanged = {},
            onThemeModeSelected = {},
            onThemeColorSelected = {},
            onBack = {},
        )
    }
}
