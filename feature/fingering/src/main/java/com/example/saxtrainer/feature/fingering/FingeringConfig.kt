package com.example.saxtrainer.feature.fingering

import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.fingering.FingeringChart
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Tempo

/** What listening to the player should do, when it is switched on at all. */
enum class CheckMode(val displayName: String, val description: String) {
    /** Show whether the right note is sounding; the exercise still moves on by itself. */
    SHOW("Just show me", "Marks the note green while you play it correctly"),

    /** Move on as soon as the note is played correctly, with the timer as a fallback. */
    ADVANCE("Move on when correct", "Continues early once you hold the right note"),
}

/**
 * Settings of the fingering exercise.
 *
 * The trainer talks to the player in seconds, which is all a beginner needs, but the exercise itself
 * is still built from musical durations: seconds are converted at [tempo], which stays fixed because
 * there is nothing here for a tempo control to do.
 */
data class FingeringConfig(
    val chart: FingeringChart = SaxophoneFingerings,
    /** Display times offered on the setup screen. */
    val secondsOptions: List<Int> = listOf(3, 5, 8),
    val defaultSeconds: Int = 5,
    /** How often the selected notes are worked through. */
    val rounds: Int = 1,
    /** Reference tempo used to turn seconds into beats; not shown to the player. */
    val tempo: Tempo = Tempo(60),
    /** How often the remaining time is refreshed. */
    val tickIntervalMs: Long = 50,
    /** Listening is off by default: the trainer must work in silence. */
    val checkPlayingByDefault: Boolean = false,
    val defaultCheckMode: CheckMode = CheckMode.SHOW,
    /** How long the right note must sound before [CheckMode.ADVANCE] moves on. */
    val requiredCorrectSeconds: Float = 0.4f,
    val capture: AudioCaptureConfig = AudioCaptureConfig(),
    val listening: NoteListener.ListeningSettings = NoteListener.ListeningSettings(),
)
