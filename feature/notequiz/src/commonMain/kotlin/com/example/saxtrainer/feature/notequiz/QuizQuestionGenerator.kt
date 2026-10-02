package com.example.saxtrainer.feature.notequiz

import com.example.saxtrainer.core.exercise.randomNoteAvoiding
import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.music.SpelledNote
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
 * Distractors are drawn from the same pool by [distractorsFor]: near in pitch more often than not,
 * and of the same kind of note as the target where the pool has one. Near neighbours are what make
 * the question a test of recognition rather than of eyesight.
 *
 * The pool is written notes, and may hold one pitch twice - D sharp and E flat. The question is
 * about one of them as written; no other spelling of its pitch is ever offered against it, and no
 * two options share a pitch, so the rule above - distinct options, distinct diagrams - still holds.
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
     *
     * [previous] is the note the question before this one asked about. It is never asked about
     * again straight away; there are always at least [MIN_OPTIONS] notes, so there is always
     * another to ask.
     */
    fun generate(
        pool: List<SpelledNote>,
        mode: QuizMode,
        chart: FingeringChart,
        random: Random = Random.Default,
        previous: SpelledNote? = null,
    ): QuizQuestion {
        val notes = pool.distinct().filter { chart.supports(it.note) }
        require(pitchCountOf(notes) >= MIN_OPTIONS) {
            "a question needs at least $MIN_OPTIONS pitches to choose between"
        }

        val asked = askedAs(mode, random)
        // Never the previous pitch, in whatever spelling: E flat after D sharp is the same note again.
        val target = randomNoteAvoiding(notes, previous, random) { it.note }

        fun optionsFor(slot: Int): List<SpelledNote> =
            candidatesFor(target, notes, asked.slots[slot].optionCount, random)

        return when (asked) {
            QuizMode.NOTE_AND_FINGERING -> {
                val names = optionsFor(0)
                val grips = optionsFor(1)
                NoteAndFingering(
                    note = target,
                    names = NameAnswers(names, names.indexOf(target)),
                    fingerings = FingeringAnswers(
                        options = grips.map { chart.fingeringsFor(it.note)!!.standard },
                        correctIndex = grips.indexOf(target),
                    ),
                )
            }

            QuizMode.NOTE_TO_FINGERING -> optionsFor(0).let { answers ->
                NoteToFingering(
                    note = target,
                    options = answers.map { chart.fingeringsFor(it.note)!!.standard },
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
                    fingering = chart.fingeringsFor(target.note)!!.standard,
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
     * One slot's worth of options: the target and its distractors, in no telling order.
     *
     * [wanted] is trimmed to what the pool can supply, so ten names are ten distinct names whenever
     * there are ten notes to draw them from, and fewer only when there are fewer.
     */
    private fun candidatesFor(
        target: SpelledNote,
        notes: List<SpelledNote>,
        wanted: Int,
        random: Random,
    ): List<SpelledNote> {
        val count = wanted.coerceIn(MIN_OPTIONS, pitchCountOf(notes))
        return (listOf(target) + distractorsFor(target, notes, count - 1, random)).shuffled(random)
    }

    /** How many different pitches [notes] holds - what a question can actually choose between. */
    fun pitchCountOf(notes: Collection<SpelledNote>): Int = notes.map { it.note }.distinct().size

    const val MIN_OPTIONS = 2
}
