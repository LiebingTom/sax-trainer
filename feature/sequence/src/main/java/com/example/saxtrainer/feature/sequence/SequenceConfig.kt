package com.example.saxtrainer.feature.sequence

import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.listening.NoteListener
import com.example.saxtrainer.core.exercise.SequenceDefinition
import com.example.saxtrainer.core.exercise.SequenceLibrary
import com.example.saxtrainer.core.exercise.TimingTolerance

/**
 * Settings of the sequence trainer that are not the player's to choose.
 *
 * Deliberately small. Anything a player picks - which sequence, how fast, what is checked - lives in
 * the UI state instead, and anything musical lives in the sequence itself.
 */
data class SequenceConfig(
    val library: List<SequenceDefinition> = SequenceLibrary.all,
    val defaultSequenceId: String = SequenceLibrary.cMajorUp.id,
    /** How many times through the sequence, unless a preset says otherwise. */
    val defaultRepeats: Int = 5,
    val repeatRange: IntRange = 1..12,
    val defaultAddInverse: Boolean = false,
    /** How close to the written beat still counts as on it. */
    val timing: TimingTolerance = TimingTolerance(),
    /**
     * Bars counted before the first note. Always counted - this practice brings its own count-in
     * and does not read the general setting - but only heard when the metronome is on.
     */
    val countInBars: Int = 1,
    /** How often the exercise position is recomputed. */
    val tickIntervalMs: Long = 25,
    val capture: AudioCaptureConfig = AudioCaptureConfig(),
    val listening: NoteListener.ListeningSettings = NoteListener.ListeningSettings(),
)
