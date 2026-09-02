package com.example.saxtrainer.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.SelectableRow
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.settings.Instrument

/**
 * Global settings. Sections are independent, so a future setting (tuning reference, appearance,
 * audio input) is added as one more section without touching the existing ones.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onInstrumentSelected: (Instrument) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(title = "Settings", onBack = onBack, modifier = modifier) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SaxTrainerTheme {
        SettingsScreen(
            state = SettingsUiState(),
            onInstrumentSelected = {},
            onBack = {},
        )
    }
}
