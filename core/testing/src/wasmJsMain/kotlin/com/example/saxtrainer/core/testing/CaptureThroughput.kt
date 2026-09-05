package com.example.saxtrainer.core.testing

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.capture.WebAudioCapture

/**
 * What the real web capture managed while a trainer was running on top of it.
 *
 * `:core:audio` measures the chain on its own - `getUserMedia`, the worklet, the crossing into
 * Kotlin and the shared DSP, with nothing drawing. This is the same chain under the thing that
 * actually competes with it: a Compose screen redrawing on the one thread Kotlin/Wasm has.
 *
 * [dropped] is the number that matters. The worklet cannot be pushed back on, so a completed frame
 * the collector was too busy to take is discarded and counted, which makes it a direct reading of
 * main-thread starvation rather than an inference from one.
 */
class Throughput(
    val label: String,
    /** Seconds of audio the browser's own clock says went past while this was measured. */
    val seconds: Double,
    val sampleRate: Int,
    val hopSize: Int,
    val frames: Int,
    val dropped: Int,
    val poolMisses: Int,
) {
    /** Frames a second the hop size asks for at the rate the browser chose. */
    val expectedPerSecond: Double get() = sampleRate.toDouble() / hopSize

    val perSecond: Double get() = if (seconds > 0.0) frames / seconds else 0.0

    /** How much of what was asked for actually arrived. One is perfect; below one is falling behind. */
    val keptUp: Double get() = if (expectedPerSecond > 0.0) perSecond / expectedPerSecond else 0.0

    fun print() {
        println("--- $label, real web capture under a running screen")
        println("  sample rate / hop     $sampleRate Hz / $hopSize")
        println("  measured over         $seconds s")
        println("  frames                $frames ($perSecond /s, asked for $expectedPerSecond /s)")
        println("  kept up               ${keptUp * 100} %")
        println("  dropped / pool misses $dropped / $poolMisses")
    }
}

/**
 * Runs [capture] under whatever is already on screen until [frames] of audio have been analysed,
 * then reports what got through. The screen keeps drawing throughout, which is the point.
 */
@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.measureThroughput(
    label: String,
    capture: WebAudioCapture,
    frames: Int = MEASURED_FRAMES,
    config: AudioCaptureConfig = AudioCaptureConfig(),
): Throughput {
    // From the first frame rather than from the call: opening a context, loading the worklet and
    // resolving getUserMedia are startup, and averaging them in would understate the steady state.
    awaitInRealTime("the first frame of audio") { capture.framesEmitted > 0 }
    // The audio clock, not the wall clock. How much audio went past is what the frames should be
    // counted against, and it is free of the polling below: a stall shows up as a dropped frame,
    // never as a shorter second.
    val fromSeconds = capture.lastCapturedAtSeconds
    val from = capture.framesEmitted
    val fromDropped = capture.droppedFrames
    // Deltas, not totals. Opening a context, loading the worklet and composing a screen for the
    // first time all happen before the first frame, and a pool miss there says nothing about
    // whether the chain keeps up once it is running.
    val fromMisses = capture.poolMisses
    awaitInRealTime("$frames frames of audio") { capture.framesEmitted - from >= frames }

    return Throughput(
        label = label,
        seconds = capture.lastCapturedAtSeconds - fromSeconds,
        sampleRate = capture.sampleRate,
        hopSize = config.hopSize,
        frames = capture.framesEmitted - from,
        dropped = capture.droppedFrames - fromDropped,
        poolMisses = capture.poolMisses - fromMisses,
    ).also { it.print() }
}

/** Enough frames to average over without turning a browser test into a long one: about two seconds. */
private const val MEASURED_FRAMES = 40
