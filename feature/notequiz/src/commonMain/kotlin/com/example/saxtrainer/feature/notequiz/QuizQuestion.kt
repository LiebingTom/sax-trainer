package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.fingering.Fingering
import com.example.saxtrainer.core.music.Note

/** What kind of thing is being chosen in one slot of a question. */
enum class AnswerKind { NAME, FINGERING, STAFF_NOTE }

/**
 * One thing a mode asks the player to choose, and how many of them it puts on screen.
 *
 * The count belongs to the mode and to nobody else: ten names fit on a screen and are read at a
 * glance, where ten fingering diagrams would be a wall of keywork. It is what the mode asks for,
 * not what it gets - a small selection of notes still asks a smaller question.
 */
data class AnswerSlot(val kind: AnswerKind, val optionCount: Int)

/**
 * Which way round a question is asked, and what it asks for.
 *
 * A mode is a list of [slots], every one of which has to be answered before the question is over.
 * Most modes ask for one thing; the combined one asks for two. A new mode declares its own slots
 * and nothing else in the quiz has to change.
 */
enum class QuizMode(
    val displayName: String,
    val description: String,
    val slots: List<AnswerSlot>,
) {
    NOTE_AND_FINGERING(
        "Note and fingering",
        "See a note, name it and pick the grip",
        listOf(
            AnswerSlot(AnswerKind.NAME, 10),
            AnswerSlot(AnswerKind.FINGERING, 4),
        ),
    ),
    NOTE_TO_FINGERING(
        "Note to fingering",
        "See a note, pick the grip",
        listOf(AnswerSlot(AnswerKind.FINGERING, 4)),
    ),
    NOTE_TO_NAME(
        "Note to name",
        "See a note, pick its name",
        listOf(AnswerSlot(AnswerKind.NAME, 10)),
    ),
    FINGERING_TO_NOTE(
        "Fingering to note",
        "See a grip, pick the note",
        listOf(AnswerSlot(AnswerKind.STAFF_NOTE, 4)),
    ),

    /** Asks as one of the single-slot modes, decided per question; its own slots are never used. */
    MIXED(
        "Mixed",
        "Grips and notes, in random order",
        listOf(AnswerSlot(AnswerKind.FINGERING, 4)),
    ),
}

/**
 * What one slot offers, and which of it is right.
 *
 * Which option is the right one is decided when the question is built, so answering is a comparison
 * of indices and never a second look at the chart.
 */
sealed interface QuizAnswers {
    val correctIndex: Int
    val optionCount: Int
}

/** Names to choose between. Notes rather than strings: how a name is written is the screen's. */
data class NameAnswers(
    val options: List<Note>,
    override val correctIndex: Int,
) : QuizAnswers {
    override val optionCount: Int get() = options.size
}

/** Fingering diagrams to choose between. */
data class FingeringAnswers(
    val options: List<Fingering>,
    override val correctIndex: Int,
) : QuizAnswers {
    override val optionCount: Int get() = options.size
}

/** Notes drawn on a staff to choose between. */
data class StaffNoteAnswers(
    val options: List<Note>,
    override val correctIndex: Int,
) : QuizAnswers {
    override val optionCount: Int get() = options.size
}

/**
 * One question and everything that has to be chosen to answer it.
 *
 * The target is always a written note, because that is what a player reads and what the fingering
 * chart is keyed by. [answers] holds one entry per slot, in the order they appear on screen.
 */
sealed interface QuizQuestion {
    val note: Note
    val answers: List<QuizAnswers>

    /** The question asked in words, for the screen reader and for the result. */
    val prompt: String
}

/** A note on the staff; the options are fingerings. */
data class NoteToFingering(
    override val note: Note,
    val options: List<Fingering>,
    val correctIndex: Int,
) : QuizQuestion {
    val optionCount: Int get() = options.size
    override val answers: List<QuizAnswers> get() = listOf(FingeringAnswers(options, correctIndex))
    override val prompt: String get() = "Which fingering is this note?"
}

/**
 * A note on the staff; the options are its name and the names of its neighbours.
 *
 * The options are notes rather than strings, so how each one is written stays where every other
 * name on screen is decided: the player's naming convention, applied when it is drawn.
 */
data class NoteToName(
    override val note: Note,
    val options: List<Note>,
    val correctIndex: Int,
) : QuizQuestion {
    val optionCount: Int get() = options.size
    override val answers: List<QuizAnswers> get() = listOf(NameAnswers(options, correctIndex))
    override val prompt: String get() = "What is this note called?"
}

/** A fingering diagram; the options are notes on the staff. */
data class FingeringToNote(
    override val note: Note,
    val fingering: Fingering,
    val options: List<Note>,
    val correctIndex: Int,
) : QuizQuestion {
    val optionCount: Int get() = options.size
    override val answers: List<QuizAnswers> get() = listOf(StaffNoteAnswers(options, correctIndex))
    override val prompt: String get() = "Which note is this fingering?"
}

/**
 * A note on the staff, asked both ways at once: what it is called, and how it is fingered.
 *
 * Two slots, answered in either order and independently of each other. Knowing the name and knowing
 * the grip are two different pieces of knowledge about the same note, and this is the mode that
 * asks whether the player has both.
 */
data class NoteAndFingering(
    override val note: Note,
    val names: NameAnswers,
    val fingerings: FingeringAnswers,
) : QuizQuestion {
    override val answers: List<QuizAnswers> get() = listOf(names, fingerings)
    override val prompt: String get() = "Name this note and pick its fingering"
}
