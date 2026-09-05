package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName

/**
 * The predefined practice patterns.
 *
 * Roots are concert pitch, like every other note in the app; instruments only change how the notes
 * are labelled. At the default root the patterns read as A4, A4 ten times, A4 B4 A4 B4 A4, and so
 * on - see each entry.
 */
object SequenceCatalog {

    val defaultRoot: Note = Note(NoteName.A, 4)

    private val LONG_HOLD = Beats.of(5)
    private val STANDARD_HOLD = Beats.of(4)
    private const val DEFAULT_BPM = 60

    /** A4 */
    val singleNote = ExercisePattern(
        id = "single_note",
        name = "Single Note",
        description = "Hold one note as steadily as you can.",
        offsets = listOf(0),
        defaultBeatsPerNote = LONG_HOLD,
        suggestedBpm = DEFAULT_BPM,
    )

    /** A4 ten times */
    val repeat = ExercisePattern(
        id = "repeat",
        name = "Repeat",
        description = "The same note ten times - consistent attack and tone.",
        offsets = List(10) { 0 },
        defaultBeatsPerNote = STANDARD_HOLD,
        suggestedBpm = DEFAULT_BPM,
    )

    /** A4 B4 A4 B4 A4 */
    val neighboringNotes = ExercisePattern(
        id = "neighboring_notes",
        name = "Neighboring Notes",
        description = "Alternate between two neighbouring notes.",
        offsets = listOf(0, 2, 0, 2, 0),
        defaultBeatsPerNote = STANDARD_HOLD,
        suggestedBpm = DEFAULT_BPM,
    )

    /** A4 B4 C5 D5 */
    val stepwiseUp = ExercisePattern(
        id = "stepwise_up",
        name = "Stepwise Up",
        description = "Four notes rising step by step.",
        offsets = listOf(0, 2, 3, 5),
        defaultBeatsPerNote = STANDARD_HOLD,
        suggestedBpm = DEFAULT_BPM,
    )

    /** A4 B4 C5 D5 C5 B4 A4 */
    val stepwiseUpAndDown = ExercisePattern(
        id = "stepwise_up_down",
        name = "Stepwise Up & Down",
        description = "Rise step by step and come back down.",
        offsets = listOf(0, 2, 3, 5, 3, 2, 0),
        defaultBeatsPerNote = STANDARD_HOLD,
        suggestedBpm = DEFAULT_BPM,
    )

    /** A4 C5 B4 D5 C5 E5 */
    val ascendingThirds = ExercisePattern(
        id = "ascending_thirds",
        name = "Ascending Thirds",
        description = "Thirds climbing in pairs.",
        offsets = listOf(0, 3, 2, 5, 3, 7),
        defaultBeatsPerNote = STANDARD_HOLD,
        suggestedBpm = DEFAULT_BPM,
    )

    /** A4 E5 A4 E5 */
    val fifth = ExercisePattern(
        id = "fifth",
        name = "Fifth",
        description = "Jump between a note and its fifth.",
        offsets = listOf(0, 7, 0, 7),
        defaultBeatsPerNote = STANDARD_HOLD,
        suggestedBpm = DEFAULT_BPM,
    )

    val patterns: List<ExercisePattern> = listOf(
        singleNote,
        repeat,
        neighboringNotes,
        stepwiseUp,
        stepwiseUpAndDown,
        ascendingThirds,
        fifth,
    )

    fun byId(id: String): ExercisePattern? = patterns.firstOrNull { it.id == id }
}
