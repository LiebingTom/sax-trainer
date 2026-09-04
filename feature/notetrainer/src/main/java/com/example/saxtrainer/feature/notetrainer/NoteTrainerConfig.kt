package com.example.saxtrainer.feature.notetrainer

import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.listening.NoteListener
import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Tempo

/**
 * Settings of the note trainer that the player never sees.
 *
 * Deliberately small: anything a player picks lives in the UI state instead. There is no note
 * duration here and no timeout, because a note waits for the player rather than the other way
 * round.
 */
data class NoteTrainerConfig(
    val chart: FingeringChart = SaxophoneFingerings,
    /** Reaction targets offered on the setup screen; they colour the result and nothing else. */
    val targetOptions: List<Int> = listOf(2, 4, 8),
    val defaultTargetSeconds: Int = 4,
    /** How often the selected notes are worked through, unless a preset says otherwise. */
    val defaultRounds: Int = 1,
    /**
     * How long the right note has to keep sounding before it counts.
     *
     * Long enough that a note passed through on the way to another does not count, short enough
     * that nobody is being asked to hold it. The reaction time is still taken from the *first*
     * moment the note was heard, so this wait never inflates it.
     */
    val requiredCorrectSeconds: Double = 0.25,
    /** How long a wrong note has to sound before it is called one, rather than an unsettled attack. */
    val wrongPitchSeconds: Double = 0.15,
    /** How long the note stays up as correct before the next one. */
    val successSeconds: Double = 0.6,
    /** Reference tempo for the count-in; this trainer has no tempo of its own. */
    val tempo: Tempo = Tempo(60),
    val capture: AudioCaptureConfig = AudioCaptureConfig(),
    val listening: NoteListener.ListeningSettings = NoteListener.ListeningSettings(),
)
