package com.example.saxtrainer.home

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.saxtrainer.core.designsystem.component.IconSource
import com.example.saxtrainer.core.designsystem.icon.Fingering
import com.example.saxtrainer.core.designsystem.icon.Learning
import com.example.saxtrainer.core.designsystem.icon.LongTone
import com.example.saxtrainer.core.designsystem.icon.Metronome
import com.example.saxtrainer.core.designsystem.icon.NoteQuiz
import com.example.saxtrainer.core.designsystem.icon.NoteTrainer
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.icon.Sequence
import com.example.saxtrainer.core.designsystem.icon.Settings
import com.example.saxtrainer.core.learning.Curriculum
import com.example.saxtrainer.learning.catalog.units.ACCIDENTALS_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.FIRST_NOTES_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.OCTAVE_UNIT_ID
import com.example.saxtrainer.navigation.SaxTrainerDestination

/**
 * A single item on the landing page. The home screen renders whatever is in the lists below, so a
 * future tool (tuner, ear training, ...) is added by appending one entry plus one navigation
 * destination - the home layout itself never changes.
 */
data class HomeEntry(
    val id: String,
    val title: String,
    val description: String,
    val icon: IconSource,
    val route: String,
)

/** A handful of practice tools that belong together, under a heading that says why. */
data class HomeGroup(val title: String, val entries: List<HomeEntry>)

/**
 * Guided lessons, one entry per learning unit.
 *
 * Derived from the curriculum rather than written out, so a new unit appears on the landing page
 * without this file or the home screen being edited. It is handed the curriculum rather than
 * reading the catalog itself, so the list on the landing page and the lessons behind it can never
 * be two different things.
 */
fun learningEntriesOf(curriculum: Curriculum): List<HomeEntry> = curriculum.units.map { unit ->
    HomeEntry(
        id = "learning-${unit.id}",
        title = unit.title,
        description = unit.description,
        icon = IconSource.Vector(iconForUnit(unit.id)),
        route = SaxTrainerDestination.learningUnit(unit.id),
    )
}

/**
 * A unit is a topic rather than a trainer, so it names its own icon instead of borrowing one.
 * Anything unnamed falls back to the generic learning mark.
 */
private fun iconForUnit(unitId: String): ImageVector = when (unitId) {
    FIRST_NOTES_UNIT_ID -> SaxTrainerIcons.NoteQuiz
    OCTAVE_UNIT_ID -> SaxTrainerIcons.NoteTrainer
    ACCIDENTALS_UNIT_ID -> SaxTrainerIcons.Fingering
    else -> SaxTrainerIcons.Learning
}

private val noteQuiz = HomeEntry(
    id = "notequiz",
    title = "Note Quiz",
    description = "Match written notes to fingerings. No instrument needed.",
    icon = IconSource.Vector(SaxTrainerIcons.NoteQuiz),
    route = SaxTrainerDestination.NOTE_QUIZ,
)

private val fingering = HomeEntry(
    id = "fingering",
    title = "Fingering Trainer",
    description = "Learn which keys to press for each written note.",
    icon = IconSource.Vector(SaxTrainerIcons.Fingering),
    route = SaxTrainerDestination.FINGERING,
)

private val noteTrainer = HomeEntry(
    id = "notetrainer",
    title = "Note Trainer",
    description = "Read a note and play it. How quickly is up to you.",
    icon = IconSource.Vector(SaxTrainerIcons.NoteTrainer),
    route = SaxTrainerDestination.NOTE_TRAINER,
)

private val longTones = HomeEntry(
    id = "longtones",
    title = "Long Tones",
    description = "Hold a note steadily with live level and spectrum feedback.",
    icon = IconSource.Vector(SaxTrainerIcons.LongTone),
    route = SaxTrainerDestination.LONG_TONES,
)

private val sequence = HomeEntry(
    id = "sequence",
    title = "Sequence Trainer",
    description = "Play scales and patterns in time, with an optional click.",
    icon = IconSource.Vector(SaxTrainerIcons.Sequence),
    route = SaxTrainerDestination.SEQUENCE,
)

private val metronome = HomeEntry(
    id = "metronome",
    title = "Metronome",
    description = "Steady click with accented downbeat and tap tempo.",
    icon = IconSource.Vector(SaxTrainerIcons.Metronome),
    route = SaxTrainerDestination.METRONOME,
)

/**
 * The practice tools, grouped by the skill they train.
 *
 * Six cards in a flat list would say nothing about which tool answers which question, and the
 * difference between them is the whole point: recognise the note, learn the grip, play the note,
 * play a line. The headings are what make that visible without anybody reading a description.
 */
val practiceGroups: List<HomeGroup> = listOf(
    HomeGroup("Know your notes", listOf(noteQuiz, fingering)),
    HomeGroup("Play your notes", listOf(noteTrainer, longTones)),
    HomeGroup("Play in time", listOf(sequence, metronome)),
)

/** Every practice tool, flattened - for previews and for anything that wants the plain list. */
val practiceEntries: List<HomeEntry> = practiceGroups.flatMap { it.entries }

val appEntries: List<HomeEntry> = listOf(
    HomeEntry(
        id = "settings",
        title = "Settings",
        description = "Instrument and transposition used across all tools.",
        icon = IconSource.Vector(SaxTrainerIcons.Settings),
        route = SaxTrainerDestination.SETTINGS,
    ),
)
