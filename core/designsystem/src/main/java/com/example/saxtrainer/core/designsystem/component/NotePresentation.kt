package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.music.Note

/**
 * Shows a note as its name, as notation, or as both.
 *
 * Which of the two appears is passed in as plain booleans rather than as a settings type, so the
 * design system stays free of app configuration while every screen honours the same preference.
 */
@Composable
fun NotePresentation(
    note: Note,
    label: String,
    showName: Boolean,
    showStaff: Boolean,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    staffColor: Color = color,
    nameStyle: TextStyle = MaterialTheme.typography.displaySmall,
    staffLineSpacing: Dp = DefaultStaffSpacing,
    secondaryNote: Note? = null,
    secondaryColor: Color = staffColor,
) {
    // A preference that hides both would leave nothing on screen; the name is the safer fallback.
    val name = showName || !showStaff

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        if (name) {
            Text(text = label, style = nameStyle, color = color)
        }
        if (showStaff) {
            MusicalNoteView(
                primaryNote = note,
                primaryColor = staffColor,
                secondaryNote = secondaryNote,
                secondaryColor = secondaryColor,
                lineSpacing = staffLineSpacing,
            )
        }
    }
}

private val DefaultStaffSpacing = 10.dp
