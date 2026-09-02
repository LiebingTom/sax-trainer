package com.example.saxtrainer.core.exercise

import com.example.saxtrainer.core.music.Beats
import com.example.saxtrainer.core.music.ToneSequence
import com.example.saxtrainer.core.music.ToneStep

/** How far through a sequence the player is. Indices are zero based; [displayPosition] is not. */
data class SequenceProgress(
    val stepIndex: Int,
    val stepCount: Int,
    val completedBeats: Beats,
    val totalBeats: Beats,
) {
    /** Position as a musician would count it, "3" of "3 / 7". */
    val displayPosition: Int get() = stepIndex + 1

    val fraction: Float
        get() = if (totalBeats.value <= 0.0) 0f else (completedBeats.value / totalBeats.value).toFloat()
}

/**
 * A cursor over a [ToneSequence].
 *
 * Deliberately passive: it holds a position and moves when told to, and it never decides *why* a
 * note ended. Long tone practice advances it from audio time, a rhythm trainer would advance it on
 * a detected onset, a fingering trainer on a key change. Keeping that decision outside is what lets
 * every feature share the same sequences, so no clock, audio or feedback logic belongs in here.
 */
class SequenceRunner(val sequence: ToneSequence) {

    private var index = 0
    private var finished = false

    val currentIndex: Int get() = index

    /** The note being practised. Stays on the last note once the sequence is finished. */
    val currentStep: ToneStep get() = sequence.steps[index]

    val isFinished: Boolean get() = finished

    val isLastStep: Boolean get() = index == sequence.steps.lastIndex

    val progress: SequenceProgress
        get() = SequenceProgress(
            stepIndex = index,
            stepCount = sequence.steps.size,
            completedBeats = if (finished) sequence.totalBeats else sequence.startBeatOf(index),
            totalBeats = sequence.totalBeats,
        )

    /** The next [count] notes, empty at the end of the sequence. */
    fun upcoming(count: Int): List<ToneStep> {
        require(count >= 0) { "count cannot be negative" }
        if (finished) return emptyList()
        return sequence.steps.drop(index + 1).take(count)
    }

    /**
     * Moves to the next note. Returns false when the sequence is over, in which case the cursor
     * stays on the last note and [isFinished] becomes true - it never runs off the end.
     */
    fun advance(): Boolean {
        if (finished) return false
        if (isLastStep) {
            finished = true
            return false
        }
        index++
        return true
    }

    fun reset() {
        index = 0
        finished = false
    }
}
