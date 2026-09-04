package com.example.saxtrainer.core.audio.metronome

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Pre-renders the two click sounds once, so the render loop only copies samples and never
 * allocates or computes transcendental functions while audio is flowing.
 */
class ClickSynth(sampleRate: Int) {

    val accentClick: ShortArray = renderClick(sampleRate, ACCENT_FREQUENCY_HZ, ACCENT_AMPLITUDE)
    val normalClick: ShortArray = renderClick(sampleRate, NORMAL_FREQUENCY_HZ, NORMAL_AMPLITUDE)

    private fun renderClick(sampleRate: Int, frequencyHz: Double, amplitude: Double): ShortArray {
        val length = (sampleRate * CLICK_DURATION_SECONDS).toInt().coerceAtLeast(1)
        val samples = ShortArray(length)
        val angularStep = 2.0 * PI * frequencyHz / sampleRate
        for (i in 0 until length) {
            val envelope = exp(-DECAY * i / length)
            val value = amplitude * envelope * sin(angularStep * i)
            samples[i] = (value * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private companion object {
        const val CLICK_DURATION_SECONDS = 0.035
        const val ACCENT_FREQUENCY_HZ = 1600.0
        const val NORMAL_FREQUENCY_HZ = 1000.0
        const val ACCENT_AMPLITUDE = 0.75
        const val NORMAL_AMPLITUDE = 0.5
        const val DECAY = 6.0
    }
}
