package com.example.saxtrainer.feature.fingering

import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.listening.NoteListener
import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.Beats

/**
 * Settings of the fingering exercise.
 *
 * How long a note lasts is a number of beats, as it is everywhere else in the app. What that comes
 * to in seconds is worked out from [tempo] at the moment it is needed and nowhere else, so there is
 * one duration here rather than a musical one and a clock one that have to be kept in step.
 */
data class FingeringConfig(
    val chart: FingeringChart = SaxophoneFingerings,
    /** How long a note is worked on before the fingering is shown; offered on the setup screen. */
    val beatOptions: List<Int> = listOf(3, 5, 8),
    val defaultBeats: Int = 5,
    /** How often the selected notes are worked through, unless a preset says otherwise. */
    val defaultRounds: Int = 1,
    /**
     * The speed this exercise counts at, and what the player can change it to.
     *
     * Offered rather than fixed, because a length in beats says nothing on its own: five beats is
     * five seconds at 60 and half that at 120, and the player is the one who has to know which.
     */
    val defaultTempo: Tempo = Tempo(60),
    val tempoRange: IntRange = Tempo.SUPPORTED_RANGE,
    /** How often the remaining time is refreshed. */
    val tickIntervalMs: Long = 50,
    /** Listening is off by default: the trainer must work in silence. */
    val checkPlayingByDefault: Boolean = false,
    /**
     * How long the right note has to sound before it counts as played. Short, because this is a
     * fingering check and not a tone exercise - long enough to rule out a note passed through on
     * the way to another, not long enough to ask anybody to hold it.
     */
    val requiredCorrectSeconds: Float = 0.3f,
    /**
     * How long the fingering stays up as the answer when the note was *not* played in time.
     *
     * A note the player did get is held by their own [FingeringUiState.successDelaySeconds]
     * instead; this is only the consolation look at a grip that could not be recalled.
     */
    val revealSeconds: Float = 1.2f,
    /**
     * Waits the setup screen offers after a note has been played right, in seconds. Zero moves on
     * at once.
     */
    val successDelayOptions: List<Float> = listOf(0f, 1f, 2f, 3f),
    val defaultSuccessDelaySeconds: Float = 1f,
    val capture: AudioCaptureConfig = AudioCaptureConfig(),
    val listening: NoteListener.ListeningSettings = NoteListener.ListeningSettings(),
) {
    /** [beatOptions] as durations, which is what everything past the setup screen deals in. */
    val beatChoices: List<Beats> get() = beatOptions.map(Beats::of)
}
