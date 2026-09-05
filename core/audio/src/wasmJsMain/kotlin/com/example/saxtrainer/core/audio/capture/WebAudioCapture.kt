package com.example.saxtrainer.core.audio.capture

import com.example.saxtrainer.core.audio.web.AudioContextHandle
import com.example.saxtrainer.core.audio.web.AudioNodeHandle
import com.example.saxtrainer.core.audio.web.CAPTURE_WORKLET_SOURCE
import com.example.saxtrainer.core.audio.web.HopMessage
import com.example.saxtrainer.core.audio.web.MediaStreamHandle
import com.example.saxtrainer.core.audio.web.addWorkletModule
import com.example.saxtrainer.core.audio.web.baseLatencyOf
import com.example.saxtrainer.core.audio.web.blobUrl
import com.example.saxtrainer.core.audio.web.capturedAtOf
import com.example.saxtrainer.core.audio.web.captureNode
import com.example.saxtrainer.core.audio.web.closeContext
import com.example.saxtrainer.core.audio.web.connect
import com.example.saxtrainer.core.audio.web.disconnect
import com.example.saxtrainer.core.audio.web.errorNameOf
import com.example.saxtrainer.core.audio.web.MicrophoneResult
import com.example.saxtrainer.core.audio.web.microphoneSource
import com.example.saxtrainer.core.audio.web.newAudioContext
import com.example.saxtrainer.core.audio.web.outputLatencyOf
import com.example.saxtrainer.core.audio.web.performanceNow
import com.example.saxtrainer.core.audio.web.poolMissesOf
import com.example.saxtrainer.core.audio.web.portOf
import com.example.saxtrainer.core.audio.web.quantaOf
import com.example.saxtrainer.core.audio.web.requestMicrophone
import com.example.saxtrainer.core.audio.web.resume
import com.example.saxtrainer.core.audio.web.returnBuffer
import com.example.saxtrainer.core.audio.web.revokeBlobUrl
import com.example.saxtrainer.core.audio.web.sampleRateOf
import com.example.saxtrainer.core.audio.web.samplesOf
import com.example.saxtrainer.core.audio.web.stateOf
import com.example.saxtrainer.core.audio.web.stopTracks
import com.example.saxtrainer.core.audio.web.streamOf
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import org.w3c.dom.MessageEvent
import org.w3c.dom.MessagePort

/**
 * [AudioCapture] backed by Web Audio: `getUserMedia` into an `AudioWorkletNode`, whose batches cross
 * into Kotlin and become the same sliding analysis window Android produces.
 *
 * The chain, and the reason for each link, was measured by the M3 probe: 44.5 ms from a note
 * starting to it being named, 2.0 ms of DSP per frame, 4.9 % of the main thread, and no allocation
 * per hop on the audio thread.
 *
 * Two differences from [com.example.saxtrainer.core.audio.capture.AudioRecordCapture] follow from
 * the platform rather than from choice, and both are visible only here:
 *
 * - **The sample rate is the browser's, not ours.** [AudioCaptureConfig] names a preference; what
 *   the context actually gave us is reported on every [AudioFrame] and is what the DSP configures
 *   itself from. [requestedSampleRateHz] is the lever for asking for something else, and the browser
 *   may ignore it.
 * - **The producer cannot be pushed back on.** Android blocks its recorder thread while the
 *   collector works; the worklet keeps running regardless. So a frame is handed out only while the
 *   collector is not already holding one, and the frames missed in between are counted in
 *   [droppedFrames] rather than queued - stale audio is worse than no audio in every trainer here.
 */
class WebAudioCapture(
    private val requestedSampleRateHz: Double = 0.0,
    private val bufferPoolSize: Int = DEFAULT_POOL_SIZE,
) : AudioCapture {

    /** What the browser actually gave us, once capture has started. Zero before that. */
    var sampleRate: Int = 0
        private set

    /** What the browser admits to buffering, in seconds, or -1 where it does not say. */
    var baseLatencySeconds: Double = -1.0
        private set

    var outputLatencySeconds: Double = -1.0
        private set

    /** Batches the worklet posted. */
    var batchesReceived: Int = 0
        private set

    var framesEmitted: Int = 0
        private set

    /** Completed frames the collector was too busy to take. Main-thread starvation, directly. */
    var droppedFrames: Int = 0
        private set

    /** Batches the worklet had to allocate because its pool was empty. Allocation pressure. */
    var poolMisses: Int = 0
        private set

    /** Render quanta the worklet has seen. Against [batchesReceived], says whether any were lost. */
    var quanta: Int = 0
        private set

    /** Total milliseconds spent copying samples out of JavaScript. Divide by [batchesReceived]. */
    var copyMillisTotal: Double = 0.0
        private set

    /** Audio-clock time of the most recent batch, in seconds since the context was created. */
    var lastCapturedAtSeconds: Double = 0.0
        private set

    override fun frames(config: AudioCaptureConfig): Flow<AudioCaptureEvent> = flow {
        val session = Session(config)
        val failure = session.open()
        if (failure != null) {
            session.shutDown()
            emit(AudioCaptureEvent.Failed(failure))
            return@flow
        }
        try {
            // The channel only says "a frame is waiting". The frame itself is the reused window, so
            // conflating is right: two signals arriving before either is taken mean one frame, and
            // the one that was missed is already counted as dropped.
            for (signal in session.ready) {
                emit(AudioCaptureEvent.Frame(session.frame))
                session.handedOut = false
            }
        } finally {
            session.shutDown()
        }
    }

    private inner class Session(private val config: AudioCaptureConfig) {
        val ready = Channel<Unit>(Channel.CONFLATED)

        private var context: AudioContextHandle? = null
        private var source: AudioNodeHandle? = null
        private var worklet: AudioNodeHandle? = null
        private var port: MessagePort? = null
        private var microphone: MediaStreamHandle? = null

        /** Filled by the worklet, slid by a hop each batch. Never handed out while in use. */
        private val window = FloatArray(config.frameSize)

        /**
         * What the collector sees. A second array, because nothing can stop the worklet writing
         * into [window] while the collector still has the previous frame - and the reused-buffer
         * contract promises the collector that its samples hold still until it returns. Copying
         * 4096 floats costs far less than a frame's DSP does.
         */
        private val handOut = FloatArray(config.frameSize)

        lateinit var frame: AudioFrame
            private set

        var handedOut = false

        private var filled = 0
        private var samplesRead = 0L
        private var samplesSinceLastFrame = 0

        suspend fun open(): AudioCaptureError? {
            val created = runCatching { newAudioContext(requestedSampleRateHz) }.getOrNull()
                ?: return AudioCaptureError.InitializationFailed
            context = created

            val rate = sampleRateOf(created).toInt()
            if (rate <= 0) return AudioCaptureError.UnsupportedConfiguration
            sampleRate = rate
            frame = AudioFrame(handOut, rate)

            val url = blobUrl(CAPTURE_WORKLET_SOURCE)
            try {
                addWorkletModule(created, url).await<JsAny?>()
            } catch (failure: Throwable) {
                return AudioCaptureError.InitializationFailed
            } finally {
                revokeBlobUrl(url)
            }

            // A context created before the user has interacted with the page starts suspended. Every
            // trainer starts from a button, so by the time this runs there has been a gesture.
            if (stateOf(created) != "running") {
                runCatching { resume(created).await<JsAny?>() }
            }

            baseLatencySeconds = baseLatencyOf(created)
            outputLatencySeconds = outputLatencyOf(created)

            val result = requestMicrophone().await<MicrophoneResult>()
            errorNameOf(result)?.let { return it.asCaptureError() }
            val stream = streamOf(result)
            microphone = stream

            val input = microphoneSource(created, stream)
            source = input
            val node = captureNode(created, config.hopSize, bufferPoolSize)
            worklet = node
            val nodePort = portOf(node)
            port = nodePort
            nodePort.onmessage = { event: MessageEvent -> receive(nodePort, event) }
            // The worklet has no outputs, so it is never connected to the destination: nothing the
            // microphone hears is played back.
            connect(input, node)
            return null
        }

        private fun receive(port: MessagePort, event: MessageEvent) {
            @Suppress("UNCHECKED_CAST")
            val message = event.data as HopMessage
            val samples = samplesOf(message)
            batchesReceived++
            lastCapturedAtSeconds = capturedAtOf(message)
            poolMisses = poolMissesOf(message)
            quanta = quantaOf(message)

            val copyStart = performanceNow()
            appendToWindow(samples)
            // Back to the worklet the moment its contents are safely in Kotlin's own array, so it
            // refills that buffer instead of allocating another.
            returnBuffer(port, samples)
            copyMillisTotal += performanceNow() - copyStart

            filled = minOf(config.frameSize, filled + config.hopSize)
            samplesRead += config.hopSize
            samplesSinceLastFrame += config.hopSize
            if (filled < config.frameSize) return

            if (handedOut) {
                // The collector still has the previous window. Its samples keep accumulating, so
                // the next frame it does take is continuous; only this one is lost.
                droppedFrames++
                return
            }
            window.copyInto(handOut)
            frame.position(
                startSample = samplesRead - config.frameSize,
                newSamples = samplesSinceLastFrame,
            )
            samplesSinceLastFrame = 0
            framesEmitted++
            handedOut = true
            ready.trySend(Unit)
        }

        /**
         * Slides the window left by one hop and copies the new batch into the end.
         *
         * The copy is per element because Kotlin/Wasm has no view onto a JavaScript typed array.
         * The M3 probe measured this crossing at 0.07 ms for 2048 samples.
         */
        private fun appendToWindow(samples: Float32Array) {
            window.copyInto(window, 0, config.hopSize, window.size)
            val start = window.size - config.hopSize
            for (i in 0 until config.hopSize) {
                window[start + i] = samples[i]
            }
        }

        fun shutDown() {
            port?.onmessage = null
            worklet?.let { runCatching { disconnect(it) } }
            source?.let { runCatching { disconnect(it) } }
            microphone?.let { runCatching { stopTracks(it) } }
            context?.let { runCatching { closeContext(it) } }
            ready.close()
            port = null
            worklet = null
            source = null
            microphone = null
            context = null
        }
    }

    private companion object {
        /** Four buffers in flight was enough for zero pool misses over 67,488 quanta in M3. */
        const val DEFAULT_POOL_SIZE = 4

        /**
         * The browser's refusal, as one of this module's own reasons.
         *
         * The names are the DOMException names the Media Capture specification defines; anything
         * unrecognised is a failure to start rather than a guess at why.
         */
        fun String.asCaptureError(): AudioCaptureError = when (this) {
            "NotAllowedError", "SecurityError", "PermissionDeniedError" ->
                AudioCaptureError.PermissionDenied

            "NotFoundError", "DevicesNotFoundError", "NotReadableError", "TrackStartError",
            "AbortError",
            -> AudioCaptureError.DeviceUnavailable

            "OverconstrainedError", "ConstraintNotSatisfiedError" ->
                AudioCaptureError.UnsupportedConfiguration

            else -> AudioCaptureError.InitializationFailed
        }
    }
}
