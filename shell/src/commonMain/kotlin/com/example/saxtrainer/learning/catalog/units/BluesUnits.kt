package com.example.saxtrainer.learning.catalog.units

import com.example.saxtrainer.core.learning.LearningUnit
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.Scale
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.phrase.PatternLibrary
import com.example.saxtrainer.core.phrase.PhraseSource
import com.example.saxtrainer.core.phrase.ScaleChoice
import com.example.saxtrainer.core.phrase.ScaleLibrary
import com.example.saxtrainer.feature.phrasetrainer.PhraseTrainerPreset
import com.example.saxtrainer.learning.catalog.steps.phraseTrainerStep

/**
 * The blues scale, one key at a time.
 *
 * Built from the content files rather than written out here: the scale comes from
 * `content/scales/blues.json`, the run and the melody from `content/patterns`. A unit only says
 * which scale, which roots and which patterns - so the notes it teaches are the scale's own, and a
 * fix to the scale file is a fix to the lesson.
 */
const val BLUES_1_UNIT_ID = "blues-1"

/** The scale pattern that runs the scale up and back down - see `content/patterns`. */
internal const val SCALE_RUN_PATTERN_ID = "scale_up_and_down"

/** The first blues melody - see `content/patterns`. */
internal const val BLUES_1_MELODY_PATTERN_ID = "blues_1_melody"

/** Times through each phrase in a blues unit. */
internal const val BLUES_PHRASE_REPEATS = 3

/** One key a blues unit plays the scale in: its root, and the octave the run starts in. */
internal data class BluesKey(val root: NoteName, val octave: Int)

/**
 * The blues in A, then G, then D.
 *
 * The half steps come first, every black key read both ways, because G (B♭, D♭) and D (A♭) need
 * flats that A does not. A leads the keys: A C D E♭ E G is one flat away from the white keys.
 */
val blues1Unit = bluesUnit(
    id = BLUES_1_UNIT_ID,
    title = "Blues 1",
    description = "The blues scale in A, then G and D: one six-note pattern, three keys.",
    firstKey = BluesKey(NoteName.A, 4),
    moreKeys = listOf(BluesKey(NoteName.G, 4), BluesKey(NoteName.D, 4)),
    melodyId = BLUES_1_MELODY_PATTERN_ID,
)

/**
 * A blues unit: every half step named and played, both spellings, so any key's flats and sharps
 * can be read; then every note of all its keys played, the first key's notes held; then each key's
 * scale and melody, the first key first.
 */
private fun bluesUnit(
    id: String,
    title: String,
    description: String,
    firstKey: BluesKey,
    moreKeys: List<BluesKey>,
    melodyId: String,
): LearningUnit {
    val scale = requireNotNull(ScaleLibrary.byId("blues")) { "content/scales has no blues scale" }
    requireNotNull(PatternLibrary.byId(melodyId)) { "content/patterns has no $melodyId" }

    fun keyLessons(key: BluesKey) = bluesKeyLessons(id, scale, key, melodyId)
    val keys = listOf(firstKey) + moreKeys
    // Played together: every note any of the keys asks for, each written as its scale writes it.
    val allNotes = keys.flatMap { bluesNotesOf(scale, it) }.distinct().sortedWith(SpelledNote.PITCH_ORDER)
    val firstKeyName = ScaleChoice(scale, firstKey.root).rootName
    val keyNames = keys.map { ScaleChoice(scale, it.root).rootName }
        .let { it.dropLast(1).joinToString(", ") + " and " + it.last() }

    return LearningUnit(
        id = id,
        title = title,
        description = description,
        lessons = halfStepLessons(id) +
            playLesson(
                id = id,
                notes = allNotes,
                title = "Play the blues notes",
                subtitle = "Every note of the $keyNames blues, $TRAINER_ROUNDS rounds, each held for 2 beats",
            ) +
            holdLesson(
                id = id,
                notes = bluesNotesOf(scale, firstKey),
                title = "Hold the $firstKeyName blues notes",
                subtitle = "One round of long tones, each note held for 8 beats",
            ) +
            keys.flatMap(::keyLessons),
    )
}

/** The notes a key's scale run plays: root to root, an octave up. */
internal fun bluesNotesOf(scale: Scale, key: BluesKey): List<SpelledNote> {
    val run = requireNotNull(PatternLibrary.byId(SCALE_RUN_PATTERN_ID)) { "content/patterns has no $SCALE_RUN_PATTERN_ID" }
    return run.resolve(scale, key.root, listOf(key.octave)).distinctWrittenNotes()
}

/** One key in the phrase trainer: the scale up and down, then the melody on it. */
private fun bluesKeyLessons(unitId: String, scale: Scale, key: BluesKey, melodyId: String): List<Lesson> {
    val keyName = ScaleChoice(scale, key.root).rootName
    val keyId = "$unitId-${key.root.name.lowercase()}"
    val notes = bluesNotesOf(scale, key).dropLast(1).joinToString(" ") { it.pitchName() }

    fun phraseLesson(lessonId: String, title: String, subtitle: String, patternId: String) = Lesson(
        id = LessonId("$keyId-$lessonId"),
        title = title,
        subtitle = subtitle,
        steps = listOf(
            phraseTrainerStep(
                PhraseTrainerPreset(
                    source = PhraseSource.ScalePattern(
                        patternId = patternId,
                        scale = scale,
                        root = key.root,
                        octaves = setOf(key.octave),
                    ),
                    repeats = BLUES_PHRASE_REPEATS,
                ),
            ),
        ),
    )

    return listOf(
        phraseLesson(
            lessonId = "scale",
            title = "$keyName blues scale",
            subtitle = "$notes - up and back down in eighths, $BLUES_PHRASE_REPEATS times",
            patternId = SCALE_RUN_PATTERN_ID,
        ),
        phraseLesson(
            lessonId = "melody",
            title = "$keyName blues melody",
            subtitle = "Four bars up to the blue note and home to $keyName, $BLUES_PHRASE_REPEATS times",
            patternId = melodyId,
        ),
    )
}

/**
 * Every half step from low C to high C, each black key as a sharp and as a flat: named in one
 * lesson, played in the next. Played once through - with both spellings that is 35 notes.
 */
private fun halfStepLessons(unitId: String): List<Lesson> = listOf(
    quizLesson(
        id = unitId,
        notes = HALF_STEPS_TWO_OCTAVES,
        lessonId = "$unitId-half-steps-quiz",
        title = "Name every half step",
        subtitle = "C to C, two octaves, C♯ also as D♭ and so on: $QUIZ_QUESTIONS questions",
    ),
    playLesson(
        id = unitId,
        notes = HALF_STEPS_TWO_OCTAVES,
        lessonId = "$unitId-half-steps-play",
        title = "Play every half step",
        rounds = 1,
        subtitle = "The same notes, each played once and held for 2 beats",
    ),
)

/** The letter and sign without the octave, as a key's notes are listed: "E♭", "F♯", "A". */
private fun SpelledNote.pitchName(): String =
    token.dropLastWhile { it.isDigit() }.replace("#", "♯").replace("b", "♭")
