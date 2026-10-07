package com.example.saxtrainer.feature.reedlogbook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.saxtrainer.core.designsystem.component.rememberTextFileSaver
import com.example.saxtrainer.core.reed.ReedLogbookCsv
import com.example.saxtrainer.core.reed.ReedLogbookRepository
import com.example.saxtrainer.core.reed.ReedSessions
import kotlin.time.Clock
import kotlinx.datetime.TimeZone

/*
 * The stateful entry points of the reed logbook. The app navigates to these and supplies only the
 * logbook's store, its session logic and where each button leads, so the feature stays independent
 * of the navigation implementation.
 */

/** The logbook itself: the reeds in rotation, a session to start or stop, the export. */
@Composable
fun ReedLogbookRoute(
    repository: ReedLogbookRepository,
    sessions: ReedSessions,
    onAdd: () -> Unit,
    onHistory: () -> Unit,
    onOpenReed: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: ((Boolean) -> Unit)? = null,
    viewModel: ReedLogbookViewModel = viewModel { ReedLogbookViewModel(repository, sessions) },
) {
    val logbook by viewModel.logbook.collectAsStateWithLifecycle()
    val now = rememberNow(ticking = logbook.runningSession != null)
    val zone = remember { TimeZone.currentSystemDefault() }
    val saver = rememberTextFileSaver()
    val askForNotifications = rememberNotificationPermissionAsker()

    ReedLogbookScreen(
        logbook = logbook,
        now = now,
        onSelect = viewModel::select,
        onStart = {
            askForNotifications()
            viewModel.start()
        },
        onStop = viewModel::stop,
        onAdd = onAdd,
        onHistory = onHistory,
        onExport = {
            val today = ReedText.isoDayOf(Clock.System.now(), zone)
            saver.save(ReedLogbookCsv.fileNameFor(today), "text/csv", viewModel.csv())
        },
        onOpenReed = onOpenReed,
        onBack = onBack,
        modifier = modifier,
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
    )
}

/** Every reed there has been. */
@Composable
fun ReedHistoryRoute(
    repository: ReedLogbookRepository,
    onOpenReed: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logbook by repository.logbook.collectAsStateWithLifecycle()
    val now = rememberNow(ticking = false)
    ReedHistoryScreen(logbook = logbook, now = now, onOpenReed = onOpenReed, onBack = onBack, modifier = modifier)
}

/** One reed and its sessions. Leaves by [onBack] when there is no such reed, or no longer is. */
@Composable
fun ReedDetailRoute(
    repository: ReedLogbookRepository,
    sessions: ReedSessions,
    reedId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReedLogbookViewModel = viewModel { ReedLogbookViewModel(repository, sessions) },
) {
    val logbook by viewModel.logbook.collectAsStateWithLifecycle()
    val reed = logbook.reedOf(reedId)
    if (reed == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val now = rememberNow(ticking = logbook.runningSession?.reedId == reedId)
    val zone = remember { TimeZone.currentSystemDefault() }

    ReedDetailScreen(
        reed = reed,
        logbook = logbook,
        now = now,
        zone = zone,
        onEdit = onEdit,
        onSetStatus = { status, number -> viewModel.setStatus(reedId, status, number) },
        // Gone from the logbook, the reed's page leaves by itself - see the check above.
        onDeleteReed = { viewModel.deleteReed(reedId) },
        onDeleteSession = viewModel::deleteSession,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Adding a reed ([reedId] null) or editing one. [onSaved] is told the reed's id. */
@Composable
fun ReedEditRoute(
    repository: ReedLogbookRepository,
    reedId: String?,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReedEditViewModel = viewModel { ReedEditViewModel(repository, reedId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReedEditScreen(
        state = state,
        onManufacturer = viewModel::selectManufacturer,
        onProduct = viewModel::selectProduct,
        onStrength = viewModel::selectStrength,
        onNumber = viewModel::selectNumber,
        onPrice = viewModel::typePrice,
        onSave = { viewModel.save()?.let(onSaved) },
        onBack = onBack,
        modifier = modifier,
    )
}
