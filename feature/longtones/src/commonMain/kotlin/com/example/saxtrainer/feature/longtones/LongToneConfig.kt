package com.example.saxtrainer.feature.longtones

import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.dsp.pitch.YinConfig
import com.example.saxtrainer.core.dsp.spectrum.SpectrumConfig
import com.example.saxtrainer.core.exercise.HeldNote
import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.Tempo

/**
 * Tuning knobs of the exercise. Signal thresholds and update rates live here rather than in the UI
 * or inside the DSP algorithms, so they can be adjusted (or later made user settings) in one place.
 */
data class LongToneConfig(
    val capture: AudioCaptureConfig = AudioCaptureConfig(),
    val spectrum: SpectrumConfig = SpectrumConfig(),
    /** Note lengths offered in the UI, in beats: every beat from four up to the longest held note. */
    val beatOptions: List<Int> = HeldNote.beatOptionsFrom(4),
    /**
     * The speed a note's beats are counted at. Fixed rather than offered, so a beat is a second and
     * nothing on screen has to explain the conversion; only a test changes it, to run faster.
     */
    val defaultTempo: Tempo = HeldNote.tempo,
    /** Bars of count-in before the first note. */
    val countInBars: Int = 1,
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
    /** Pitch estimation settings; the range covers the sounding range of the common saxophones. */
    val yin: YinConfig = YinConfig(minFrequencyHz = 60f, maxFrequencyHz = 1_500f),
    /** Estimates below this confidence are discarded rather than shown or averaged. */
    val minPitchConfidence: Float = 0.75f,
    /** Beyond this deviation the player is treated as playing a different note, not as out of tune. */
    val offTargetCents: Float = 100f,
    /** Half width of the cents meter. */
    val centsDisplayRange: Float = 50f,
    /** Which notes can be chosen to hold at all: the ones the horn has a fingering for. */
    val chart: FingeringChart = SaxophoneFingerings,
) {
    val beatChoices: List<Beats> get() = beatOptions.map(Beats::of)

    /** How long each note is held until the player chooses otherwise: the shortest choice. */
    val defaultBeatsPerNote: Beats get() = beatChoices.first()
}
