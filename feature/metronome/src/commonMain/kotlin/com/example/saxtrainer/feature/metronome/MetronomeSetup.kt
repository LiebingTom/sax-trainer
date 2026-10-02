package com.example.saxtrainer.feature.metronome

import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.TimeSignature
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.exerciseSetup

/** The time signatures the metronome settings screen offers, in the order it lists them. */
val metronomeTimeSignatures: List<TimeSignature> = listOf(
    TimeSignature.FOUR_FOUR,
    TimeSignature.THREE_FOUR,
    TimeSignature.TWO_FOUR,
)

/**
 * How the metronome's setup is written down and read back.
 *
 * One exercise setup for the tempo, the time signature, the first-beat accent and the two half-beat
 * preferences, because it is kept the same way as every other tool's setup rather than as an app
 * setting: it is what this tool was last set to, not something any other tool obeys. The tempo is
 * remembered by the practice screen at Start, the rest by the settings screen as they are chosen;
 * whichever one writes writes all five, so none of them overwrites what the others chose.
 */
internal fun metronomeSetup(
    bpm: Int,
    timeSignature: TimeSignature,
    accentFirstBeat: Boolean,
    visualizeHalfBeats: Boolean,
    halfBeatSound: Boolean,
): ExerciseSetup = exerciseSetup {
    put(KEY_TEMPO, bpm)
    put(KEY_BEATS_PER_BAR, timeSignature.beatsPerBar)
    put(KEY_ACCENT_FIRST_BEAT, accentFirstBeat)
    put(KEY_VISUALIZE_HALF_BEATS, visualizeHalfBeats)
    put(KEY_HALF_BEAT_SOUND, halfBeatSound)
}

/** A remembered tempo, held to what the app supports whatever was stored. */
internal fun ExerciseSetup.rememberedBpm(default: Int): Int =
    int(KEY_TEMPO, default).coerceIn(Tempo.SUPPORTED_RANGE)

/** A remembered time signature, held to what the settings screen offers whatever was stored. */
internal fun ExerciseSetup.rememberedTimeSignature(default: TimeSignature): TimeSignature =
    metronomeTimeSignatures.firstOrNull {
        it.beatsPerBar == int(KEY_BEATS_PER_BAR, default.beatsPerBar)
    } ?: default

/** Whether beat one keeps its accent click, on by default so existing players notice nothing. */
internal fun ExerciseSetup.rememberedAccentFirstBeat(default: Boolean = true): Boolean =
    boolean(KEY_ACCENT_FIRST_BEAT, default)

/** Whether the midpoint between beats is marked with a small circle of its own, on by default. */
internal fun ExerciseSetup.rememberedVisualizeHalfBeats(default: Boolean = true): Boolean =
    boolean(KEY_VISUALIZE_HALF_BEATS, default)

/** Whether the midpoint between beats gets its own, softer click, on by default. */
internal fun ExerciseSetup.rememberedHalfBeatSound(default: Boolean = true): Boolean =
    boolean(KEY_HALF_BEAT_SOUND, default)

private const val KEY_TEMPO = "tempo"
private const val KEY_BEATS_PER_BAR = "beats_per_bar"
private const val KEY_ACCENT_FIRST_BEAT = "accent_first_beat"
private const val KEY_VISUALIZE_HALF_BEATS = "visualize_half_beats"
private const val KEY_HALF_BEAT_SOUND = "half_beat_sound"
