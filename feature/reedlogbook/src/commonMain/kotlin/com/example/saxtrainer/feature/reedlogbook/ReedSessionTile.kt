package com.example.saxtrainer.feature.reedlogbook

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.core.designsystem.component.InlineIcon
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.icon.Reeds
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.reed.ReedLogbook
import com.example.saxtrainer.core.reed.ReedLogbookRepository
import com.example.saxtrainer.core.reed.ReedSessions
import com.example.saxtrainer.core.reed.plusReed
import com.example.saxtrainer.core.reed.started
import kotlin.time.Instant

/**
 * The reed session on the landing page, stateful: started, stopped and shown through the same store
 * and the same session logic as the logbook, so the two never disagree.
 */
@Composable
fun ReedSessionTileRoute(
    repository: ReedLogbookRepository,
    sessions: ReedSessions,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logbook by repository.logbook.collectAsStateWithLifecycle()
    val now = rememberNow(ticking = logbook.runningSession != null)
    val askForNotifications = rememberNotificationPermissionAsker()
    ReedSessionTile(
        logbook = logbook,
        now = now,
        onStart = {
            askForNotifications()
            sessions.start()
        },
        onStop = sessions::stop,
        onOpen = onOpen,
        onAdd = onAdd,
        modifier = modifier,
    )
}

/**
 * The reed session on the landing page, stateless: a brass card, the width of the page.
 *
 * Running, it says which reed and for how long, with Stop. Otherwise it offers to start on the reed
 * selected in the logbook, saying how long that reed has been played - or, with no reed yet, to add
 * one. Tapping the card itself opens the
 * logbook.
 */
@Composable
fun ReedSessionTile(
    logbook: ReedLogbook,
    now: Instant,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = logbook.runningSession
    val reed = running?.let { logbook.reedOf(it.reedId) } ?: logbook.effectiveSelection()

    // Three lines: whose number, what reed, and how long - this session's clock while it runs, the
    // reed's whole playing time while it waits.
    val first = when {
        reed == null -> TITLE
        running != null -> "Playing ${ReedText.numberOf(reed)}"
        else -> ReedText.titleOf(reed)
    }
    val second = reed?.let(ReedText::describe) ?: "No reed in rotation yet"
    val third = when {
        running != null -> ReedText.clockOf(running.durationAt(now))
        reed != null -> ReedText.playedOf(logbook.playedTime(reed.id, now))
        else -> null
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            // The brass behind every tool's icon on the landing page, so the tile reads as one of them.
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Open the reed logbook", role = Role.Button, onClick = onOpen)
                .padding(Dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            InlineIcon(icon = IconSource.Vector(SaxTrainerIcons.Reeds), contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = first, style = MaterialTheme.typography.titleMedium)
                Text(text = second, style = MaterialTheme.typography.bodyMedium)
                if (third != null) {
                    Text(
                        text = third,
                        style = if (running != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            val (label, action) = when {
                running != null -> "Stop" to onStop
                reed != null -> "Start" to onStart
                else -> "Add reed" to onAdd
            }
            // The app's own primary button, the same as Start session in the logbook.
            PrimaryButton(text = label, onClick = action)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReedSessionTilePreview() {
    val t0 = Instant.parse("2026-10-05T14:00:00Z")
    val logbook = ReedLogbook()
        .plusReed("a", 1, "daddario", "daddario-royal", "2.5", t0)
        .started("s", "a", t0)
    SaxTrainerTheme {
        ReedSessionTile(
            logbook = logbook,
            now = Instant.parse("2026-10-05T14:42:13Z"),
            onStart = {},
            onStop = {},
            onOpen = {},
            onAdd = {},
        )
    }
}
