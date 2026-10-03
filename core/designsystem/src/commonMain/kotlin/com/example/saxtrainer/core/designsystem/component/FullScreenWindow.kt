package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.saxtrainer.core.designsystem.icon.ArrowBack
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.theme.Dimens

/**
 * A full-screen window over a setup screen, for a choice too long for an inline list.
 *
 * Back - the arrow or the system gesture - calls [onDismiss]. [bottomBar] stays put below the
 * scrolling [content], for the actions that finish the choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenWindow(
    title: String,
    onDismiss: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            InlineIcon(IconSource.Vector(SaxTrainerIcons.ArrowBack), contentDescription = "Back")
                        }
                    },
                )
            },
            bottomBar = bottomBar,
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                content = content,
            )
        }
    }
}
