package com.example.saxtrainer.feature.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.saxtrainer.core.designsystem.theme.Dimens

/**
 * One lesson, as a tile.
 *
 * Deliberately knows nothing about where the lesson came from: the curriculum's lessons and the
 * practices the player saved for themselves are the same thing to a reader, and drawing them with
 * two different cards would be the first step towards two different lesson systems. What differs is
 * what can be done with one, and that arrives as [trailing] - an Edit button, or the controls that
 * appear while saved lessons are being managed.
 *
 * A plain title and subtitle rather than a [LessonItem], because a saved practice has no progress to
 * report and inventing one to draw it would be a lie.
 */
@Composable
fun LessonCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** A line under the subtitle: what has happened to this lesson, or what it will ask for. */
    status: String? = null,
    /** Drawn at the far end of the row, after [trailing]. Done, Locked, or nothing at all. */
    badge: (@Composable () -> Unit)? = null,
    /** Controls of the card's own, clickable in their own right rather than opening the lesson. */
    trailing: (@Composable () -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Dimens.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
            if (badge != null) {
                badge()
            }
        }
    }
}
