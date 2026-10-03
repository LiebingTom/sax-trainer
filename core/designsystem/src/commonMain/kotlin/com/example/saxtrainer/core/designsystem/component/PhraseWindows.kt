package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.saxtrainer.core.designsystem.icon.Preview as PreviewIcon
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.phrase.Phrase
import com.example.saxtrainer.core.phrase.PhraseRecipe
import com.example.saxtrainer.core.phrase.ScaleChoice

/**
 * The two windows a phrase is chosen in - the library of written-out phrases, and the scale
 * patterns that become phrases once a scale and octaves are picked.
 *
 * Shared, so every place that offers a phrase offers it the same way: the phrase trainer and quiz
 * choose what to practise in them, and the note picker takes the notes of whatever is chosen.
 */

/** Simply lists the phrases in the library. No editing happens here. */
@Composable
fun PhraseLibraryWindow(
    phrases: List<Phrase>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var previewing by remember { mutableStateOf<Phrase?>(null) }

    FullScreenWindow(title = "Phrase Library", onDismiss = onDismiss) {
        if (phrases.isEmpty()) {
            Text(
                text = "No phrases yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        phrases.forEach { phrase ->
            SelectableCard(
                title = phrase.name,
                supportingText = phrase.description,
                selected = phrase.id == selectedId,
                onClick = { onSelect(phrase.id) },
                trailing = { PreviewButton(name = phrase.name, onClick = { previewing = phrase }) },
            )
        }
    }

    previewing?.let { phrase ->
        NotesPreviewDialog(title = phrase.name, sequence = phrase.toneSequence(), onDismiss = { previewing = null })
    }
}

/**
 * Scale, octaves, then the stored patterns to choose between - the pattern is what closes the
 * window; changing the scale or the octaves just changes what the same list of patterns will sound
 * like. Scale and octaves are each a closed field: tapping one opens a dialog over this window
 * rather than crowding the list with a slider or a row of chips.
 */
@Composable
fun ScalePatternWindow(
    patterns: List<PhraseRecipe>,
    selectedPatternId: String?,
    scaleChoices: List<ScaleChoice>,
    scaleChoiceId: String,
    octaves: Set<Int>,
    octaveRange: IntRange,
    onSelectScaleChoice: (String) -> Unit,
    onToggleOctave: (Int) -> Unit,
    onSelectPattern: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var previewing by remember { mutableStateOf<PhraseRecipe?>(null) }
    var scaleDialogOpen by remember { mutableStateOf(false) }
    var octaveDialogOpen by remember { mutableStateOf(false) }
    val scaleChoice = scaleChoices.firstOrNull { it.id == scaleChoiceId }

    FullScreenWindow(title = "Scale Pattern", onDismiss = onDismiss) {
        SectionHeader("Scale")
        FieldRow(
            label = "Scale",
            value = scaleChoice?.displayName ?: "Choose a scale",
            onClick = { scaleDialogOpen = true },
        )

        Spacer(Modifier.height(Dimens.spaceSm))
        FieldRow(
            label = "Octaves",
            value = octaveSummary(octaves),
            onClick = { octaveDialogOpen = true },
        )

        Spacer(Modifier.height(Dimens.spaceSm))
        SectionHeader("Pattern")
        if (patterns.isEmpty()) {
            Text(
                text = "No patterns yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        patterns.forEach { pattern ->
            SelectableCard(
                title = pattern.name,
                supportingText = pattern.description,
                selected = pattern.id == selectedPatternId,
                onClick = { onSelectPattern(pattern.id) },
                trailing = { PreviewButton(name = pattern.name, onClick = { previewing = pattern }) },
            )
        }
    }

    if (scaleDialogOpen) {
        ScaleChoiceDialog(
            choices = scaleChoices,
            selectedId = scaleChoiceId,
            onSelect = onSelectScaleChoice,
            onDismiss = { scaleDialogOpen = false },
        )
    }
    if (octaveDialogOpen) {
        OctaveChoiceDialog(
            octaves = octaveRange.toList(),
            chosen = octaves,
            root = scaleChoice?.root,
            onToggle = onToggleOctave,
            onDismiss = { octaveDialogOpen = false },
        )
    }

    // Generated from whichever scale and octaves are chosen right now, in this window - not the
    // pattern's own defaults, since it has none: the notes shown are the ones that would actually
    // be practised.
    val previewedPattern = previewing
    if (previewedPattern != null && scaleChoice != null && octaves.isNotEmpty()) {
        val resolved = previewedPattern.resolve(scaleChoice.scale, scaleChoice.root, octaves)
        NotesPreviewDialog(title = resolved.name, sequence = resolved.toneSequence(), onDismiss = { previewing = null })
    }
}

/** "Choose octaves" while nothing is picked, otherwise the chosen ones in order, lowest first. */
fun octaveSummary(octaves: Set<Int>): String =
    octaves.sorted().joinToString(", ").ifEmpty { "Choose octaves" }

/** The trailing preview button every phrase or pattern card offers - see [NotesPreviewDialog]. */
@Composable
private fun PreviewButton(name: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        InlineIcon(IconSource.Vector(SaxTrainerIcons.PreviewIcon), contentDescription = "Preview $name")
    }
}

/** Which scale a pattern is played on - single choice, so picking one closes the dialog. */
@Composable
private fun ScaleChoiceDialog(
    choices: List<ScaleChoice>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a scale") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    SelectableRow(
                        title = choice.displayName,
                        selected = choice.id == selectedId,
                        onClick = {
                            onSelect(choice.id)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * Which octaves a pattern is generated in - any number of them, so every tap stays open: closing is
 * its own, separate action, the same as the note quiz's own custom-notes dialog.
 */
@Composable
private fun OctaveChoiceDialog(
    octaves: List<Int>,
    chosen: Set<Int>,
    root: NoteName?,
    onToggle: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose octaves") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                octaves.forEach { octave ->
                    CheckableRow(
                        title = "Octave $octave",
                        supportingText = octaveHint(octave, root),
                        checked = octave in chosen,
                        onCheckedChange = { onToggle(octave) },
                    )
                }
            }
        },
        confirmButton = {
            // Nothing to generate from is not a choice, so Done waits for at least one octave.
            TextButton(onClick = onDismiss, enabled = chosen.isNotEmpty()) { Text("Done") }
        },
    )
}

/**
 * What octave [octave] actually means for [root]: the root note it starts on, so "octave 4" is
 * never just a bare number, and a flag where that note falls outside what a saxophone can write -
 * see [SaxWrittenRange].
 */
private fun octaveHint(octave: Int, root: NoteName?): String {
    if (root == null) return "the root note in this octave"
    val note = Note(root, octave)
    val label = "${root.displayName}$octave"
    return when {
        note.midiNumber < SaxWrittenRange.LOWEST.midiNumber -> "starts on $label - below the saxophone's range"
        note.midiNumber > SaxWrittenRange.HIGHEST.midiNumber -> "starts on $label - altissimo, needs years of practice"
        else -> "starts on $label"
    }
}

/**
 * The written range every saxophone shares - low Bb up to high F#, no altissimo - the same across
 * soprano, alto, tenor and baritone, since a fingering chart is written pitch.
 */
private object SaxWrittenRange {
    val LOWEST = Note(NoteName.A_SHARP, 3)
    val HIGHEST = Note(NoteName.F_SHARP, 6)
}
