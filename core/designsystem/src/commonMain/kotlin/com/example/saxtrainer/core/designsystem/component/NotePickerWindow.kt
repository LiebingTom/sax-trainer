package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.saxtrainer.core.designsystem.icon.ChooseNotes
import com.example.saxtrainer.core.designsystem.icon.DropDown
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.fingering.NotePickerState
import com.example.saxtrainer.core.fingering.NoteSet
import com.example.saxtrainer.core.fingering.Register
import com.example.saxtrainer.core.fingering.SpellingKind
import com.example.saxtrainer.core.music.Key
import com.example.saxtrainer.core.music.NoteNaming
import com.example.saxtrainer.core.music.OctaveNumbering
import com.example.saxtrainer.core.phrase.Phrase
import com.example.saxtrainer.core.phrase.PhraseRecipe
import com.example.saxtrainer.core.phrase.ScaleChoice

/**
 * Picks the notes an exercise works on, full screen: ready-made sets, keys, phrases and scale
 * patterns to preselect from, filters to narrow the list, and every written note as a chip, one row
 * per octave.
 *
 * [state] is the caller's draft and every change comes back through [onChange] as a function of the
 * draft, so the rules live in [NotePickerState]; only which dialog or window is open is kept here.
 * Filters only hide notes - a chosen note that is hidden stays chosen, and the count above the
 * notes says how many are. Every preselection replaces the draft rather than adding to it.
 */
@Composable
fun NotePickerWindow(
    state: NotePickerState,
    naming: NoteNaming,
    octaves: OctaveNumbering,
    onChange: ((NotePickerState) -> NotePickerState) -> Unit,
    onStartFrom: (NoteSet) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    title: String = "Choose notes",
    presets: List<NoteSet> = NoteSet.entries - NoteSet.CUSTOM,
    /** Written-out phrases whose notes can be taken; none, and the button is not offered. */
    phrases: List<Phrase> = emptyList(),
    /** Scale patterns whose notes can be taken once a scale and octaves are picked. */
    patterns: List<PhraseRecipe> = emptyList(),
) {
    var choosingKey by remember { mutableStateOf(false) }
    var choosingPhrase by remember { mutableStateOf(false) }
    var choosingPattern by remember { mutableStateOf(false) }
    // The pattern window's own scale and octaves, kept while this window is open so reopening it
    // shows what was last used.
    var scaleChoiceId by remember { mutableStateOf(ScaleChoice.default?.id.orEmpty()) }
    var patternOctaves by remember { mutableStateOf(setOf(DEFAULT_PATTERN_OCTAVE)) }

    FullScreenWindow(
        title = title,
        onDismiss = onCancel,
        bottomBar = { ApplyBar(enabled = state.canApply, onApply = onApply) },
    ) {
        SectionHeader("Preselect")
        presets.forEach { set ->
            SecondaryButton(
                text = set.displayName,
                onClick = { onStartFrom(set) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Opens the list of keys; picking one fills in its notes straight away.
        SecondaryButton(
            text = "Key: ${state.key.nameIn(naming)}",
            onClick = { choosingKey = true },
            modifier = Modifier.fillMaxWidth(),
            icon = IconSource.Vector(SaxTrainerIcons.DropDown),
        )
        if (phrases.isNotEmpty()) {
            SecondaryButton(
                text = "Phrase library",
                onClick = { choosingPhrase = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (patterns.isNotEmpty()) {
            SecondaryButton(
                text = "Scale pattern",
                onClick = { choosingPattern = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionGap()
        SectionHeader("Filters")
        ChipRow {
            Register.entries.forEach { register ->
                FilterChip(
                    selected = register in state.filter.registers,
                    onClick = { onChange { it.toggleRegister(register) } },
                    label = { Text(register.displayName) },
                )
            }
        }
        ChipRow {
            SpellingKind.entries.forEach { kind ->
                FilterChip(
                    selected = kind in state.filter.spellings,
                    onClick = { onChange { it.toggleSpelling(kind) } },
                    label = { Text(kind.displayName) },
                )
            }
        }

        SectionGap()
        SectionHeader("Notes")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hidden = state.hiddenChosenCount
            Text(
                text = "${state.chosen.size} selected" + if (hidden > 0) " · $hidden hidden" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onChange { it.selectAllShown() } }) { Text("Select all") }
            TextButton(onClick = { onChange { it.clearShown() } }) { Text("Select none") }
        }
        val shown = state.shown
        if (shown.isEmpty()) {
            Hint("No notes match these filters.")
        }
        shown.groupBy { it.octave }.forEach { (octave, notes) ->
            Text(
                text = "Octave ${octaves.numberOf(octave)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow {
                notes.forEach { note ->
                    FilterChip(
                        selected = note in state.chosen,
                        onClick = { onChange { it.toggle(note) } },
                        label = { Text(naming.nameOf(note)) },
                    )
                }
            }
        }
    }

    if (choosingKey) {
        ChoiceDialog(
            title = "Key",
            options = Key.all,
            selected = state.key,
            label = { it.nameIn(naming) },
            onSelect = { key -> onChange { it.useKey(key) } },
            onDismiss = { choosingKey = false },
        )
    }
    if (choosingPhrase) {
        PhraseLibraryWindow(
            phrases = phrases,
            selectedId = null,
            onSelect = { id ->
                phrases.firstOrNull { it.id == id }?.let { phrase ->
                    onChange { it.useNotes(phrase.distinctWrittenNotes()) }
                }
                choosingPhrase = false
            },
            onDismiss = { choosingPhrase = false },
        )
    }
    if (choosingPattern) {
        ScalePatternWindow(
            patterns = patterns,
            selectedPatternId = null,
            scaleChoices = ScaleChoice.all,
            scaleChoiceId = scaleChoiceId,
            octaves = patternOctaves,
            octaveRange = PATTERN_OCTAVES,
            onSelectScaleChoice = { scaleChoiceId = it },
            onToggleOctave = { octave ->
                patternOctaves = if (octave in patternOctaves) patternOctaves - octave else patternOctaves + octave
            },
            onSelectPattern = { id ->
                val pattern = patterns.firstOrNull { it.id == id }
                val choice = ScaleChoice.byId(scaleChoiceId)
                if (pattern != null && choice != null && patternOctaves.isNotEmpty()) {
                    val phrase = pattern.resolve(choice.scale, choice.root, patternOctaves)
                    onChange { it.useNotes(phrase.distinctWrittenNotes()) }
                }
                choosingPattern = false
            },
            onDismiss = { choosingPattern = false },
        )
    }
}

/** The octaves a scale pattern can be built in, as the phrase trainer offers them. */
private val PATTERN_OCTAVES = 4..6
private const val DEFAULT_PATTERN_OCTAVE = 4

/** The one thing fixed below the list: Apply, across the whole width. */
@Composable
private fun ApplyBar(enabled: Boolean, onApply: () -> Unit) {
    Surface(tonalElevation = Dimens.spaceXs) {
        PrimaryButton(
            text = "Apply",
            onClick = onApply,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceSm),
        )
    }
}

/** The room between two sections, on top of the spacing every row already has. */
@Composable
private fun SectionGap() {
    Spacer(Modifier.height(Dimens.spaceSm))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) { content() }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One choice out of a list, applied as soon as it is tapped. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    SelectableRow(
                        title = label(option),
                        selected = option == selected,
                        onClick = {
                            onSelect(option)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The notes an exercise will use, said plainly - the set's name, how many notes and from where to
 * where, and the notes themselves - on a card that opens [NotePickerWindow] when tapped.
 */
@Composable
fun NoteSelectionSummary(
    title: String,
    detail: String,
    names: String,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Card(
        onClick = onChoose,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = names,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            InlineIcon(IconSource.Vector(SaxTrainerIcons.ChooseNotes), contentDescription = "Choose notes")
        }
    }
}
