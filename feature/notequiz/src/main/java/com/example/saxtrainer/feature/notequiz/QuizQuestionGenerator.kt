package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.music.Note
import kotlin.math.abs
import kotlin.random.Random

/**
 * Builds one question out of the notes the player chose.
 *
 * **Standard fingerings only.** A note's alternates never appear, as the answer or as a distractor.
 * That is what makes a question unambiguous without any special handling: the chart lists no two
 * notes with the same set of pressed keys, so distinct notes always mean distinct diagrams. An
 * alternates mode would need multiple right answers or sibling exclusion, and it is not what a
 * player learning their first octave should be asked.
 *
 * Distractors are the target's nearest neighbours by pitch, drawn from the same pool. Near
 * neighbours are what make the question a test of recognition rather than of eyesight - including
 * octave twins, which differ by the octave key alone and are exactly worth drilling.
 */
object QuizQuestionGenerator {

    /**
     * A question about one of [pool], with [optionCount] answers.
     *
     * [optionCount] is trimmed to what the pool can supply without repeating an option, so a small
     * selection asks an easier question rather than showing the same answer twice.
     */
    fun generate(
        pool: List<Note>,
        mode: QuizMode,
        chart: FingeringChart,
        random: Random = Random.Default,
        optionCount: Int = DEFAULT_OPTION_COUNT,
    ): QuizQuestion {
        val notes = pool.distinct().filter(chart::supports)
        require(notes.size >= MIN_OPTIONS) { "a question needs at least $MIN_OPTIONS notes to choose between" }

        val target = notes.random(random)
        val options = optionCount.coerceIn(MIN_OPTIONS, notes.size)
        val answers = (listOf(target) + distractorsFor(target, notes, options - 1, random))
            .shuffled(random)

        return when (askedAs(mode, random)) {
            QuizMode.NOTE_TO_FINGERING -> NoteToFingering(
                note = target,
                options = answers.map { chart.fingeringsFor(it)!!.standard },
                correctIndex = answers.indexOf(target),
            )

            else -> FingeringToNote(
                note = target,
                fingering = chart.fingeringsFor(target)!!.standard,
                options = answers,
                correctIndex = answers.indexOf(target),
            )
        }
    }

    /** MIXED decides per question, so a run of them covers both directions. */
    private fun askedAs(mode: QuizMode, random: Random): QuizMode = when (mode) {
        QuizMode.MIXED ->
            if (random.nextBoolean()) QuizMode.NOTE_TO_FINGERING else QuizMode.FINGERING_TO_NOTE

        else -> mode
    }

    /**
     * The nearest notes by pitch, shuffled so the same neighbours do not come up in the same order
     * every time. Twice as many are considered as are needed, which keeps the answers close to the
     * target without making them predictable.
     */
    private fun distractorsFor(
        target: Note,
        notes: List<Note>,
        count: Int,
        random: Random,
    ): List<Note> = notes
        .filterNot { it == target }
        .sortedBy { abs(it.midiNumber - target.midiNumber) }
        .take(count * NEIGHBOURHOOD)
        .shuffled(random)
        .take(count)

    const val DEFAULT_OPTION_COUNT = 4
    const val MIN_OPTIONS = 2

    /** How many candidates are considered for every distractor actually shown. */
    private const val NEIGHBOURHOOD = 2
}
