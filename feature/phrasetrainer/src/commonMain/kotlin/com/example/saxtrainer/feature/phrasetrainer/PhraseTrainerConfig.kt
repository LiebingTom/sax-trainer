package com.example.saxtrainer.feature.phrasetrainer

import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.listening.NoteListener
import com.example.saxtrainer.core.music.Tempo

/**
 * Settings of the trainer that the player never sees.
 *
 * Deliberately small: anything a player picks lives in the UI state instead.
 */
data class PhraseTrainerConfig(
    /** How long a unit is shown before it is hidden and has to be played from memory. */
    val defaultShowSeconds: Double = 2.5,
    /** The speed a phrase not otherwise marked with one is practised at. */
    val defaultTempo: Tempo = Tempo(80),
    /**
     * How long a right note has to keep sounding before it counts, when duration is not being
     * checked. Long enough that a note passed through on the way to another does not count.
     */
    val requiredCorrectSeconds: Double = 0.2,
    /** How long a wrong note has to sound before it is called one, rather than an unsettled attack. */
    val wrongPitchSeconds: Double = 0.15,
    val capture: AudioCaptureConfig = AudioCaptureConfig(),
    val listening: NoteListener.ListeningSettings = NoteListener.ListeningSettings(),
)
