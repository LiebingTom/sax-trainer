package com.example.saxtrainer.learning.catalog.units

import com.example.saxtrainer.core.learning.LearningUnit
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.phrase.PatternLibrary
import com.example.saxtrainer.core.phrase.PhraseSource
import com.example.saxtrainer.core.phrase.ScaleLibrary
import com.example.saxtrainer.feature.phrasetrainer.PhraseTrainerPreset
import com.example.saxtrainer.learning.catalog.steps.phraseTrainerStep

/**
 * The blues scale, one key at a time.
 *
 * Built from the content files rather than written out here: the scale comes from
 * `content/scales/blues.json`, the run and the melody from `content/patterns`. A unit only says
 * which scale, which root and which patterns - so the notes it teaches are the scale's own, and a
 * fix to the scale file is a fix to the lesson.
 */
const val BLUES_1_UNIT_ID = "blues-1"

/** The scale pattern that runs the scale up and back down - see `content/patterns`. */
internal const val SCALE_RUN_PATTERN_ID = "scale_up_and_down"

/** The first blues melody - see `content/patterns`. */
internal const val BLUES_1_MELODY_PATTERN_ID = "blues_1_melody"

/** Times through each phrase in a blues unit. */
internal const val BLUES_PHRASE_REPEATS = 3

/**
 * A blues: A C D E♭ E G. One flat, the E♭ blue note, and everything else A minor pentatonic -
 * white keys only, in the middle of the horn. On a tenor it sounds in G, on an alto in C.
 */
val blues1Unit = bluesUnit(
    id = BLUES_1_UNIT_ID,
    title = "Blues 1",
    description = "The A blues scale: A C D E♭ E G. One flat - the E♭ blue note.",
    root = NoteName.A,
    octave = 4,
    melodyId = BLUES_1_MELODY_PATTERN_ID,
)

/**
 * One blues key: the scale's notes learned like any group's (name, play, hold), then the scale in
 * order and a melody on it, both in the phrase trainer.
 */
private fun bluesUnit(
    id: String,
    title: String,
    description: String,
    root: NoteName,
    octave: Int,
    melodyId: String,
): LearningUnit {
    val scale = requireNotNull(ScaleLibrary.byId("blues")) { "content/scales has no blues scale" }
    val run = requireNotNull(PatternLibrary.byId(SCALE_RUN_PATTERN_ID)) { "content/patterns has no $SCALE_RUN_PATTERN_ID" }
    requireNotNull(PatternLibrary.byId(melodyId)) { "content/patterns has no $melodyId" }

    // The scale's notes are whatever its run plays: root to root, an octave up.
    val notes = run.resolve(scale, root, listOf(octave)).distinctWrittenNotes()

    fun phraseLesson(lessonId: String, title: String, subtitle: String, patternId: String) = Lesson(
        id = LessonId("$id-$lessonId"),
        title = title,
        subtitle = subtitle,
        steps = listOf(
            phraseTrainerStep(
                PhraseTrainerPreset(
                    source = PhraseSource.ScalePattern(
                        patternId = patternId,
                        scale = scale,
                        root = root,
                        octaves = setOf(octave),
                    ),
                    repeats = BLUES_PHRASE_REPEATS,
                ),
            ),
        ),
    )

    return LearningUnit(
        id = id,
        title = title,
        description = description,
        lessons = noteLessons(id, notes) + listOf(
            phraseLesson(
                lessonId = "scale",
                title = "The scale",
                subtitle = "Up and back down in eighths, $BLUES_PHRASE_REPEATS times",
                patternId = SCALE_RUN_PATTERN_ID,
            ),
            phraseLesson(
                lessonId = "melody",
                title = "The melody",
                subtitle = "Four bars of call and response, $BLUES_PHRASE_REPEATS times",
                patternId = melodyId,
            ),
        ),
    )
}
