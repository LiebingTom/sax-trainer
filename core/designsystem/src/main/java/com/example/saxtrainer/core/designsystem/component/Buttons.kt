package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.theme.Dimens

private val ButtonMinHeight = 52.dp

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: IconSource? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonMinHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = Dimens.spaceLg, vertical = Dimens.spaceSm),
        colors = ButtonDefaults.buttonColors(),
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: IconSource? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonMinHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = Dimens.spaceLg, vertical = Dimens.spaceSm),
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
private fun ButtonContent(text: String, icon: IconSource?) {
    Row {
        if (icon != null) {
            InlineIcon(icon)
            Spacer(Modifier.width(Dimens.spaceSm))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
