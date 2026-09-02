package com.example.saxtrainer.core.dsp

import kotlin.math.PI
import kotlin.math.sin

/** Test signal helper shared by the DSP tests. */
fun sineWave(
    frequencyHz: Double,
    sampleRate: Int,
    length: Int,
    amplitude: Double = 1.0,
    phase: Double = 0.0,
): FloatArray = FloatArray(length) { i ->
    (amplitude * sin(2.0 * PI * frequencyHz * i / sampleRate + phase)).toFloat()
}
