package com.example.saxtrainer.feature.metronome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.designsystem.theme.numericLarge
import com.example.saxtrainer.feature.metronome.engine.MetronomeError

/** Stateless metronome UI: it renders [state] and reports intent, it never keeps time itself. */
@Composable
fun MetronomeScreen(
    state: MetronomeUiState,
    onToggleRunning: () -> Unit,
    onBpmChange: (Int) -> Unit,
    onNudgeBpm: (Int) -> Unit,
    onTap: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(title = "Metronome", onBack = onBack, modifier = modifier) {
        Spacer(Modifier.height(Dimens.spaceLg))

        if (state.error != null) {
            ErrorCard(state.error)
            Spacer(Modifier.height(Dimens.spaceMd))
        }

        TempoDisplay(bpm = state.bpm)
        Spacer(Modifier.height(Dimens.spaceMd))

        BeatIndicator(
            beatsPerBar = state.beatsPerBar,
            currentBeat = state.currentBeat,
            isRunning = state.isRunning,
        )
        Spacer(Modifier.height(Dimens.spaceLg))

        Slider(
            value = state.bpm.toFloat(),
            onValueChange = { onBpmChange(it.toInt()) },
            valueRange = state.minBpm.toFloat()..state.maxBpm.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = "Tempo ${state.bpm} BPM" },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            SecondaryButton(
                text = "-1",
                onClick = { onNudgeBpm(-1) },
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "-5",
                onClick = { onNudgeBpm(-5) },
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "+5",
                onClick = { onNudgeBpm(5) },
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "+1",
                onClick = { onNudgeBpm(1) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(Dimens.spaceLg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            PrimaryButton(
                text = if (state.isRunning) "Stop" else "Start",
                onClick = onToggleRunning,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "Tap tempo",
                onClick = onTap,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TempoDisplay(bpm: Int) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = bpm.toString(),
            style = numericLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "BPM  -  4/4",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BeatIndicator(beatsPerBar: Int, currentBeat: Int, isRunning: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = if (isRunning) {
                    "Beat ${currentBeat + 1} of $beatsPerBar"
                } else {
                    "Metronome stopped"
                }
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (beat in 0 until beatsPerBar) {
            val active = isRunning && beat == currentBeat
            val isAccent = beat == 0
            val scale by animateFloatAsState(
                targetValue = if (active) 1f else 0.7f,
                label = "beatScale",
            )
            val color = when {
                active && isAccent -> MaterialTheme.colorScheme.primary
                active -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = Dimens.spaceSm)
                    .size(if (isAccent) Dimens.beatIndicatorAccentSize else Dimens.beatIndicatorSize)
                    .scale(scale)
                    .background(color, CircleShape),
            )
        }
    }
}

@Composable
private fun ErrorCard(error: MetronomeError) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = when (error) {
                MetronomeError.AUDIO_UNAVAILABLE ->
                    "Audio output is unavailable. Close other apps using audio and try again."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(Dimens.cardPadding),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MetronomeScreenPreview() {
    SaxTrainerTheme {
        MetronomeScreen(
            state = MetronomeUiState(bpm = 96, isRunning = true, currentBeat = 2),
            onToggleRunning = {},
            onBpmChange = {},
            onNudgeBpm = {},
            onTap = {},
            onBack = {},
        )
    }
}
