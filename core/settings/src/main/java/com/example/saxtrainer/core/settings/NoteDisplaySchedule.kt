package com.example.saxtrainer.core.settings

import kotlin.random.Random

/**
 * How each note of an exercise is presented, decided in advance.
 *
 * [NoteDisplay.RANDOM] has to be turned into something drawable, and it cannot be decided at draw
 * time: a screen republishes many times a second, so rolling per frame would make a note flicker
 * between its name and the staff while the player is still reading it. Deciding once per note, for
 * the whole exercise, also means the small preview of the next note already shows what that note
 * will look like when it arrives.
 *
 * Nothing is drawn from [random] unless [display] is [NoteDisplay.RANDOM], so switching the option
 * on is the only thing that changes any other random draw in an exercise.
 *
 * @param stepCount how many notes the exercise has.
 */
fun noteDisplaySchedule(
    display: NoteDisplay,
    stepCount: Int,
    random: Random = Random.Default,
): List<NoteDisplay> {
    require(stepCount >= 0) { "an exercise cannot have $stepCount notes" }
    if (!display.isRandom) return List(stepCount) { display }

    // Name or notation, never both: a note shown twice over asks the player nothing.
    return List(stepCount) { if (random.nextBoolean()) NoteDisplay.NAME else NoteDisplay.STAFF }
}

/**
 * The presentation of note [index], or [fallback] past the end of the exercise.
 *
 * Reading a schedule by index is the only thing the trainers do with it, and asking for the note
 * after the last one is normal - that is the preview slot on the final note.
 */
fun List<NoteDisplay>.displayAt(
    index: Int,
    fallback: NoteDisplay = NoteDisplay.BOTH,
): NoteDisplay = getOrElse(index) { fallback }
