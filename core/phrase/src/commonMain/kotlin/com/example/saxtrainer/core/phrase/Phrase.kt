package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.music.TimeSignature
import com.example.saxtrainer.core.music.ToneSequence

/**
 * A named, multi-bar piece of practice material: what the Phrase Trainer and Phrase Quiz show, hide
 * and check the player against.
 *
 * Bars are authored explicitly rather than derived from [com.example.saxtrainer.core.music.bars] -
 * robust for hand or tool authoring, with no floating point bar-boundary inference needed - which is
 * why each bar's total duration is validated against [timeSignature] here, once, at construction.
 *
 * [trainingBars] is how many bars wide one practice window is, authored as part of the content
 * itself rather than chosen by the player: a phrase that is meant to be practised two bars at a time
 * says so once, here, and every screen that walks it agrees without asking. There is deliberately no
 * per-player override - see [units].
 *
 * Carries no tempo, for the same reason [ToneSequence] does not: the same phrase is practised at many
 * speeds, so tempo belongs to whoever runs the exercise.
 */
data class Phrase(
    val id: String,
    val name: String,
    val description: String,
    val bars: List<PhraseBar>,
    /** How many consecutive bars make up one practice unit - see [units]. */
    val trainingBars: Int = 1,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val accidentalStyle: AccidentalStyle = AccidentalStyle.SHARPS,
) {
    init {
        require(id.isNotBlank()) { "a phrase needs an id" }
        require(bars.isNotEmpty()) { "phrase $id has no bars" }
        require(trainingBars >= 1) { "phrase $id needs at least one training bar" }
        bars.forEachIndexed { index, bar ->
            val total = bar.steps.fold(Beats.ZERO) { sum, step -> sum + step.duration }
            require(total.value == timeSignature.beatsPerBar.toDouble()) {
                "bar ${index + 1} of phrase $id lasts $total, but $timeSignature needs " +
                    "${timeSignature.beatsPerBar} beats"
            }
        }
    }

    /**
     * The phrase cut into practice windows of [trainingBars] consecutive bars each. The last one is
     * whatever is left over, even if that is fewer bars than the rest - a six-bar phrase at three
     * bars a unit is two whole units, but a seven-bar one is two full units and a short third.
     */
    val units: List<PracticeUnit>
        get() = bars.indices.chunked(trainingBars).map { PracticeUnit(it.first()..it.last()) }

    /**
     * Whether every note knows its degree in a key, so the phrase can be shown as numbers: true for
     * a resolved scale pattern, false for a library phrase written out note by note.
     */
    val hasDegrees: Boolean
        get() = bars.flatMap { it.steps }.filterNot { it.isRest }.let { notes ->
            notes.isNotEmpty() && notes.all { it.degree != null }
        }

    /**
     * Every note the phrase asks for, each once and exactly as written, lowest first - what it is
     * made of, with its order, rhythm and rests left behind.
     */
    fun distinctWrittenNotes(): List<SpelledNote> =
        toneSequence().writtenNotes.distinct().sortedWith(SpelledNote.PITCH_ORDER)

    /** The whole phrase, flattened into the flat sequence type everything else plays and reads. */
    fun toneSequence(): ToneSequence = ToneSequence(
        steps = bars.flatMap { it.steps },
        timeSignature = timeSignature,
        accidentalStyle = accidentalStyle,
        name = name,
        description = description,
    )

    /** Just [unit]'s bars, as their own playable sequence. */
    fun toneSequenceOf(unit: PracticeUnit): ToneSequence = ToneSequence(
        steps = unit.bars.flatMap { bars[it].steps },
        timeSignature = timeSignature,
        accidentalStyle = accidentalStyle,
    )
}
