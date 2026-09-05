package com.example.saxtrainer.core.audio

/**
 * Audio format used across the app. The sample rate is data, never a constant baked into DSP or
 * feature code, so a device that only supports a different rate can be handled by changing this
 * configuration alone.
 */
data class AudioConfig(
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000
    }
}
