package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.dsp.spectrum.SpectrumConfig
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName

/**
 * Tuning knobs of the exercise. Signal thresholds and update rates live here rather than in the UI
 * or inside the DSP algorithms, so they can be adjusted (or later made user settings) in one place.
 */
data class LongToneConfig(
    val capture: AudioCaptureConfig = AudioCaptureConfig(),
    val spectrum: SpectrumConfig = SpectrumConfig(),
    val durationsSeconds: List<Int> = listOf(8, 15, 30),
    val defaultDurationSeconds: Int = 15,
    val countdownSeconds: Int = 3,
    /** Level above which the app considers a note to be sounding. */
    val gateOpenDbfs: Float = -42f,
    val gateCloseDbfs: Float = -48f,
    /** Bottom of the level meter. */
    val meterFloorDbfs: Float = -60f,
    /** Highest frequency drawn in the spectrum view. The full spectrum is always computed. */
    val displayMaxFrequencyHz: Float = 3_000f,
    /** Number of bars in the spectrum view; bins are folded into this many buckets. */
    val displayBands: Int = 96,
    /** UI refresh interval, about 25 updates per second. */
    val uiUpdateIntervalMs: Long = 40,
    val lowestNote: Note = Note(NoteName.C, 3),
    val highestNote: Note = Note(NoteName.C, 5),
    val defaultNote: Note = Note(NoteName.G, 3),
)
