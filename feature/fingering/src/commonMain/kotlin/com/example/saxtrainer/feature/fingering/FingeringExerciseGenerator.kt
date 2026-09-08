package com.example.saxtrainer.feature.fingering

import com.example.saxtrainer.core.exercise.NoteOrder
import com.example.saxtrainer.core.exercise.practiceOrder
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import kotlin.random.Random

/**
 * Turns a set of selected notes into the material for one exercise.
 *
 * The order comes from `practiceOrder` in `core:exercise`, which every note exercise shares; all
 * this adds is how long each note is shown, in beats, which is the only unit a sequence knows.
 * Turning that into seconds is the runner's business and happens against the exercise's tempo. Once a [ToneSequence] exists it is fixed and the runner
 * walks it deterministically, which keeps the runner reusable and makes an exercise reproducible
 * from a seed.
 */
object FingeringExerciseGenerator {

    fun generate(
        notes: List<Note>,
        order: NoteOrder,
        beatsPerNote: Beats,
        rounds: Int = 1,
        random: Random = Random.Default,
    ): ToneSequence {
        require(notes.isNotEmpty()) { "select at least one note" }
        require(rounds >= 1) { "an exercise needs at least one round" }
        require(beatsPerNote > Beats.ZERO) { "a note must be shown for some time" }

        return ToneSequence(
            steps = practiceOrder(notes, order, rounds, random).map { ToneStep(it, beatsPerNote) },
            name = EXERCISE_NAME,
        )
    }

    private const val EXERCISE_NAME = "Fingering practice"
}
