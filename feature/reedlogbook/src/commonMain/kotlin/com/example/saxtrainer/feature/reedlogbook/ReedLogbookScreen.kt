package com.example.saxtrainer.feature.reedlogbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.saxtrainer.core.designsystem.component.ActionIconButton
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.core.designsystem.component.PracticeTitleActions
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.ScreenHeader
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.component.SelectableCard
import com.example.saxtrainer.core.designsystem.icon.Add
import com.example.saxtrainer.core.designsystem.icon.Export
import com.example.saxtrainer.core.designsystem.icon.History
import com.example.saxtrainer.core.designsystem.icon.Info
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.reed.Reed
import com.example.saxtrainer.core.reed.ReedLogbook
import com.example.saxtrainer.core.reed.ReedSession
import com.example.saxtrainer.core.reed.plusReed
import com.example.saxtrainer.core.reed.started
import kotlin.time.Instant

/**
 * The reed logbook, stateless: the reeds in rotation to choose from, and a session to start or stop
 * on the chosen one.
 *
 * While a session runs, the reed being played and how long it has been are pinned at the top, and
 * the choice of reed waits until it stops.
 */
@Composable
fun ReedLogbookScreen(
    logbook: ReedLogbook,
    now: Instant,
    onSelect: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAdd: () -> Unit,
    onHistory: () -> Unit,
    onExport: () -> Unit,
    onOpenReed: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: ((Boolean) -> Unit)? = null,
) {
    val running = logbook.runningSession
    val selected = logbook.effectiveSelection()

    ScreenScaffold(
        title = TITLE,
        onBack = onBack,
        modifier = modifier,
        titleActions = {
            ActionIconButton(IconSource.Vector(SaxTrainerIcons.Add), "Add a reed", onAdd)
            ActionIconButton(IconSource.Vector(SaxTrainerIcons.History), "Every reed so far", onHistory)
            ActionIconButton(IconSource.Vector(SaxTrainerIcons.Export), "Export the logbook as CSV", onExport)
            PracticeTitleActions(TITLE, isFavorite, onToggleFavorite, null)
        },
        header = if (running == null) ScreenHeader.Description("Choose the reed on your mouthpiece, then start.") else null,
        subject = if (running != null) {
            { RunningSession(logbook, running, now) }
        } else {
            null
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                PrimaryButton(
                    text = "Start session",
                    onClick = onStart,
                    enabled = logbook.canStart,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "Stop session",
                    onClick = onStop,
                    enabled = running != null,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        SectionHeader("Reeds in rotation")
        val reeds = logbook.activeReeds
        if (reeds.isEmpty()) {
            Text(
                text = "No reeds yet. Add the reeds you play, each under the number you write on it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(text = "Add a reed", onClick = onAdd, modifier = Modifier.fillMaxWidth())
        }
        reeds.forEach { reed ->
            ReedCard(
                reed = reed,
                logbook = logbook,
                now = now,
                selected = reed.id == selected?.id,
                onClick = { if (running == null) onSelect(reed.id) },
                onOpen = { onOpenReed(reed.id) },
            )
        }
    }
}

/** The reed being played and how long for, pinned at the top while a session runs. */
@Composable
private fun RunningSession(logbook: ReedLogbook, session: ReedSession, now: Instant) {
    val reed = logbook.reedOf(session.reedId)
    Text(
        text = reed?.let { "Playing ${ReedText.numberOf(it)}" } ?: "Playing",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (reed != null) {
        Text(
            text = ReedText.describe(reed),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text(
        text = ReedText.clockOf(session.durationAt(now)),
        style = MaterialTheme.typography.displaySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * One reed in rotation, in three lines: its number, then make, model and strength, then how long it
 * has been played - which is what tells reeds of one box apart in wear.
 */
@Composable
private fun ReedCard(
    reed: Reed,
    logbook: ReedLogbook,
    now: Instant,
    selected: Boolean,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SelectableCard(
        title = ReedText.titleOf(reed),
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        supportingText = reedLines(reed, logbook, now),
        trailing = {
            ActionIconButton(
                icon = IconSource.Vector(SaxTrainerIcons.Info),
                contentDescription = "Details of reed ${reed.number}",
                onClick = onOpen,
            )
        },
    )
}

/** The two lines under a reed's number, wherever a reed is listed: what it is, and how worn. */
internal fun reedLines(reed: Reed, logbook: ReedLogbook, now: Instant): String =
    ReedText.describe(reed) + "\n" + ReedText.playedOf(logbook.playedTime(reed.id, now))

internal const val TITLE = "Reed Logbook"

@Preview(showBackground = true)
@Composable
private fun ReedLogbookScreenPreview() {
    val t0 = Instant.parse("2026-10-05T14:00:00Z")
    val logbook = ReedLogbook()
        .plusReed("a", 1, "daddario", "daddario-royal", "2.5", t0)
        .plusReed("b", 2, "daddario", "daddario-royal", "2.5", t0)
        .started("s", "a", t0)
    SaxTrainerTheme {
        ReedLogbookScreen(
            logbook = logbook,
            now = Instant.parse("2026-10-05T14:42:13Z"),
            onSelect = {},
            onStart = {},
            onStop = {},
            onAdd = {},
            onHistory = {},
            onExport = {},
            onOpenReed = {},
            onBack = {},
        )
    }
}
