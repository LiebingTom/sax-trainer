package com.example.saxtrainer.feature.reedlogbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.SelectableRow
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.reed.ReedCatalog

/**
 * Adding a reed or describing one again, stateless: make, model, strength, and the number written
 * on it. Laid out like the settings, one choice per card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReedEditScreen(
    state: ReedEditUiState,
    onManufacturer: (String) -> Unit,
    onProduct: (String) -> Unit,
    onStrength: (String) -> Unit,
    onNumber: (Int) -> Unit,
    onPrice: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        title = if (state.isNew) "Add a reed" else "Edit reed",
        onBack = onBack,
        modifier = modifier,
        actions = {
            PrimaryButton(
                text = if (state.isNew) "Add reed" else "Save",
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        verticalArrangement = Arrangement.Top,
    ) {
        if (state.numbers.isNotEmpty()) {
            SectionHeader("Number")
            Hint("What you write on the reed. Two reeds in rotation never share one.")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                state.numbers.forEach { number ->
                    FilterChip(
                        selected = number == state.draft.number,
                        onClick = { onNumber(number) },
                        label = { Text("$number") },
                    )
                }
            }
            Spacer(Modifier.height(Dimens.spaceLg))
        }

        SectionHeader("Manufacturer")
        RowsCard {
            state.manufacturers.forEach { maker ->
                SelectableRow(
                    title = maker.name,
                    selected = maker.id == state.draft.manufacturerId,
                    onClick = { onManufacturer(maker.id) },
                )
            }
        }
        Spacer(Modifier.height(Dimens.spaceLg))

        SectionHeader("Product")
        RowsCard {
            state.products.forEach { product ->
                SelectableRow(
                    title = product.name,
                    selected = product.id == state.draft.productId,
                    onClick = { onProduct(product.id) },
                )
            }
        }
        Spacer(Modifier.height(Dimens.spaceLg))

        SectionHeader("Strength")
        Hint("As printed on the box, on ${ReedCatalog.productNameOf(state.draft.productId)}'s own scale.")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            state.strengths.forEach { strength ->
                FilterChip(
                    selected = strength == state.draft.strength,
                    onClick = { onStrength(strength) },
                    label = { Text(strength) },
                )
            }
        }
        Spacer(Modifier.height(Dimens.spaceLg))

        SectionHeader("Price")
        Hint("What the reed cost, if you want to keep track. Leave it empty otherwise.")
        OutlinedTextField(
            value = state.draft.price,
            onValueChange = onPrice,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Price in €") },
            placeholder = { Text("4.50") },
            isError = !state.isPriceValid,
            supportingText = if (state.isPriceValid) null else { { Text("A price like 4.50 or 4,50") } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.height(Dimens.spaceLg))
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Dimens.spaceSm))
}

@Preview(showBackground = true)
@Composable
private fun ReedEditScreenPreview() {
    SaxTrainerTheme {
        ReedEditScreen(
            state = ReedEditUiState(
                isNew = true,
                draft = ReedDraft("daddario", "daddario-royal", "2.5", 1),
                numbers = listOf(1, 4),
            ),
            onManufacturer = {},
            onProduct = {},
            onStrength = {},
            onNumber = {},
            onPrice = {},
            onSave = {},
            onBack = {},
        )
    }
}
