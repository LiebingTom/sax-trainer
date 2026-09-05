package com.example.saxtrainer.probe

import kotlinx.coroutines.await
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import org.w3c.dom.MessageEvent

/**
 * M3 PROBE - TEMPORARY WEB-SPECIFIC CODE. Not production. Delete with the probe/ directory.
 *
 * The capture chain: a source (microphone or tone) into an AudioWorklet, the worklet's hopSize
 * batches into Kotlin, and a sliding analysis window handed to [onFrame].
 *
 * Two things here are deliberately the same as `AudioRecordCapture` on Android, because M4 has to
 * be able to put both behind one interface:
 *
 * - the window is a single [FloatArray] allocated once and slid left by hopSize each time, so the
 *   real-time path allocates nothing per frame;
 * - [onFrame] must finish with the window before it returns, exactly as `AudioFrame.samples` is
 *   documented to require.
 *
 * The batch buffer is posted back to the worklet as soon as its samples have been copied out, so
 * the JavaScript side can refill it rather than allocate a new one.
 */
class ProbeCapture(
    private val frameSize: Int,
    private val hopSize: Int,
    private val bufferPoolSize: Int = 4,
) {
    /** What the browser actually gave us, which is not necessarily what was asked for. */
    var sampleRate: Int = 0
        private set

    var baseLatencySeconds: Double = -1.0
        private set

    var outputLatencySeconds: Double = -1.0
        private set

    private var context: AudioContextHandle? = null
    private var source: AudioNodeHandle? = null
    private var node: AudioNodeHandle? = null
    private var microphone: MediaStreamHandle? = null

    private val window = FloatArray(frameSize)
    private var filled = 0
    private var samplesRead = 0L
    private var samplesSinceLastFrame = 0

    /** Wall-clock milliseconds spent copying out of JavaScript, per batch. */
    val copyMillis = Statistic()

    /** Wall-clock milliseconds spent in [onFrame], which is where the DSP runs. */
    val frameMillis = Statistic()

    /** Milliseconds between one batch arriving and the next. */
    val hopIntervalMillis = Statistic()

    var batchesReceived = 0
        private set

    var framesEmitted = 0
        private set

    /** Batches the worklet had to allocate because the pool was empty. Allocation pressure. */
    var poolMisses = 0
        private set

    /** Render quanta the worklet saw. Compared with batches, this says whether any were dropped. */
    var quanta = 0
        private set

    private var lastArrivalMillis = 0.0

    /**
     * Audio-clock time of the most recent batch, and the wall-clock reading taken when it arrived.
     * Together they give a latency free of any assumption about how long the worklet buffered.
     */
    var lastCapturedAt = 0.0
        private set

    private var onFrame: ((FloatArray, Double) -> Unit)? = null

    /**
     * Starts capture. [requestedSampleRate] of zero takes whatever the browser offers; anything else
     * is lever three of the performance plan and may be ignored by the browser.
     */
    suspend fun start(
        source: Source,
        requestedSampleRate: Double = 0.0,
        toneHz: Double = 0.0,
        workletUrl: String = "capture-worklet.js",
        onFrame: (window: FloatArray, capturedAtSeconds: Double) -> Unit,
    ) {
        this.onFrame = onFrame

        val context = newAudioContext(requestedSampleRate)
        this.context = context
        sampleRate = sampleRateOf(context).toInt()

        addWorkletModule(context, workletUrl).await<JsAny?>()
        if (stateOf(context) != "running") {
            resume(context).await<JsAny?>()
        }

        baseLatencySeconds = baseLatencyOf(context)
        outputLatencySeconds = outputLatencyOf(context)

        val node = when (source) {
            Source.MICROPHONE -> {
                val stream = getMicrophone().await<MediaStreamHandle>()
                microphone = stream
                microphoneSource(context, stream)
            }

            Source.TONE -> toneSource(context, toneHz, TONE_GAIN)
            // A tone at zero gain rather than nothing connected, so the chain is identical and only
            // the audio is missing: that is what makes it a test of silence.
            Source.SILENCE -> toneSource(context, toneHz, 0.0)
            Source.NOISE -> noiseSource(context, NOISE_GAIN)
        }
        this.source = node

        val capture = captureNode(context, hopSize, bufferPoolSize)
        this.node = capture
        val port = portOf(capture)
        port.onmessage = { event: MessageEvent -> receive(port, event) }
        connect(node, capture)
    }

    /**
     * Turns the tone source on or off. Used to give an onset an exact moment, which is what the
     * end-to-end latency is measured from.
     */
    fun setSourceGain(gain: Double) {
        source?.let { setGain(it, gain) }
    }

    /** Retunes the tone source. Only meaningful when capture was started without a microphone. */
    fun retune(frequencyHz: Double) {
        source?.let { setFrequency(it, frequencyHz) }
    }

    fun stop() {
        node?.let { disconnect(it) }
        source?.let { disconnect(it) }
        microphone?.let { stopTracks(it) }
        context?.let { close(it) }
        node = null
        source = null
        microphone = null
        context = null
        onFrame = null
    }

    private fun receive(port: org.w3c.dom.MessagePort, event: MessageEvent) {
        val arrivedAt = performanceNow()
        if (batchesReceived > 0) hopIntervalMillis.add(arrivedAt - lastArrivalMillis)
        lastArrivalMillis = arrivedAt
        batchesReceived++

        @Suppress("UNCHECKED_CAST")
        val message = event.data as HopMessage
        val samples = samplesOf(message)
        lastCapturedAt = capturedAtOf(message)
        poolMisses = poolMissesOf(message)
        quanta = quantaOf(message)

        val copyStart = performanceNow()
        appendToWindow(samples)
        // Back to the worklet the moment its contents are safely in Kotlin's own array.
        returnBuffer(port, samples)
        copyMillis.add(performanceNow() - copyStart)

        filled = minOf(frameSize, filled + hopSize)
        samplesRead += hopSize
        samplesSinceLastFrame += hopSize
        if (filled == frameSize) {
            samplesSinceLastFrame = 0
            framesEmitted++
            val frameStart = performanceNow()
            onFrame?.invoke(window, lastCapturedAt)
            frameMillis.add(performanceNow() - frameStart)
        }
    }

    /**
     * Slides the window left by one hop and copies the new batch into the end.
     *
     * The copy is per element because Kotlin/Wasm has no view onto a JavaScript typed array. That
     * crossing is one of the things this probe exists to measure, which is why it is timed.
     */
    private fun appendToWindow(samples: Float32Array) {
        window.copyInto(window, 0, hopSize, window.size)
        val start = window.size - hopSize
        for (i in 0 until hopSize) {
            window[start + i] = samples[i]
        }
    }

    /** Where the samples come from. The worklet cannot tell the difference. */
    enum class Source { MICROPHONE, TONE, NOISE, SILENCE }

    private companion object {
        /** Well above the gate, well below clipping. */
        const val TONE_GAIN = 0.25

        /** Quieter than the tone and still well above the gate: loud, and with no pitch in it. */
        const val NOISE_GAIN = 0.15
    }
}

/** Mean, maximum and a rough p95 over a bounded sample, so a long run does not grow without limit. */
class Statistic(private val capacity: Int = 4096) {
    private val values = DoubleArray(capacity)
    private var count = 0
    private var next = 0

    var total = 0
        private set

    var max = 0.0
        private set

    fun add(value: Double) {
        total++
        if (value > max) max = value
        values[next] = value
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    val mean: Double get() = if (count == 0) 0.0 else (0 until count).sumOf { values[it] } / count

    /** Not exact: the percentile of the last [capacity] samples, which is what a probe needs. */
    fun percentile(fraction: Double): Double {
        if (count == 0) return 0.0
        val sorted = DoubleArray(count) { values[it] }
        sorted.sort()
        return sorted[((count - 1) * fraction).toInt()]
    }
}
