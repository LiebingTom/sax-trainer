package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import com.example.saxtrainer.core.music.Scale
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.music.TimeSignature
import com.example.saxtrainer.core.music.ToneStep

/** One note of a [RecipeBar]: a scale degree and how long it lasts, or a rest if [degree] is null. */
data class RecipeStep(val degree: Int?, val duration: Beats) {
    init {
        require(duration > Beats.ZERO) { "a step must last at least some beats" }
    }
}

/** One bar's worth of [RecipeStep]s - the same shape [PhraseBar] gives a phrase, in degree space. */
data class RecipeBar(val steps: List<RecipeStep>) {
    init {
        require(steps.isNotEmpty()) { "a bar needs at least one step" }
    }
}

/**
 * A scale pattern, stored as a recipe rather than as notes: the degrees, their durations and how
 * they are grouped into bars, with the pitch left out on purpose. [resolve] is what turns it into an
 * actual [Phrase] once a player has picked which scale and which octave to play it in - the exact
 * same [Phrase] shape a library phrase is, so the Phrase Trainer and Phrase Quiz never have to know
 * a given phrase was generated rather than written down.
 *
 * A recipe is content, in the same sense a library [Phrase] is: authored once, in
 * [PatternLibrary] or a file it reads, and never built up by the player tapping degrees together on
 * a setup screen.
 */
data class PhraseRecipe(
    val id: String,
    val name: String,
    val description: String,
    val bars: List<RecipeBar>,
    /** How many consecutive bars make up one practice unit once this is resolved - see [Phrase.trainingBars]. */
    val trainingBars: Int = 1,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
) {
    init {
        require(id.isNotBlank()) { "a pattern needs an id" }
        require(bars.isNotEmpty()) { "pattern $id has no bars" }
        require(trainingBars >= 1) { "pattern $id needs at least one training bar" }
        bars.forEachIndexed { index, bar ->
            val total = bar.steps.fold(Beats.ZERO) { sum, step -> sum + step.duration }
            require(total.value == timeSignature.beatsPerBar.toDouble()) {
                "bar ${index + 1} of pattern $id lasts $total, but $timeSignature needs " +
                    "${timeSignature.beatsPerBar} beats"
            }
        }
    }

    /**
     * This recipe, played on [scale] from [root].
     *
     * Each degree becomes `root.transposedBy(scale.semitoneOffset(degree))` - the same mechanism
     * [com.example.saxtrainer.core.exercise.ExercisePattern] uses, applied directly here rather than
     * through that type, which would pull a dependency on `core:exercise` into this module for no
     * reason beyond one function call. The id carries the recipe, the scale and the root along with
     * it, so two resolutions of the same recipe in different keys are never mistaken for each other.
     */
    fun resolve(scale: Scale, root: Note, accidentalStyle: AccidentalStyle = AccidentalStyle.SHARPS): Phrase = Phrase(
        id = "${id}_${scale.id}_${root.midiNumber}",
        name = "$name (${root.name.displayName} ${scale.name})",
        description = description,
        bars = resolvedBars(scale, root),
        trainingBars = trainingBars,
        timeSignature = timeSignature,
        accidentalStyle = accidentalStyle,
    )

    /**
     * This recipe, played on [scale] from [root], in every octave of [octaves], one after another.
     *
     * Concatenated rather than interleaved: [octaves] sorted ascending, each contributing its own
     * bars in full before the next one starts - so [Phrase.trainingBars] still cuts the result into
     * practice units the same way it always does, just drawn from more bars than one octave alone
     * would produce. Choosing octaves 3 and 4 asks for training units that sometimes come from
     * either, not for the two octaves to be woven together note by note.
     */
    fun resolve(
        scale: Scale,
        root: NoteName,
        octaves: Collection<Int>,
        accidentalStyle: AccidentalStyle = AccidentalStyle.SHARPS,
    ): Phrase {
        require(octaves.isNotEmpty()) { "pattern $id needs at least one octave" }
        val sortedOctaves = octaves.distinct().sorted()
        return Phrase(
            id = "${id}_${scale.id}_${root.name}_${sortedOctaves.joinToString("-")}",
            name = "$name (${root.displayName} ${scale.name})",
            description = description,
            bars = sortedOctaves.flatMap { octave -> resolvedBars(scale, Note(root, octave)) },
            trainingBars = trainingBars,
            timeSignature = timeSignature,
            accidentalStyle = accidentalStyle,
        )
    }

    /**
     * Each degree as a pitch and, where the scale says how it is spelled, as the note it is written
     * as: on the letter [Scale.letterStep] names, counted up from the root's. The fourth of F major
     * is a B flat and the flat three of the C blues an E flat, whatever the phrase's fallback style
     * says. A scale that does not say, and a degree only a double sharp or flat could spell, are left
     * to the phrase's [Phrase.accidentalStyle], as every generated note used to be.
     */
    private fun resolvedBars(scale: Scale, root: Note): List<PhraseBar> {
        val rootWritten = SpelledNote.of(root)
        return bars.map { bar ->
            PhraseBar(
                bar.steps.map { step ->
                    val degree = step.degree ?: return@map ToneStep(null, step.duration)
                    val note = root.transposedBy(scale.semitoneOffset(degree))
                    val written = scale.letterStep(degree)?.let { letters ->
                        SpelledNote.onLetter(rootWritten.diatonicIndex + letters, note)
                    }
                    ToneStep(note, step.duration, written)
                },
            )
        }
    }
}
