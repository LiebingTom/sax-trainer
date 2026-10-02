package com.example.saxtrainer.feature.phrasequiz

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/** Decoys padding the phrase quiz's option grid: never a note nobody could ever play. */
class PhraseQuizDistractorsTest {

    @Test
    fun `a decoy near the bottom of the range never falls below what a saxophone can sound`() {
        val correct = listOf(Note(NoteName.C, 4), Note(NoteName.D, 4))

        repeat(50) { seed ->
            val decoys = decoysFor(correct, total = 18, random = Random(seed))
            decoys.forEach { decoy ->
                assertTrue(
                    decoy.midiNumber >= SaxWrittenRange.LOWEST.midiNumber,
                    "$decoy is below ${SaxWrittenRange.LOWEST}, which nobody can play",
                )
            }
        }
    }

    @Test
    fun `a decoy near the top of the range never climbs past what a saxophone can sound`() {
        val correct = listOf(Note(NoteName.E, 6), Note(NoteName.F, 6))

        repeat(50) { seed ->
            val decoys = decoysFor(correct, total = 18, random = Random(seed))
            decoys.forEach { decoy ->
                assertTrue(
                    decoy.midiNumber <= SaxWrittenRange.HIGHEST.midiNumber,
                    "$decoy is above ${SaxWrittenRange.HIGHEST}, which nobody can play",
                )
            }
        }
    }
}
