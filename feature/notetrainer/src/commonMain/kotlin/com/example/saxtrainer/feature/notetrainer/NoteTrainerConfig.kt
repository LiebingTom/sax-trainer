package com.example.saxtrainer.feature.notetrainer

import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.listening.NoteListener
import com.example.saxtrainer.core.exercise.HeldNote
import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.Beats

/**
 * Settings of the note trainer that the player never sees.
 *
 * Deliberately small: anything a player picks lives in the UI state instead. There is no note
 * duration here and no timeout, because a note waits for the player rather than the other way
 * round.
 */
data class NoteTrainerConfig(
    val chart: FingeringChart = SaxophoneFingerings,
    /**
     * What counts as a quick answer, in seconds. It colours the result and nothing else - a slow
     * note is never a failed one. Fixed rather than offered: too fine a thing to ask a player to set.
     */
    val quickSeconds: Int = 2,
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
     * Holds the setup screen offers, in beats; zero is "until it is right", with no hold at all.
     *
     * A hold turns the exercise from finding a note into finding it and keeping it, which is a
     * different practice and the reason it is the player's to choose rather than a constant. It is
     * the one duration here that is music - the player is being asked to sound a note for a length
     * of it - so it is counted in beats, in the same lengths Long Tones offers, and turned into
     * seconds against [defaultTempo]. The waits either side of it are not music and stay in seconds.
     */
    val holdBeatOptions: List<Int> = listOf(0) + HeldNote.beatOptions,
    val defaultHoldBeats: Int = 0,
    /** How long a wrong note has to sound before it is called one, rather than an unsettled attack. */
    val wrongPitchSeconds: Double = 0.15,
    /** How long the note stays up as correct before the next one. */
    val successSeconds: Double = 0.6,
    /**
     * The speed this trainer counts at: the count-in, and the hold a player is asked to keep.
     *
     * Fixed, and the same as Long Tones: a beat is a second, so a hold needs no explaining.
     */
    val defaultTempo: Tempo = HeldNote.tempo,
    val capture: AudioCaptureConfig = AudioCaptureConfig(),
    val listening: NoteListener.ListeningSettings = NoteListener.ListeningSettings(),
) {
    /** [holdBeatOptions] as durations, which is what the rest of the trainer deals in. */
    val holdChoices: List<Beats> get() = holdBeatOptions.map(Beats::of)
}
