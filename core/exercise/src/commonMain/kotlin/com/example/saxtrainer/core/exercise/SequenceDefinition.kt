package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.TimeSignature
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep
import com.example.saxtrainer.core.music.andBackDown
import com.example.saxtrainer.core.music.repeated

/**
 * What kind of thing a sequence is, for the benefit of a menu.
 *
 * Nothing that plays a sequence reads this: a scale and a melodic pattern are practised in exactly
 * the same way, and the moment a trainer branches on the category the abstraction has failed.
 */
enum class SequenceCategory(val displayName: String) {
    SCALE("Scales"),
    PATTERN("Patterns"),
}

/**
 * A named piece of practice material.
 *
 * Notes are **written** pitch - what the player reads and fingers - so "C Major" is the same scale
 * on every saxophone. Sounding pitch is worked out only where a microphone is involved.
 *
 * Named keys with their own spelling and their own rhythm, produced as a [ToneSequence] - the
 * abstraction every exercise shares.
 */
data class SequenceDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: SequenceCategory,
    val steps: List<ToneStep>,
    val accidentalStyle: AccidentalStyle = AccidentalStyle.SHARPS,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val suggestedTempo: Tempo = Tempo(DEFAULT_BPM),
) {
    init {
        require(id.isNotBlank()) { "a sequence needs an id" }
        require(steps.isNotEmpty()) { "sequence $id has no notes" }
    }

    val noteCount: Int get() = steps.size

    /**
     * The material to practise.
     *
     * [addInverse] plays the notes back down to where they started, and [repeats] plays the whole
     * thing over again. Both are written out here rather than handled while the exercise runs, so
     * the runner walks one plain list of notes and a beat position means the same thing in the last
     * pass as in the first.
     */
    fun build(addInverse: Boolean = false, repeats: Int = 1): ToneSequence {
        val written = ToneSequence(
            steps = steps,
            timeSignature = timeSignature,
            accidentalStyle = accidentalStyle,
            name = name,
            description = description,
        )
        return (if (addInverse) written.andBackDown() else written).repeated(repeats)
    }

    private companion object {
        const val DEFAULT_BPM = 80
    }
}
