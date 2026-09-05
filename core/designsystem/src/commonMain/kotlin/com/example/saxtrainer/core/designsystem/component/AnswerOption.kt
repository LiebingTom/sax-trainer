package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.FeedbackColors
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName

/** Whether an answer has been judged yet, and how it turned out. */
enum class AnswerState { NEUTRAL, CORRECT, INCORRECT }

/**
 * One tappable answer in a multiple-choice question.
 *
 * The thing being chosen - a note on a staff, a fingering diagram - stays a plain drawing that knows
 * nothing about being an answer, so this wraps it rather than any of them growing an `onClick`. It
 * also owns the accessibility label: `MusicalNoteView` replaces its own subtree's semantics, so a
 * description set inside would never be read.
 */
@Composable
fun AnswerOption(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: AnswerState = AnswerState.NEUTRAL,
    selected: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val outline = when (state) {
        AnswerState.CORRECT -> FeedbackColors.correct
        AnswerState.INCORRECT -> FeedbackColors.incorrect
        AnswerState.NEUTRAL ->
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    }
    val judged = state != AnswerState.NEUTRAL

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(
            if (contentDescription == null) {
                Modifier
            } else {
                Modifier.semantics { this.contentDescription = contentDescription }
            },
        ),
        shape = MaterialTheme.shapes.medium,
        color = if (judged) outline.copy(alpha = JudgedFill) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (judged || selected) StrongBorder else Border, outline),
    ) {
        Box(
            modifier = Modifier.padding(Dimens.spaceSm),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }
}

private val Border = 1.dp
private val StrongBorder = 2.dp
private const val JudgedFill = 0.12f

@Preview(showBackground = true)
@Composable
private fun AnswerOptionPreview() {
    SaxTrainerTheme {
        Row(modifier = Modifier.padding(Dimens.spaceMd)) {
            listOf(AnswerState.NEUTRAL, AnswerState.CORRECT, AnswerState.INCORRECT)
                .forEachIndexed { index, state ->
                    AnswerOption(
                        onClick = {},
                        modifier = Modifier.padding(end = Dimens.spaceSm),
                        state = state,
                    ) {
                        MusicalNoteView(
                            primaryNote = Note(NoteName.C, 4 + index % 2),
                            lineSpacing = 8.dp,
                        )
                    }
                }
        }
    }
}
