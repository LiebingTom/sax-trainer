package com.example.saxtrainer.core.phrase

import com.example.saxtrainer.core.music.AccidentalStyle
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NotatedDuration
import com.example.saxtrainer.core.music.NoteValue
import com.example.saxtrainer.core.music.Scale
import com.example.saxtrainer.core.music.SpelledNote
import com.example.saxtrainer.core.music.TimeSignature
import com.example.saxtrainer.core.music.ToneStep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The on-disk shape of a library phrase and of a stored pattern.
 *
 * Deliberately the same shape for both, one field apart: a phrase's step names a [note] it sounds,
 * a pattern's names a [degree] of whatever scale it is later played on - see
 * [PhraseRecipe]. Exactly one of the two is expected; a step with neither is a rest, in either
 * document.
 *
 * Written by hand or by a tool, not by this app: nothing here is ever produced by the player tapping
 * something together, which is the whole point of storing content this way rather than generating it
 * from little pieces on a setup screen.
 */
@Serializable
data class StepJson(val note: String? = null, val degree: Int? = null, val duration: String)

@Serializable
data class BarJson(val steps: List<StepJson>)

@Serializable
data class PhraseJson(
    val id: String,
    val name: String,
    val description: String = "",
    val trainingBars: Int = 1,
    val timeSignature: String? = null,
    val accidentalStyle: String? = null,
    val bars: List<BarJson>,
)

@Serializable
data class PatternJson(
    val id: String,
    val name: String,
    val description: String = "",
    val trainingBars: Int = 1,
    val timeSignature: String? = null,
    val bars: List<BarJson> = emptyList(),
    /** In place of [bars]: the scale up and down, every note this long - see [ScaleRun]. */
    val run: RunJson? = null,
)

@Serializable
data class RunJson(val duration: String)

/**
 * The on-disk shape of a scale: its steps in semitones from the root, and - for a scale that is not
 * one note per letter - which letter each degree is written on. See [Scale.letterSteps].
 */
@Serializable
data class ScaleJson(
    val id: String,
    val name: String,
    val intervals: List<Int>,
    val letters: List<Int>? = null,
)

/** The manifest a folder of content files needs, since reading one back cannot list a directory. */
@Serializable
data class ContentManifestJson(val files: List<String>)

private val json = Json { ignoreUnknownKeys = true }

/**
 * A phrase library built from JSON text, exactly as a file-backed one on a real device would parse
 * it - see [PhraseLibrary] for where the default content is read from.
 *
 * A document that fails to parse, or a step whose note or duration this build cannot read, is left
 * out rather than crashing the app over one bad file: the same "every reader is total" rule
 * [com.example.saxtrainer.core.settings.ExerciseSetup] follows.
 */
fun parsePhrases(files: Collection<String>): List<Phrase> = files.mapNotNull { text ->
    runCatching { json.decodeFromString<PhraseJson>(text) }.getOrNull()?.let(::toPhrase)
}

fun parsePatterns(files: Collection<String>): List<PhraseRecipe> = files.mapNotNull { text ->
    runCatching { json.decodeFromString<PatternJson>(text) }.getOrNull()?.let(::toPattern)
}

/** The filenames a manifest lists, or none where it fails to parse at all. */
fun parseManifest(text: String): List<String> =
    runCatching { json.decodeFromString<ContentManifestJson>(text) }.getOrNull()?.files.orEmpty()

private fun toPhrase(source: PhraseJson): Phrase? {
    val timeSignature = timeSignatureOf(source.timeSignature)
    val bars = source.bars.map { bar ->
        PhraseBar(
            bar.steps.map { step ->
                val duration = durationOf(step.duration, timeSignature) ?: return null
                // Kept exactly as the file writes it: an "Eb4" is an E flat on the page, not the
                // D sharp its pitch would otherwise be spelled as.
                step.note?.let(::parseWrittenNote)?.let { ToneStep.of(it, duration) }
                    ?: ToneStep(null, duration)
            },
        )
    }
    return runCatching {
        Phrase(
            id = source.id,
            name = source.name,
            description = source.description,
            bars = bars,
            trainingBars = source.trainingBars,
            timeSignature = timeSignature,
            accidentalStyle = accidentalStyleOf(source.accidentalStyle),
        )
    }.getOrNull()
}

/** The scales built from JSON text - see [ScaleLibrary]. A file that does not parse is left out. */
fun parseScales(files: Collection<String>): List<Scale> = files.mapNotNull { text ->
    runCatching {
        val source = json.decodeFromString<ScaleJson>(text)
        if (source.letters == null) {
            Scale(id = source.id, name = source.name, intervals = source.intervals)
        } else {
            Scale(id = source.id, name = source.name, intervals = source.intervals, letterSteps = source.letters)
        }
    }.getOrNull()
}

private fun toPattern(source: PatternJson): PhraseRecipe? {
    val timeSignature = timeSignatureOf(source.timeSignature)
    val bars = source.bars.map { bar ->
        RecipeBar(bar.steps.map { step -> RecipeStep(step.degree, durationOf(step.duration, timeSignature) ?: return null) })
    }
    val run = source.run?.let { ScaleRun(durationOf(it.duration, timeSignature) ?: return null) }
    return runCatching {
        PhraseRecipe(
            id = source.id,
            name = source.name,
            description = source.description,
            bars = bars,
            trainingBars = source.trainingBars,
            timeSignature = timeSignature,
            run = run,
        )
    }.getOrNull()
}

/**
 * "C4", "F#4", "Bb3", "Cb4" - a letter, an optional sharp or flat, and the octave of that letter,
 * kept as written. Null if it is none of that.
 */
internal fun parseWrittenNote(token: String): SpelledNote? = SpelledNote.parse(token)

/** The pitch [parseWrittenNote] reads: "Cb4" sounds as B3, "B#3" as C4. */
internal fun parseNote(token: String): Note? = parseWrittenNote(token)?.note

/**
 * "quarter", "eighth.", and so on - a note value, dotted or not, read the way a musician says it.
 * Values joined by "+" are tied into one note: "half+eighth" lasts two and a half beats.
 */
internal fun durationOf(token: String, timeSignature: TimeSignature): Beats? =
    token.split("+").fold(Beats.ZERO) { total, part -> total + (noteValueOf(part.trim(), timeSignature) ?: return null) }

private fun noteValueOf(token: String, timeSignature: TimeSignature): Beats? {
    val dots = token.count { it == '.' }
    val value = NOTE_VALUES[token.trimEnd('.').lowercase()] ?: return null
    return NotatedDuration(value, dots).beats(timeSignature.beatUnit)
}

private fun timeSignatureOf(token: String?): TimeSignature {
    val (beats, unit) = token?.split("/")?.takeIf { it.size == 2 } ?: return TimeSignature.FOUR_FOUR
    val beatsPerBar = beats.toIntOrNull() ?: return TimeSignature.FOUR_FOUR
    val beatUnit = unit.toIntOrNull() ?: return TimeSignature.FOUR_FOUR
    return TimeSignature(beatsPerBar, beatUnit)
}

private fun accidentalStyleOf(token: String?): AccidentalStyle = when (token?.lowercase()) {
    "flats" -> AccidentalStyle.FLATS
    else -> AccidentalStyle.SHARPS
}

private val NOTE_VALUES = mapOf(
    "whole" to NoteValue.WHOLE,
    "half" to NoteValue.HALF,
    "quarter" to NoteValue.QUARTER,
    "eighth" to NoteValue.EIGHTH,
    "sixteenth" to NoteValue.SIXTEENTH,
)
