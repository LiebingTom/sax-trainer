package com.example.saxtrainer.core.audio.capture

import com.example.saxtrainer.core.audio.AudioConfig
import com.example.saxtrainer.core.audio.listening.Hearing
import com.example.saxtrainer.core.audio.listening.NoteListener
import com.example.saxtrainer.core.audio.web.CAPTURE_WORKLET_SOURCE
import com.example.saxtrainer.core.audio.web.HopMessage
import com.example.saxtrainer.core.audio.web.addWorkletModule
import com.example.saxtrainer.core.audio.web.blobUrl
import com.example.saxtrainer.core.audio.web.captureNode
import com.example.saxtrainer.core.audio.web.closeContext
import com.example.saxtrainer.core.audio.web.newAudioContext
import com.example.saxtrainer.core.audio.web.performanceNow
import com.example.saxtrainer.core.audio.web.portOf
import com.example.saxtrainer.core.audio.web.resume
import com.example.saxtrainer.core.audio.web.returnBuffer
import com.example.saxtrainer.core.audio.web.sampleRateOf
import com.example.saxtrainer.core.audio.web.samplesOf
import com.example.saxtrainer.core.audio.web.stateOf
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.promise
import kotlinx.coroutines.withTimeoutOrNull
import org.khronos.webgl.get
import org.w3c.dom.MessageEvent
import kotlin.js.Promise
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The production web capture, in a real browser.
 *
 * Nothing here is simulated: Firefox, a real `AudioContext`, a real `AudioWorklet`, real
 * `getUserMedia` (against the browser's fake device, which plays a steady 1 kHz tone) and the real
 * shared `NoteListener`. The M3 probe measured this chain at 44.5 ms end to end and 4.9 % of the
 * main thread; these cases exist to keep the production version of it honest.
 */
class WebAudioCaptureTest {

    /** What Firefox's fake microphone plays. */
    private val fakeDeviceHz = 1000.0

    @Test
    fun the_microphone_reaches_the_shared_listener_and_is_named(): Promise<JsAny?> = inBrowser {
        val capture = WebAudioCapture()
        val heard = mutableListOf<Hearing>()

        collect(NoteListener(capture, AudioCaptureConfig()).listen(), count = 8, into = heard)

        assertTrue(capture.sampleRate > 0, "the capture never reported a sample rate")
        assertEquals(8, heard.size, "only ${heard.size} frames arrived from getUserMedia")
        val pitched = heard.filterIsInstance<Hearing.Pitched>()
        assertTrue(pitched.isNotEmpty(), "nothing was pitched: $heard")
        val detected = pitched.last().detected
        assertTrue(
            abs(detected.frequencyHz - fakeDeviceHz) / fakeDeviceHz < 0.01,
            "detected ${detected.frequencyHz} Hz where the fake device plays $fakeDeviceHz Hz",
        )
    }

    @Test
    fun the_pitch_follows_the_browsers_rate_and_not_the_configured_one(): Promise<JsAny?> =
        inBrowser {
            // The configuration asks for 48 kHz - it is the default - and the context is asked for
            // 24 kHz, which Firefox honours. If anything downstream took the rate from the
            // configuration rather than from the frame, this 1 kHz tone would be named an octave
            // out. That is the whole of the sample-rate propagation contract, end to end.
            val capture = WebAudioCapture(requestedSampleRateHz = 24_000.0)
            val heard = mutableListOf<Hearing>()

            collect(NoteListener(capture, AudioCaptureConfig()).listen(), count = 8, into = heard)

            assertEquals(
                48_000,
                AudioConfig().sampleRate,
                "this case is only meaningful while the configured default differs",
            )
            assertEquals(24_000, capture.sampleRate, "the browser did not honour the asked-for rate")
            val pitched = heard.filterIsInstance<Hearing.Pitched>()
            assertTrue(pitched.isNotEmpty(), "nothing was pitched: $heard")
            val detected = pitched.last().detected.frequencyHz
            assertTrue(
                abs(detected - fakeDeviceHz) / fakeDeviceHz < 0.01,
                "detected $detected Hz at a 24 kHz context; the frame's rate was not used",
            )
        }

    @Test
    fun the_frames_carry_the_audio_clock(): Promise<JsAny?> = inBrowser {
        val capture = WebAudioCapture()
        val config = AudioCaptureConfig()
        val frames = mutableListOf<Triple<Long, Int, Int>>()

        collect(
            capture.frames(config).map { event ->
                val frame = (event as AudioCaptureEvent.Frame).frame
                Triple(frame.startSample, frame.newSamples, frame.sampleRate)
            },
            count = 6,
            into = frames,
        )

        assertEquals(6, frames.size, "only ${frames.size} frames arrived")
        assertEquals(0L, frames.first().first, "the first window does not start at the beginning")
        frames.forEach { (_, _, rate) ->
            assertEquals(capture.sampleRate, rate, "a frame reported a rate the context does not run at")
        }
        // Each window advances by exactly one hop, and the audio seen is the sum of the new samples.
        frames.zipWithNext { previous, next ->
            assertEquals(
                previous.first + config.hopSize,
                next.first,
                "the window did not advance by one hop",
            )
        }
        assertTrue(
            frames.drop(1).all { it.second == config.hopSize },
            "a frame claimed the wrong amount of new audio: ${frames.map { it.second }}",
        )
    }

    @Test
    fun ending_the_collection_stops_the_microphone(): Promise<JsAny?> = inBrowser {
        val capture = WebAudioCapture()
        val collecting = GlobalScope.launch {
            capture.frames(AudioCaptureConfig()).collect { }
        }
        waitUntil("frames to start") { capture.framesEmitted >= 4 }

        collecting.cancel()
        delay(SETTLE_MILLIS)
        val afterCancel = capture.framesEmitted
        delay(SETTLE_MILLIS)

        assertEquals(afterCancel, capture.framesEmitted, "frames kept arriving after cancellation")
    }

    @Test
    fun an_input_with_nothing_in_it_still_delivers_audio(): Promise<JsAny?> = inBrowser {
        // The M3 finding, as a regression test. Firefox hands `process` an *empty* input array when
        // nothing upstream is producing sound - not a quantum of zeros - so the worklet is built
        // here with nothing connected to it at all, which is that case exactly. A worklet that
        // returned early would deliver nothing, and every exercise clock downstream is driven by
        // frames arriving: a long tone's timer, a sequence's beat matching and the note trainer's
        // reaction window would all stall the moment the player stopped playing.
        val hopSize = 2048
        val context = newAudioContext(0.0)
        try {
            val url = blobUrl(CAPTURE_WORKLET_SOURCE)
            addWorkletModule(context, url).await<JsAny?>()
            if (stateOf(context) != "running") resume(context).await<JsAny?>()

            val node = captureNode(context, hopSize, 4)
            val port = portOf(node)
            var batches = 0
            var loudest = 0f
            port.onmessage = { event: MessageEvent ->
                @Suppress("UNCHECKED_CAST")
                val samples = samplesOf(event.data as HopMessage)
                batches++
                for (i in 0 until hopSize) {
                    val value = abs(samples[i])
                    if (value > loudest) loudest = value
                }
                returnBuffer(port, samples)
            }

            val rate = sampleRateOf(context)
            val expected = 4
            waitUntil("silent batches", timeoutMillis = 15_000) { batches >= expected }

            assertTrue(
                batches >= expected,
                "only $batches batches arrived from an unconnected input in 15 s; " +
                    "a hop is ${1000.0 * hopSize / rate} ms",
            )
            assertEquals(0f, loudest, "the substituted quantum was not silent")
        } finally {
            runCatching { closeContext(context) }
        }
    }

    @Test
    fun the_production_chain_matches_the_probes_measurements(): Promise<JsAny?> = inBrowser {
        val capture = WebAudioCapture()
        val config = AudioCaptureConfig()
        val timed = TimingCapture(capture)
        val dsp = Samples()
        val arrivals = Samples()
        var previousArrival = 0.0

        val heard = mutableListOf<Hearing>()
        collect(
            NoteListener(timed, config).listen(),
            count = MEASURED_FRAMES,
            into = heard,
        ) {
            val now = performanceNow()
            dsp.add(now - timed.lastHandedOverMillis)
            if (previousArrival > 0.0) arrivals.add(now - previousArrival)
            previousArrival = now
        }

        val nominal = 1000.0 * config.hopSize / capture.sampleRate
        val copyPerBatch = capture.copyMillisTotal / capture.batchesReceived
        val busy = 100.0 * (dsp.mean + copyPerBatch) / arrivals.mean
        println("--- core:audio web capture")
        println("  sample rate           ${capture.sampleRate} Hz")
        println("  frame / hop           ${config.frameSize} / ${config.hopSize}")
        println("  batches / frames      ${capture.batchesReceived} / ${capture.framesEmitted}")
        println("  dropped / pool misses ${capture.droppedFrames} / ${capture.poolMisses}")
        println("  quanta                ${capture.quanta}")
        println("  frame interval ms     mean ${arrivals.mean} max ${arrivals.max} (nominal $nominal)")
        println("  DSP per frame ms      mean ${dsp.mean} max ${dsp.max}")
        println("  copy out of JS ms     mean $copyPerBatch")
        println("  main thread busy      $busy %")
        println("  base / output latency ${capture.baseLatencySeconds} s / ${capture.outputLatencySeconds} s")

        assertEquals(MEASURED_FRAMES, heard.size, "the run did not complete")
        assertEquals(0, capture.poolMisses, "the worklet had to allocate a batch buffer")
        assertEquals(0, capture.droppedFrames, "the main thread was too busy to take a frame")
        // The M3 baseline was 2.0 ms of DSP and 4.9 % busy. A generous ceiling, because this is a
        // regression guard and not a benchmark: it should catch a chain that got an order of
        // magnitude worse, and never fail because a build machine was busy.
        assertTrue(dsp.mean < nominal / 2, "DSP averaged ${dsp.mean} ms against a $nominal ms frame")
        assertTrue(busy < 50.0, "the main thread was $busy % busy")
    }

    /**
     * Stamps the moment a frame is handed downstream, so the time the shared DSP takes can be
     * measured without production code carrying a hook for it.
     */
    private class TimingCapture(private val delegate: AudioCapture) : AudioCapture {
        var lastHandedOverMillis = 0.0
            private set

        override fun frames(config: AudioCaptureConfig): Flow<AudioCaptureEvent> =
            delegate.frames(config).map { event ->
                lastHandedOverMillis = performanceNow()
                event
            }
    }

    private class Samples {
        private val values = mutableListOf<Double>()

        fun add(value: Double) {
            values += value
        }

        val mean: Double get() = if (values.isEmpty()) 0.0 else values.sum() / values.size
        val max: Double get() = values.maxOrNull() ?: 0.0
    }

    private companion object {
        const val MEASURED_FRAMES = 40
        const val SETTLE_MILLIS = 500L
    }
}

/** A frame takes ~43 ms; anything that has not happened in this long is stuck, not slow. */
private const val WAIT_TIMEOUT_MILLIS = 20_000L

private const val POLL_MILLIS = 25L

@OptIn(DelicateCoroutinesApi::class)
internal fun inBrowser(block: suspend () -> Unit): Promise<JsAny?> =
    GlobalScope.promise<JsAny?> {
        block()
        null
    }

/**
 * Takes [count] values, failing rather than hanging if they do not arrive. [onEach] runs before the
 * value is recorded, which is where a timing measurement belongs.
 */
internal suspend fun <T> collect(
    flow: Flow<T>,
    count: Int,
    into: MutableList<T>,
    timeoutMillis: Long = WAIT_TIMEOUT_MILLIS,
    onEach: () -> Unit = {},
) {
    withTimeoutOrNull(timeoutMillis) {
        flow.take(count).collect {
            onEach()
            into += it
        }
    }
}

internal suspend fun waitUntil(
    what: String,
    timeoutMillis: Long = WAIT_TIMEOUT_MILLIS,
    condition: () -> Boolean,
) {
    val reached = withTimeoutOrNull(timeoutMillis) {
        while (!condition()) delay(POLL_MILLIS)
        true
    }
    if (reached != true) throw AssertionError("waited $timeoutMillis ms for $what and never got it")
}
