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
import com.example.saxtrainer.feature.phrasetrainer.PhraseTrainerUiState
import com.example.saxtrainer.learning.catalog.steps.phraseTrainerStep

/**
 * The blues scale in A, G and D: Blues 1 learns its notes and runs the scale, Blues 2 plays
 * melodies on it.
 *
 * Built from the content files rather than written out here: the scale comes from
 * `content/scales/blues.json`, the run and the melodies from `content/patterns`. A unit only says
 * which scale, which roots and which patterns - so the notes it teaches are the scale's own, and a
 * fix to the scale file is a fix to the lesson.
 */
const val BLUES_1_UNIT_ID = "blues-1"
const val BLUES_2_UNIT_ID = "blues-2"

/** The scale pattern that runs the scale up and back down - see `content/patterns`. */
internal const val SCALE_RUN_PATTERN_ID = "scale_up_and_down"

/** A blues melody of `content/patterns`, and the name its lesson goes by. */
internal data class BluesMelody(val patternId: String, val lessonId: String, val title: String, val description: String)

/** Blues 2's melodies, in the order each key plays them: the first one, then the four with rests. */
internal val BLUES_2_MELODIES = listOf(
    BluesMelody("blues_1_melody", "melody", "melody", "Four bars up to the blue note and home"),
    BluesMelody("blues_2_riff", "riff", "riff", "A riff that grows by a note, with rests to count"),
    BluesMelody("blues_2_up_to_the_top", "up-to-the-top", "climb", "In after a rest, up to the top root and home"),
    BluesMelody("blues_2_falling_line", "falling-line", "falling line", "Long-short, down from the top, a rest after each half"),
    BluesMelody("blues_2_blue_note_lick", "blue-note-lick", "lick", "Eighths around the blue note, then all the way down"),
)

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
private val BLUES_KEYS = listOf(BluesKey(NoteName.A, 4), BluesKey(NoteName.G, 4), BluesKey(NoteName.D, 4))

private val bluesScale: Scale = requireNotNull(ScaleLibrary.byId("blues")) { "content/scales has no blues scale" }

val blues1Unit = bluesNotesUnit(
    id = BLUES_1_UNIT_ID,
    title = "Blues 1",
    description = "The blues scale in A, then G and D: one six-note pattern, three keys.",
    keys = BLUES_KEYS,
)

val blues2Unit = bluesMelodiesUnit(
    id = BLUES_2_UNIT_ID,
    title = "Blues 2",
    description = "Five melodies on the blues scale, in A, then G and D - with rests to count.",
    keys = BLUES_KEYS,
    melodies = BLUES_2_MELODIES,
)

/**
 * A blues unit for the notes: every half step named and played, both spellings, so any key's flats
 * and sharps can be read; then every note of all its keys played, the first key's notes held; then
 * each key's scale, the first key first.
 */
private fun bluesNotesUnit(id: String, title: String, description: String, keys: List<BluesKey>): LearningUnit {
    val scale = bluesScale
    // Played together: every note any of the keys asks for, each written as its scale writes it.
    val allNotes = keys.flatMap { bluesNotesOf(scale, it) }.distinct().sortedWith(SpelledNote.PITCH_ORDER)
    val firstKeyName = ScaleChoice(scale, keys.first().root).rootName
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
                notes = bluesNotesOf(scale, keys.first()),
                title = "Hold the $firstKeyName blues notes",
                subtitle = "One round of long tones, each note held for 8 beats",
            ) +
            keys.map { key ->
                val keyName = ScaleChoice(scale, key.root).rootName
                val notes = bluesNotesOf(scale, key).dropLast(1).joinToString(" ") { it.pitchName() }
                bluesPhraseLesson(
                    unitId = id,
                    scale = scale,
                    key = key,
                    lessonId = "scale",
                    title = "$keyName blues scale",
                    subtitle = "$notes - up and back down in eighths, $BLUES_PHRASE_REPEATS times",
                    patternId = SCALE_RUN_PATTERN_ID,
                )
            },
    )
}

/** A blues unit for the melodies: each key in turn, every melody in it, the first key first. */
private fun bluesMelodiesUnit(
    id: String,
    title: String,
    description: String,
    keys: List<BluesKey>,
    melodies: List<BluesMelody>,
): LearningUnit {
    val scale = bluesScale
    melodies.forEach { requireNotNull(PatternLibrary.byId(it.patternId)) { "content/patterns has no ${it.patternId}" } }

    return LearningUnit(
        id = id,
        title = title,
        description = description,
        lessons = keys.flatMap { key ->
            val keyName = ScaleChoice(scale, key.root).rootName
            melodies.map { melody ->
                bluesPhraseLesson(
                    unitId = id,
                    scale = scale,
                    key = key,
                    lessonId = melody.lessonId,
                    title = "$keyName blues ${melody.title}",
                    subtitle = "${melody.description}, $BLUES_PHRASE_REPEATS times",
                    patternId = melody.patternId,
                )
            }
        },
    )
}

/** The notes a key's scale run plays: root to root, an octave up. */
internal fun bluesNotesOf(scale: Scale, key: BluesKey): List<SpelledNote> {
    val run = requireNotNull(PatternLibrary.byId(SCALE_RUN_PATTERN_ID)) { "content/patterns has no $SCALE_RUN_PATTERN_ID" }
    return run.resolve(scale, key.root, listOf(key.octave)).distinctWrittenNotes()
}

/** One pattern on one key's blues scale in the phrase trainer, the notes shown throughout. */
private fun bluesPhraseLesson(
    unitId: String,
    scale: Scale,
    key: BluesKey,
    lessonId: String,
    title: String,
    subtitle: String,
    patternId: String,
) = Lesson(
    id = LessonId("$unitId-${key.root.name.lowercase()}-$lessonId"),
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
                // Read while played, not from memory: the blues are about the sound, not recall.
                showSeconds = PhraseTrainerUiState.SHOW_THROUGHOUT,
            ),
        ),
    ),
)

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
