package com.example.saxtrainer.feature.metronome

import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.settings.ExerciseSetup
import com.example.saxtrainer.core.settings.exerciseSetup

/**
 * How the metronome's setup is written down and read back.
 *
 * One value, because the metronome asks for one thing. It is kept the same way as every other
 * exercise's setup rather than as an app setting, because it is what this tool was last set to and
 * not something any other tool obeys.
 */
internal fun metronomeSetup(bpm: Int): ExerciseSetup = exerciseSetup { put(KEY_TEMPO, bpm) }

/** A remembered tempo, held to what the app supports whatever was stored. */
internal fun ExerciseSetup.rememberedBpm(default: Int): Int =
    int(KEY_TEMPO, default).coerceIn(Tempo.SUPPORTED_RANGE)

private const val KEY_TEMPO = "tempo"
