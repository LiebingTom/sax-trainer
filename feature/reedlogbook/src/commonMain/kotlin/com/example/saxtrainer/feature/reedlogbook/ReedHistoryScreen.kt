package com.example.saxtrainer.feature.reedlogbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.example.saxtrainer.core.designsystem.component.FeatureCard
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.icon.Reeds
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.reed.ReedLogbook
import kotlin.time.Instant

/**
 * Every reed there has been, stateless, the newest first.
 *
 * The reeds no longer in rotation are drawn faded but open as readily as the others: a broken reed's
 * sessions are the whole point of keeping it.
 */
@Composable
fun ReedHistoryScreen(
    logbook: ReedLogbook,
    now: Instant,
    onOpenReed: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        title = "Every reed",
        onBack = onBack,
        modifier = modifier,
        header = ScreenHeader.Description("Every reed you added, the newest first. Open one for its sessions."),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        val reeds = logbook.reedsNewestFirst
        if (reeds.isEmpty()) {
            Text(
                text = "No reeds yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        reeds.forEach { reed ->
            FeatureCard(
                title = ReedText.titleOf(reed),
                description = reedLines(reed, logbook, now),
                icon = IconSource.Vector(SaxTrainerIcons.Reeds),
                onClick = { onOpenReed(reed.id) },
                status = ReedText.statusOf(reed.status),
                modifier = if (reed.isActive) Modifier else Modifier.alpha(RetiredAlpha),
            )
        }
    }
}

/** How faded a reed out of rotation is drawn: readable, but plainly not one of the current ones. */
private const val RetiredAlpha = 0.55f
