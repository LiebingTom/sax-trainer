package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.example.saxtrainer.core.designsystem.theme.Dimens

/**
 * A tool as a square: its icon on the card's colour, with the card's corners, and nothing written.
 *
 * For a place where the player already knows the tools by sight - the favorites on the landing page -
 * so the words a [FeatureCard] carries would only take up room. [contentDescription] is what a screen
 * reader says in their place, and is required for that reason.
 */
@Composable
fun FeatureTile(
    icon: IconSource,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1f)
            .semantics { this.contentDescription = contentDescription },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FeatureIcon(icon = icon)
        }
    }
}

/**
 * [items] as rows of equal squares that fill the width and wrap onto a new row when it is full.
 *
 * As many columns as fit at [minTileSize] or larger, then every tile grows to share out what is
 * left, so a row always reaches both edges. A last row that is not full keeps the same tile size and
 * starts at the left, rather than stretching its few tiles across the screen.
 */
@Composable
fun <T> TileGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    minTileSize: Dp = Dimens.tileMinSize,
    spacing: Dp = Dimens.spaceSm,
    tile: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = ((maxWidth + spacing) / (minTileSize + spacing)).toInt().coerceAtLeast(1)

        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { item -> tile(item, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
