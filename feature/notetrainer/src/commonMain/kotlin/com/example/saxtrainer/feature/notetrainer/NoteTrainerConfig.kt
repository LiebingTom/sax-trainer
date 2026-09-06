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
     * How long the right note has to keep sounding before it counts, when the player has not asked
     * for a hold of their own.
     *
     * Long enough that a note passed through on the way to another does not count, short enough
     * that nobody is being asked to hold it. The reaction time is still taken from the *first*
     * moment the note was heard, so this wait never inflates it, and neither does a longer hold.
     */
    val requiredCorrectSeconds: Double = 0.25,
    /**
     * Holds the setup screen offers, in seconds; zero is "until it is right", with no hold at all.
     *
     * A hold turns the exercise from finding a note into finding it and keeping it, which is a
     * different practice and the reason it is the player's to choose rather than a constant.
     */
    val holdOptions: List<Double> = listOf(0.0, 1.0, 2.0, 3.0),
    val defaultHoldSeconds: Double = 0.0,
    /** How long a wrong note has to sound before it is called one, rather than an unsettled attack. */
    val wrongPitchSeconds: Double = 0.15,
    /** How long the note stays up as correct before the next one. */
    val successSeconds: Double = 0.6,
    /** Reference tempo for the count-in; this trainer has no tempo of its own. */
    val tempo: Tempo = Tempo(60),
    val capture: AudioCaptureConfig = AudioCaptureConfig(),
    val listening: NoteListener.ListeningSettings = NoteListener.ListeningSettings(),
)
