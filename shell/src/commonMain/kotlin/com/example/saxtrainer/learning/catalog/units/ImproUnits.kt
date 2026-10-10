package com.example.saxtrainer.learning.catalog.units

import com.example.saxtrainer.core.designsystem.component.NotationStyle
import com.example.saxtrainer.core.learning.LearningUnit
import com.example.saxtrainer.core.learning.Lesson
import com.example.saxtrainer.core.learning.LessonId
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.Scale
import com.example.saxtrainer.core.phrase.PatternLibrary
import com.example.saxtrainer.core.phrase.PhraseSource
import com.example.saxtrainer.core.phrase.ScaleChoice
import com.example.saxtrainer.core.phrase.ScaleLibrary
import com.example.saxtrainer.feature.phrasetrainer.PhraseTrainerPreset
import com.example.saxtrainer.feature.phrasetrainer.PhraseTrainerUiState
import com.example.saxtrainer.learning.catalog.steps.phraseTrainerStep

/**
 * Improvising starts with hearing a key as numbers: Impro 1 plays a song on the major pentatonic
 * shown as degrees - 1 2 3 5 6 - rather than notes, so the distances are learnt and not the letters.
 *
 * As with the blues, the notes are not written here: the scale comes from `content/scales`, the song
 * from `content/patterns`, and a unit only says which keys and which pattern each plays.
 */
const val IMPRO_1_UNIT_ID = "impro-1"

/** Times through the song in an Impro lesson. */
internal const val IMPRO_PHRASE_REPEATS = 3

/**
 * One key the song is played in, and which version of it: in D the low 5 of bars five and six would
 * be an A3, under the saxophone, so D plays the version that turns up there instead.
 */
internal data class ImproKey(val root: NoteName, val octave: Int, val patternId: String)

internal val IMPRO_1_KEYS = listOf(
    ImproKey(NoteName.G, 4, "impro_1_song"),
    ImproKey(NoteName.D, 4, "impro_1_song_high"),
)

private val majorPentatonic: Scale =
    requireNotNull(ScaleLibrary.byId("major_pentatonic")) { "content/scales has no major_pentatonic scale" }

val impro1Unit = LearningUnit(
    id = IMPRO_1_UNIT_ID,
    title = "Impro 1",
    description = "A song on the major pentatonic, read as numbers: G major, then D major.",
    lessons = IMPRO_1_KEYS.map { key ->
        requireNotNull(PatternLibrary.byId(key.patternId)) { "content/patterns has no ${key.patternId}" }
        val keyName = ScaleChoice(majorPentatonic, key.root).rootName
        Lesson(
            id = LessonId("$IMPRO_1_UNIT_ID-${key.root.name.lowercase()}-song"),
            title = "Song in $keyName major",
            subtitle = "1 2 3 5 6 as numbers, the notes shown throughout, $IMPRO_PHRASE_REPEATS times",
            steps = listOf(
                phraseTrainerStep(
                    PhraseTrainerPreset(
                        source = PhraseSource.ScalePattern(
                            patternId = key.patternId,
                            scale = majorPentatonic,
                            root = key.root,
                            octaves = setOf(key.octave),
                        ),
                        repeats = IMPRO_PHRASE_REPEATS,
                        showSeconds = PhraseTrainerUiState.SHOW_THROUGHOUT,
                        notation = NotationStyle.NUMBERS,
                    ),
                ),
            ),
        )
    },
)
