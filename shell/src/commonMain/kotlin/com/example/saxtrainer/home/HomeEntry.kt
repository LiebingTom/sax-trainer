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
import com.example.saxtrainer.core.learning.TrainerId
import com.example.saxtrainer.learning.catalog.units.ACCIDENTALS_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.FIRST_NOTES_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.OCTAVE_UNIT_ID
import com.example.saxtrainer.learning.catalog.units.UPPER_OCTAVE_UNIT_ID
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
    /**
     * The practice tool this card starts, or null where it starts something else - a lesson unit,
     * a list, the settings.
     *
     * It is what makes "only a practice tool can be starred" a fact about the entry rather than a
     * rule a screen has to remember, and it is [TrainerId] rather than a name of this file's own
     * because the app already has exactly one name for a trainer.
     */
    val trainerId: TrainerId? = null,
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
    UPPER_OCTAVE_UNIT_ID -> SaxTrainerIcons.Sequence
    else -> SaxTrainerIcons.Learning
}

private val noteQuiz = HomeEntry(
    id = "notequiz",
    title = "Note Quiz",
    description = "Match written notes to fingerings. No instrument needed.",
    icon = IconSource.Vector(SaxTrainerIcons.NoteQuiz),
    route = SaxTrainerDestination.NOTE_QUIZ,
    trainerId = TrainerId.NOTE_QUIZ,
)

private val noteTrainer = HomeEntry(
    id = "notetrainer",
    title = "Note Trainer",
    description = "Read a note and play it. How quickly is up to you.",
    icon = IconSource.Vector(SaxTrainerIcons.NoteTrainer),
    route = SaxTrainerDestination.NOTE_TRAINER,
    trainerId = TrainerId.NOTE_TRAINER,
)

private val longTones = HomeEntry(
    id = "longtones",
    title = "Long Tones",
    description = "Hold a note steadily with live level and spectrum feedback.",
    icon = IconSource.Vector(SaxTrainerIcons.LongTone),
    route = SaxTrainerDestination.LONG_TONES,
    trainerId = TrainerId.LONG_TONES,
)

private val sequence = HomeEntry(
    id = "sequence",
    title = "Sequence Trainer",
    description = "Play scales and patterns in time, with an optional click.",
    icon = IconSource.Vector(SaxTrainerIcons.Sequence),
    route = SaxTrainerDestination.SEQUENCE,
    trainerId = TrainerId.SEQUENCE,
)

private val metronome = HomeEntry(
    id = "metronome",
    title = "Metronome",
    description = "Steady click with accented downbeat and tap tempo.",
    icon = IconSource.Vector(SaxTrainerIcons.Metronome),
    route = SaxTrainerDestination.METRONOME,
    trainerId = TrainerId.METRONOME,
)

/**
 * The practice tools, grouped by the skill they train.
 *
 * Five cards in a flat list would say nothing about which tool answers which question, and the
 * difference between them is the whole point: recognise the note, play the note, play a line. The
 * headings are what make that visible without anybody reading a description.
 */
val practiceGroups: List<HomeGroup> = listOf(
    HomeGroup("Know your notes", listOf(noteQuiz)),
    HomeGroup("Play your notes", listOf(noteTrainer, longTones)),
    HomeGroup("Play in time", listOf(sequence, metronome)),
)

/**
 * The starred tools, in the order the catalogue lists them.
 *
 * Resolved against the catalogue rather than stored alongside the star, so a favorite can never
 * say something the Practice list does not - and a name written by a newer version, or one whose
 * tool has since gone, simply finds nothing. The order is the traversal's: there is none stored,
 * and a starred tool sits where a player already knows to look for it.
 */
fun favoriteEntriesOf(
    favorites: Set<String>,
    practice: List<HomeGroup> = practiceGroups,
): List<HomeEntry> = practice
    .flatMap { it.entries }
    .filter { it.trainerId != null && it.trainerId.name in favorites }

/** The two lists and the settings: everything the landing page offers besides a starred tool. */
val appEntries: List<HomeEntry> = listOf(
    HomeEntry(
        id = "practice",
        title = "Practice",
        description = "All five practice tools, grouped by what they train.",
        icon = IconSource.Vector(SaxTrainerIcons.NoteTrainer),
        route = SaxTrainerDestination.PRACTICE,
    ),
    HomeEntry(
        id = "lessons",
        title = "Lessons",
        description = "Guided units that take one topic at a time.",
        icon = IconSource.Vector(SaxTrainerIcons.Learning),
        route = SaxTrainerDestination.LESSONS,
    ),
    HomeEntry(
        id = "my-lessons",
        title = "My Lessons",
        description = "The practices you saved, in the groups you put them in.",
        icon = IconSource.Vector(SaxTrainerIcons.Learning),
        route = SaxTrainerDestination.MY_LESSONS,
    ),
    HomeEntry(
        id = "settings",
        title = "Settings",
        description = "Instrument and transposition used across all tools.",
        icon = IconSource.Vector(SaxTrainerIcons.Settings),
        route = SaxTrainerDestination.SETTINGS,
    ),
)
