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
     * A question about one of [pool], with as many answers per slot as the mode asks for.
     *
     * How many answers suit a question is a property of the mode and nobody else's to set. The pool
     * is a separate matter and is normally far larger - it is trimmed to only when it cannot fill a
     * slot without repeating an option, so a small selection asks an easier question rather than
     * showing the same answer twice.
     *
     * A slot draws its own options, so the ten names and the four grips of a combined question are
     * two independent draws around the same target rather than two views of one list.
     */
    fun generate(
        pool: List<Note>,
        mode: QuizMode,
        chart: FingeringChart,
        random: Random = Random.Default,
    ): QuizQuestion {
        val notes = pool.distinct().filter(chart::supports)
        require(notes.size >= MIN_OPTIONS) { "a question needs at least $MIN_OPTIONS notes to choose between" }

        val asked = askedAs(mode, random)
        val target = notes.random(random)

        fun optionsFor(slot: Int): List<Note> =
            candidatesFor(target, notes, asked.slots[slot].optionCount, random)

        return when (asked) {
            QuizMode.NOTE_AND_FINGERING -> {
                val names = optionsFor(0)
                val grips = optionsFor(1)
                NoteAndFingering(
                    note = target,
                    names = NameAnswers(names, names.indexOf(target)),
                    fingerings = FingeringAnswers(
                        options = grips.map { chart.fingeringsFor(it)!!.standard },
                        correctIndex = grips.indexOf(target),
                    ),
                )
            }

            QuizMode.NOTE_TO_FINGERING -> optionsFor(0).let { answers ->
                NoteToFingering(
                    note = target,
                    options = answers.map { chart.fingeringsFor(it)!!.standard },
                    correctIndex = answers.indexOf(target),
                )
            }

            QuizMode.NOTE_TO_NAME -> optionsFor(0).let { answers ->
                NoteToName(
                    note = target,
                    options = answers,
                    correctIndex = answers.indexOf(target),
                )
            }

            else -> optionsFor(0).let { answers ->
                FingeringToNote(
                    note = target,
                    fingering = chart.fingeringsFor(target)!!.standard,
                    options = answers,
                    correctIndex = answers.indexOf(target),
                )
            }
        }
    }

    /**
     * MIXED decides per question, so a run of them covers both directions. Decided before anything
     * else is drawn, because what a question asks for depends on which way round it is.
     */
    private fun askedAs(mode: QuizMode, random: Random): QuizMode = when (mode) {
        QuizMode.MIXED ->
            if (random.nextBoolean()) QuizMode.NOTE_TO_FINGERING else QuizMode.FINGERING_TO_NOTE

        else -> mode
    }


    /**
     * One slot's worth of options: the target and its nearest neighbours, in no telling order.
     *
     * [wanted] is trimmed to what the pool can supply, so ten names are ten distinct names whenever
     * there are ten notes to draw them from, and fewer only when there are fewer.
     */
    private fun candidatesFor(
        target: Note,
        notes: List<Note>,
        wanted: Int,
        random: Random,
    ): List<Note> {
        val count = wanted.coerceIn(MIN_OPTIONS, notes.size)
        return (listOf(target) + distractorsFor(target, notes, count - 1, random)).shuffled(random)
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

    const val MIN_OPTIONS = 2

    /** How many candidates are considered for every distractor actually shown. */
    private const val NEIGHBOURHOOD = 2
}
