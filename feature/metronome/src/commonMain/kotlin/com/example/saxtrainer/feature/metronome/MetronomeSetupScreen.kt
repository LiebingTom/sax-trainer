package com.example.saxtrainer.feature.metronome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.saxtrainer.core.designsystem.component.CheckableRow
import com.example.saxtrainer.core.designsystem.component.PracticeTitleActions
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.SelectableRow
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.music.TimeSignature

/** Stateless metronome settings UI: choose how the click counts, then start it. */
@Composable
fun MetronomeSetupScreen(
    state: MetronomeSetupUiState,
    onSelectTimeSignature: (TimeSignature) -> Unit,
    onSelectAccentFirstBeat: (Boolean) -> Unit,
    onSelectVisualizeHalfBeats: (Boolean) -> Unit,
    onSelectHalfBeatSound: (Boolean) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: ((Boolean) -> Unit)? = null,
) {
    ScreenScaffold(
        title = "Metronome",
        onBack = onBack,
        modifier = modifier,
        // Starring is about the practice as it is set up, the same place every other tool offers
        // it - nothing to add to My Lessons, since there is nothing here for a lesson to replay.
        titleActions = {
            PracticeTitleActions("Metronome", isFavorite, onToggleFavorite, onAddToLessons = null)
        },
        header = ScreenHeader.Description(
            "Steady click with an accented downbeat and tap tempo.",
        ),
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
            ) {
                PrimaryButton(text = "Start", onClick = onStart, modifier = Modifier.weight(1f))
            }
        },
    ) {
        SectionHeader("Time signature")
        metronomeTimeSignatures.forEach { timeSignature ->
            SelectableRow(
                title = timeSignature.toString(),
                selected = timeSignature == state.timeSignature,
                onClick = { onSelectTimeSignature(timeSignature) },
            )
        }

        Spacer(Modifier.height(Dimens.spaceSm))
        SectionHeader("Sound")
        CheckableRow(
            title = "Accent the first beat",
            supportingText = "Beat one of the bar sounds a stronger click than the rest",
            checked = state.accentFirstBeat,
            onCheckedChange = onSelectAccentFirstBeat,
        )
        CheckableRow(
            title = "Half beat click",
            supportingText = "A softer click sounds halfway between each pair of beats",
            checked = state.halfBeatSound,
            onCheckedChange = onSelectHalfBeatSound,
        )

        Spacer(Modifier.height(Dimens.spaceSm))
        SectionHeader("Display")
        CheckableRow(
            title = "Half beat marker",
            supportingText = "A small circle marks the midpoint between each pair of beats",
            checked = state.visualizeHalfBeats,
            onCheckedChange = onSelectVisualizeHalfBeats,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MetronomeSetupScreenPreview() {
    SaxTrainerTheme {
        MetronomeSetupScreen(
            state = MetronomeSetupUiState(),
            onSelectTimeSignature = {},
            onSelectAccentFirstBeat = {},
            onSelectVisualizeHalfBeats = {},
            onSelectHalfBeatSound = {},
            onStart = {},
            onBack = {},
        )
    }
}
