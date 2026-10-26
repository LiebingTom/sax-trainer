package com.example.saxtrainer.feature.reedlogbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.saxtrainer.core.designsystem.component.ActionIconButton
import com.example.saxtrainer.core.designsystem.component.ConfirmDialog
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.core.designsystem.component.PrimaryButton
import com.example.saxtrainer.core.designsystem.component.ScreenScaffold
import com.example.saxtrainer.core.designsystem.component.SecondaryButton
import com.example.saxtrainer.core.designsystem.component.SectionHeader
import com.example.saxtrainer.core.designsystem.icon.Add
import com.example.saxtrainer.core.designsystem.icon.Delete
import com.example.saxtrainer.core.designsystem.icon.Edit
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.reed.Reed
import com.example.saxtrainer.core.reed.ReedCatalog
import com.example.saxtrainer.core.reed.ReedLogbook
import com.example.saxtrainer.core.reed.ReedSession
import com.example.saxtrainer.core.reed.ReedStatus
import com.example.saxtrainer.core.reed.needsNumberToReturn
import com.example.saxtrainer.core.reed.plusReed
import com.example.saxtrainer.core.reed.started
import com.example.saxtrainer.core.reed.stopped
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * One reed, stateless: what it is, where it is in its life, and every session played on it, the
 * newest first.
 *
 * Editing and deleting the reed sit in the title bar, being about the reed as a whole; the one
 * button at the foot moves it in or out of rotation. A session played without the clock running is
 * entered by hand from the + below the last one. Deleting the reed or a session is asked about
 * first - neither can be taken back. A reed coming back whose number another reed has taken in the
 * meantime asks which number it is to carry instead.
 */
@Composable
fun ReedDetailScreen(
    reed: Reed,
    logbook: ReedLogbook,
    now: Instant,
    zone: TimeZone,
    onEdit: () -> Unit,
    onSetStatus: (status: ReedStatus, number: Int?) -> Unit,
    onDeleteReed: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onAddSession: (start: Instant, end: Instant) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDeleteReed by remember { mutableStateOf(false) }
    var chooseNumber by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<ReedSession?>(null) }
    var addingSession by remember { mutableStateOf(false) }
    val sessions = logbook.sessionsOf(reed.id)

    ScreenScaffold(
        title = ReedText.titleOf(reed),
        onBack = onBack,
        modifier = modifier,
        titleActions = {
            ActionIconButton(IconSource.Vector(SaxTrainerIcons.Edit), "Edit ${ReedText.titleOf(reed)}", onEdit)
            ActionIconButton(
                IconSource.Vector(SaxTrainerIcons.Delete),
                "Delete ${ReedText.titleOf(reed)}",
                { confirmDeleteReed = true },
            )
        },
        actions = {
            if (reed.isActive) {
                SecondaryButton(
                    text = "Put aside",
                    onClick = { onSetStatus(ReedStatus.INACTIVE, null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PrimaryButton(
                    text = "Back in rotation",
                    onClick = {
                        if (logbook.needsNumberToReturn(reed.id)) chooseNumber = true
                        else onSetStatus(ReedStatus.ACTIVE, null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        verticalArrangement = Arrangement.Top,
    ) {
        SectionHeader("Reed")
        RowsCard {
            Property(if (reed.isActive) "Number" else "Was number", ReedText.numberOf(reed))
            Property("Manufacturer", ReedCatalog.manufacturerNameOf(reed.manufacturerId))
            Property("Product", ReedCatalog.productNameOf(reed.productId))
            Property("Strength", reed.strength)
            Property("Price", reed.priceCents?.let(ReedText::priceOf) ?: "Not given")
            Property("Played", ReedText.playedOf(logbook.playedTime(reed.id, now)))
            Property("Status", ReedText.statusOf(reed.status))
            Property("Added", "${ReedText.dayOf(reed.createdAt, zone)}, ${ReedText.timeOf(reed.createdAt, zone)}")
            reed.statusChangedAt?.let { changed ->
                Property(
                    "${ReedText.statusOf(reed.status)} since",
                    "${ReedText.dayOf(changed, zone)}, ${ReedText.timeOf(changed, zone)}",
                )
            }
        }
        Spacer(Modifier.height(Dimens.spaceLg))

        SectionHeader("Sessions")
        RowsCard {
            if (sessions.isEmpty()) {
                Text(
                    text = "Not played yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Dimens.spaceXs),
                )
            }
            sessions.forEachIndexed { index, session ->
                // A quiet rule between sessions, so a long list reads as one row per session.
                if (index > 0) SessionDivider()
                SessionRow(
                    session = session,
                    now = now,
                    zone = zone,
                    onDelete = { sessionToDelete = session },
                )
            }
            SessionDivider()
            // Below the last session: one played without the clock running, entered by hand.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                ActionIconButton(
                    icon = IconSource.Vector(SaxTrainerIcons.Add),
                    contentDescription = "Add a session on ${ReedText.titleOf(reed)} by hand",
                    onClick = { addingSession = true },
                )
            }
        }
        Spacer(Modifier.height(Dimens.spaceLg))
    }

    if (confirmDeleteReed) {
        val count = sessions.size
        ConfirmDialog(
            title = "Delete ${ReedText.titleOf(reed)}?",
            text = "The reed and " + (if (count == 1) "its 1 session are" else "all $count of its sessions are") +
                " deleted for good. To keep its history, put it aside instead.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDeleteReed = false
                onDeleteReed()
            },
            onCancel = { confirmDeleteReed = false },
        )
    }
    if (chooseNumber) {
        NumberChoiceDialog(
            reed = reed,
            numbers = logbook.freeNumbers(),
            onChoose = { number ->
                chooseNumber = false
                onSetStatus(ReedStatus.ACTIVE, number)
            },
            onCancel = { chooseNumber = false },
        )
    }
    if (addingSession) {
        AddSessionDialog(
            reed = reed,
            logbook = logbook,
            now = now,
            zone = zone,
            onAdd = { start, end ->
                addingSession = false
                onAddSession(start, end)
            },
            onCancel = { addingSession = false },
        )
    }
    sessionToDelete?.let { session ->
        ConfirmDialog(
            title = "Delete this session?",
            text = "${ReedText.dayOf(session.start, zone)}, ${ReedText.timeOf(session.start, zone)} · " +
                "${ReedText.lengthOf(session.durationAt(now))}. It cannot be brought back.",
            confirmLabel = "Delete",
            onConfirm = {
                sessionToDelete = null
                onDeleteSession(session.id)
            },
            onCancel = { sessionToDelete = null },
        )
    }
}

/**
 * Which number a reed coming back into rotation is to carry, its own being taken: the free ones,
 * the lowest first. It will want writing on the reed again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberChoiceDialog(
    reed: Reed,
    numbers: List<Int>,
    onChoose: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    var chosen by remember(numbers) { mutableStateOf(numbers.first()) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("A new number for this reed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                Text(
                    "No. ${reed.number} belongs to another reed in rotation now. Choose the number " +
                        "you write on this one.",
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                    numbers.forEach { number ->
                        FilterChip(
                            selected = number == chosen,
                            onClick = { chosen = number },
                            label = { Text("No. $number") },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(chosen) }) { Text("Back in rotation") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun Property(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.spaceXs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SessionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = Dimens.spaceXs),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** "Mon 5 Oct 2026 · 14:02 · 42 min", and its delete button. */
@Composable
private fun SessionRow(session: ReedSession, now: Instant, zone: TimeZone, onDelete: () -> Unit) {
    val length = if (session.isRunning) "running" else ReedText.lengthOf(session.durationAt(now))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${ReedText.dayOf(session.start, zone)} · ${ReedText.timeOf(session.start, zone)} · $length",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        ActionIconButton(
            icon = IconSource.Vector(SaxTrainerIcons.Delete),
            contentDescription = "Delete the session of ${ReedText.dayOf(session.start, zone)}, " +
                ReedText.timeOf(session.start, zone),
            onClick = onDelete,
            // A running session is stopped, not deleted from under the clock.
            enabled = !session.isRunning,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReedDetailScreenPreview() {
    val t0 = Instant.parse("2026-10-05T14:00:00Z")
    val logbook = ReedLogbook()
        .plusReed("a", 1, "daddario", "daddario-royal", "2.5", t0)
        .started("s", "a", t0)
        .stopped(t0 + 42.minutes)
    SaxTrainerTheme {
        ReedDetailScreen(
            reed = logbook.reeds.single(),
            logbook = logbook,
            now = t0 + 60.minutes,
            zone = TimeZone.UTC,
            onEdit = {},
            onSetStatus = { _, _ -> },
            onDeleteReed = {},
            onDeleteSession = {},
            onAddSession = { _, _ -> },
            onBack = {},
        )
    }
}
