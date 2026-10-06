package com.example.saxtrainer.feature.reedlogbook

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import com.example.saxtrainer.core.designsystem.theme.Dimens
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay

/**
 * The time now, moving on every second while [ticking] - so a running session's clock counts up -
 * and standing still otherwise, so a screen with nothing running is not redrawn for nothing.
 *
 * Only ever what is shown: every duration is worked out from a session's stored start, never added
 * up from these ticks.
 */
@Composable
internal fun rememberNow(ticking: Boolean, clock: () -> Instant = { Clock.System.now() }): Instant {
    var now by remember { mutableStateOf(clock()) }
    LaunchedEffect(ticking) {
        now = clock()
        while (ticking) {
            delay(1_000 - now.toEpochMilliseconds() % 1_000)
            now = clock()
        }
    }
    return now
}

/** A settings-style card: rows of one kind, grouped under a heading the caller draws above it. */
@Composable
internal fun RowsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(Dimens.cardPadding), content = content)
    }
}

/**
 * Asks once for leave to show the running session's notification, where the platform needs that
 * asked - Android 13 and later. Starting a session works either way; without leave it simply has
 * no notification.
 */
@Composable
expect fun rememberNotificationPermissionAsker(): () -> Unit
