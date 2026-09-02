package com.example.saxtrainer.feature.fingering

import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import kotlin.random.Random

/** Whether the exercise follows the notes in pitch order or mixes them up. */
enum class NoteOrder {
    /** Shuffled, so the player has to read the note instead of reciting the scale. */
    RANDOM,

    /** Ascending, useful when first meeting the notes. */
    ORDERED,
}

/**
 * Turns a set of selected notes into the material for one exercise.
 *
 * Randomisation lives here rather than in `SequenceRunner`: once a [ToneSequence] exists it is fixed
 * and the runner walks it deterministically, which keeps the runner reusable and makes an exercise
 * reproducible from a seed.
 */
object FingeringExerciseGenerator {

    /**
     * One round plays every selected note exactly once, so nothing is skipped and the exercise
     * length is simply notes times [rounds]. In [NoteOrder.RANDOM] each round is shuffled on its
     * own, and a shuffle that would repeat the previous note across the round boundary is redrawn.
     */
    fun generate(
        notes: List<Note>,
        order: NoteOrder,
        secondsPerNote: Int,
        rounds: Int = 1,
        tempo: Tempo = Tempo(60),
        random: Random = Random.Default,
    ): ToneSequence {
        require(notes.isNotEmpty()) { "select at least one note" }
        require(rounds >= 1) { "an exercise needs at least one round" }
        require(secondsPerNote > 0) { "a note must be shown for some time" }

        val duration = tempo.beatsFor(secondsPerNote.toDouble())
        val pool = notes.distinct()
        val sequenceNotes = mutableListOf<Note>()

        repeat(rounds) {
            sequenceNotes += when (order) {
                NoteOrder.ORDERED -> pool
                NoteOrder.RANDOM -> shuffleAvoidingRepeat(pool, sequenceNotes.lastOrNull(), random)
            }
        }

        return ToneSequence(
            steps = sequenceNotes.map { ToneStep(it, duration) },
            name = EXERCISE_NAME,
        )
    }

    /**
     * Shuffles the pool, retrying while the first note would repeat the one just played. With a
     * single note in the pool a repeat is unavoidable, and the retries are bounded so an unlucky
     * generator can never spin.
     */
    private fun shuffleAvoidingRepeat(
        pool: List<Note>,
        previous: Note?,
        random: Random,
    ): List<Note> {
        var shuffled = pool.shuffled(random)
        if (previous == null || pool.size < 2) return shuffled

        var attempts = 0
        while (shuffled.first() == previous && attempts < MAX_RESHUFFLES) {
            shuffled = pool.shuffled(random)
            attempts++
        }
        return shuffled
    }

    private const val MAX_RESHUFFLES = 8
    private const val EXERCISE_NAME = "Fingering practice"
}
