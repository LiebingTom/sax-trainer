package com.example.saxtrainer.probe

import com.example.saxtrainer.core.dsp.pitch.YinConfig
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteNaming
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.js.Promise
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M3 PROBE - TEMPORARY. Delete with the probe/ directory.
 *
 * The probe, run unattended in a real browser. Every number the milestone reports comes from here or
 * from the page; nothing in this file is a simulation - it is Firefox, Web Audio, a real
 * AudioWorklet and the real `core:dsp`.
 *
 * The source is an oscillator rather than a microphone for most cases, because the worklet cannot
 * tell the difference and an oscillator can be given an exact frequency to be judged against. One
 * case does use `getUserMedia`, with Firefox's fake device, to prove that path is reachable too.
 */
class WebAudioProbeTest {

    // Sounding pitches inside a tenor's range, at exact equal-tempered frequencies so that what the
    // detector should answer is not a matter of opinion.
    private val bFlat2 = 116.5409
    private val eFlat3 = 155.5635
    private val g3 = 195.9977
    private val c4 = 261.6256

    @Test
    fun a_sustained_tone_reaches_the_shared_dsp_and_is_named(): Promise<JsAny?> = probe {
        val run = Run(hopSize = 2048)
        run.start(ProbeCapture.Source.TONE, toneHz = eFlat3)
        run.awaitFrames(20)
        run.stop()

        report("sustained tone", run)
        val pitched = run.pitched()
        assertTrue(pitched.size >= 15, "only ${pitched.size} of ${run.heard.size} frames were pitched")
        val last = pitched.last()
        assertEquals(Note.nearestTo(eFlat3), last.note, "named the wrong note")
        assertTrue(
            abs(last.frequencyHz - eFlat3) / eFlat3 < 0.01,
            "detected ${last.frequencyHz} Hz for $eFlat3 Hz",
        )
    }

    @Test
    fun changing_the_tone_changes_the_note(): Promise<JsAny?> = probe {
        val run = Run(hopSize = 2048)
        run.start(ProbeCapture.Source.TONE, toneHz = bFlat2)

        val named = mutableListOf<Note>()
        listOf(bFlat2, eFlat3, g3, c4).forEach { frequency ->
            run.retune(frequency)
            run.awaitPitch(Note.nearestTo(frequency))
            named += run.pitched().last().note
        }
        run.stop()

        report("changing notes", run)
        assertEquals(listOf(bFlat2, eFlat3, g3, c4).map { Note.nearestTo(it) }, named)
    }

    @Test
    fun silence_reads_as_silence_and_never_as_a_note(): Promise<JsAny?> = probe {
        val run = Run(hopSize = 2048)
        run.start(ProbeCapture.Source.SILENCE, toneHz = eFlat3)
        run.awaitFrames(15)
        run.stop()

        report("silence", run)
        assertEquals(0, run.pitched().size, "silence was heard as a note")
        assertEquals(
            run.heard.size,
            run.heard.count { it is ProbeListener.Heard.Silent },
            "silence was not reported as silent: ${run.summary()}",
        )
    }

    @Test
    fun noise_reads_as_unclear_and_never_as_a_note(): Promise<JsAny?> = probe {
        val run = Run(hopSize = 2048)
        run.start(ProbeCapture.Source.NOISE)
        run.awaitFrames(15)
        run.stop()

        report("noise", run)
        // The gate has to open - noise is loud - and the pitch has to be rejected. Reporting a
        // breath as a wrong note is the failure a trainer must never make.
        assertEquals(0, run.pitched().size, "noise was heard as a note: ${run.summary()}")
    }

    @Test
    fun the_microphone_path_works_through_getusermedia(): Promise<JsAny?> = probe {
        val run = Run(hopSize = 2048)
        run.start(ProbeCapture.Source.MICROPHONE)
        run.awaitFrames(10)
        run.stop()

        report("microphone (fake device)", run)
        assertTrue(run.capture.framesEmitted >= 10, "no audio arrived from getUserMedia")
        assertTrue(run.capture.sampleRate > 0, "no sample rate reported")
    }

    @Test
    fun stopping_capture_stops_the_frames(): Promise<JsAny?> = probe {
        val run = Run(hopSize = 2048)
        run.start(ProbeCapture.Source.TONE, toneHz = g3)
        run.awaitFrames(8)
        run.stop()

        val afterStop = run.capture.framesEmitted
        window.awaitMillis(1000)
        assertEquals(afterStop, run.capture.framesEmitted, "frames kept arriving after stop()")
    }

    @Test
    fun the_levers_are_measured_against_the_starting_point(): Promise<JsAny?> = probe {
        // Lever one and two of the plan, measured rather than assumed. Only worth pulling if the
        // starting point is too slow, and the numbers say whether it is.
        listOf(
            Triple("hop 2048, YIN from 60 Hz", 2048, 60f),
            Triple("hop 4096, YIN from 60 Hz", 4096, 60f),
            Triple("hop 2048, YIN from 120 Hz", 2048, 120f),
        ).forEach { (name, hop, minimum) ->
            val run = Run(hopSize = hop, yin = YinConfig(minFrequencyHz = minimum))
            run.start(ProbeCapture.Source.TONE, toneHz = eFlat3)
            run.awaitFrames(20)
            run.stop()
            report(name, run)
            assertTrue(run.pitched().isNotEmpty(), "$name detected nothing")
        }
    }

    @Test
    fun a_lower_sample_rate_can_be_asked_for(): Promise<JsAny?> = probe {
        // Lever three. The browser is free to ignore it, so what it did is reported rather than
        // asserted; only that the chain still works at whatever rate came back is a requirement.
        val run = Run(hopSize = 2048)
        run.start(ProbeCapture.Source.TONE, toneHz = eFlat3, requestedSampleRate = 24_000.0)
        run.awaitFrames(20)
        run.stop()

        report("requested 24000 Hz", run)
        assertTrue(run.pitched().isNotEmpty(), "nothing detected at ${run.capture.sampleRate} Hz")
        assertEquals(
            Note.nearestTo(eFlat3),
            run.pitched().last().note,
            "the wrong note at ${run.capture.sampleRate} Hz",
        )
    }

    @Test
    fun the_time_from_a_note_starting_to_it_being_named_is_measured(): Promise<JsAny?> = probe {
        // The headline number. The tone is already connected and silent, so switching its gain is
        // an onset with an exact moment, and nothing but the capture chain lies between that moment
        // and the first frame the listener calls pitched.
        val run = Run(hopSize = 2048)
        run.start(ProbeCapture.Source.SILENCE, toneHz = eFlat3)
        run.awaitFrames(4)

        val latencies = mutableListOf<Double>()
        repeat(ONSET_ATTEMPTS) {
            run.capture.setSourceGain(0.0)
            run.awaitFrames(4)
            run.markOnset()
            run.capture.setSourceGain(0.25)
            latencies += run.awaitFirstPitch()
        }
        run.stop()

        report("onset to detection", run)
        println("  onset latencies ms    ${latencies.joinToString(" ") { it.round(0) }}")
        println("  onset latency ms      mean ${(latencies.average()).round(1)}  max ${latencies.max().round(1)}")
        println("  one hop is            ${(1000.0 * 2048 / run.capture.sampleRate).round(1)} ms")
        assertTrue(
            latencies.max() < 400.0,
            "a note took ${latencies.max()} ms to be named",
        )
    }

    @Test
    fun capture_stays_steady_for_minutes(): Promise<JsAny?> = probe {
        val run = Run(hopSize = 2048)
        run.start(ProbeCapture.Source.TONE, toneHz = g3)
        run.awaitMillis(STABILITY_MILLIS)
        val expectedFrames = STABILITY_MILLIS / (1000.0 * 2048 / run.capture.sampleRate)
        run.stop()

        report("three minutes continuous", run)
        println("  expected about ${expectedFrames.toInt()} frames")
        assertTrue(
            run.capture.framesEmitted > expectedFrames * 0.95,
            "only ${run.capture.framesEmitted} frames in ${STABILITY_MILLIS / 1000} s, " +
                "expected about ${expectedFrames.toInt()}",
        )
        assertTrue(
            run.pitched().size > run.heard.size * 0.95,
            "the note was lost during the run: ${run.summary()}",
        )
    }

    // --- harness ---------------------------------------------------------------------------------

    /** One capture run and everything it heard. */
    class Run(
        hopSize: Int,
        frameSize: Int = 4096,
        private val yin: YinConfig = YinConfig(),
    ) {
        val capture = ProbeCapture(frameSize = frameSize, hopSize = hopSize)

        init {
            open += this
        }
        val heard = mutableListOf<ProbeListener.Heard>()
        private var listener: ProbeListener? = null
        private var waiting: CompletableDeferred<Unit>? = null
        private var waitUntilFrames = Int.MAX_VALUE
        private var waitForNote: Note? = null

        suspend fun start(
            source: ProbeCapture.Source,
            toneHz: Double = 0.0,
            requestedSampleRate: Double = 0.0,
        ) {
            capture.start(
                source = source,
                requestedSampleRate = requestedSampleRate,
                toneHz = toneHz,
                workletUrl = blobUrl(CAPTURE_WORKLET_SOURCE),
            ) { window, _ -> onFrame(window) }
            listener = ProbeListener(sampleRate = capture.sampleRate, yin = yin)
        }

        fun retune(frequencyHz: Double) = capture.retune(frequencyHz)

        private var onsetAtMillis = 0.0
        private var onsetGate: CompletableDeferred<Double>? = null

        fun markOnset() {
            onsetAtMillis = performanceNow()
        }

        /** Milliseconds from [markOnset] to the first frame the listener called pitched. */
        suspend fun awaitFirstPitch(): Double {
            val gate = CompletableDeferred<Double>()
            onsetGate = gate
            val timeout = window.timeout(WAIT_TIMEOUT_MILLIS) {
                gate.completeExceptionally(IllegalStateException("no pitch after an onset"))
            }
            try {
                return gate.await()
            } finally {
                onsetGate = null
                window.cancelTimeout(timeout)
            }
        }

        fun stop() {
            capture.stop()
            open -= this
        }

        fun pitched(): List<ProbeListener.Heard.Pitched> =
            heard.filterIsInstance<ProbeListener.Heard.Pitched>()

        fun summary(): String {
            val pitchedCount = pitched().size
            val unclear = heard.count { it is ProbeListener.Heard.Unclear }
            val silent = heard.count { it is ProbeListener.Heard.Silent }
            return "$pitchedCount pitched, $unclear unclear, $silent silent"
        }

        /** Suspends until [count] more frames have been analysed, or says what state it got stuck in. */
        suspend fun awaitFrames(count: Int, timeoutMillis: Int = WAIT_TIMEOUT_MILLIS) {
            waitUntilFrames = heard.size + count
            await("$count more frames", timeoutMillis)
        }

        /** Suspends until the given note is what the listener is hearing. */
        suspend fun awaitPitch(note: Note, timeoutMillis: Int = WAIT_TIMEOUT_MILLIS) {
            waitForNote = note
            await("the note $note", timeoutMillis)
        }

        private suspend fun await(what: String, timeoutMillis: Int) {
            val gate = CompletableDeferred<Unit>()
            waiting = gate
            val timeout = window.timeout(timeoutMillis) {
                gate.completeExceptionally(
                    IllegalStateException(
                        "waited ${timeoutMillis} ms for $what and never got it: " +
                            "${capture.batchesReceived} batches, ${capture.framesEmitted} frames, " +
                            "${capture.sampleRate} Hz, ${summary()}",
                    ),
                )
            }
            try {
                gate.await()
            } finally {
                waitUntilFrames = Int.MAX_VALUE
                waitForNote = null
                waiting = null
                window.cancelTimeout(timeout)
            }
        }

        suspend fun awaitMillis(millis: Int) = window.awaitMillis(millis)

        companion object {
            /** So a failing test cannot leave an AudioContext behind for the next one. */
            val open = mutableListOf<Run>()
        }

        private fun onFrame(samples: FloatArray) {
            val result = listener?.hear(samples) ?: return
            heard += result
            if (result is ProbeListener.Heard.Pitched) {
                onsetGate?.let { gate ->
                    onsetGate = null
                    gate.complete(performanceNow() - onsetAtMillis)
                }
            }
            val note = waitForNote
            val reached = heard.size >= waitUntilFrames ||
                (note != null && result is ProbeListener.Heard.Pitched && result.note == note)
            if (reached) {
                waitUntilFrames = Int.MAX_VALUE
                waitForNote = null
                waiting?.complete(Unit)
                waiting = null
            }
        }
    }

    private fun report(name: String, run: Run) {
        val capture = run.capture
        val expectedInterval = 1000.0 * 2048 / maxOf(1, capture.sampleRate)
        println("--- probe: $name")
        println("  sample rate           ${capture.sampleRate} Hz")
        println("  batches / frames      ${capture.batchesReceived} / ${capture.framesEmitted}")
        println("  heard                 ${run.summary()}")
        println(
            "  hop interval ms       mean ${capture.hopIntervalMillis.mean.round(2)}" +
                "  p95 ${capture.hopIntervalMillis.percentile(0.95).round(2)}" +
                "  max ${capture.hopIntervalMillis.max.round(2)}" +
                "  (nominal ${expectedInterval.round(2)})",
        )
        println(
            "  copy out of JS ms     mean ${capture.copyMillis.mean.round(3)}" +
                "  p95 ${capture.copyMillis.percentile(0.95).round(3)}" +
                "  max ${capture.copyMillis.max.round(3)}",
        )
        println(
            "  DSP per frame ms      mean ${capture.frameMillis.mean.round(3)}" +
                "  p95 ${capture.frameMillis.percentile(0.95).round(3)}" +
                "  max ${capture.frameMillis.max.round(3)}",
        )
        val busy = if (capture.hopIntervalMillis.mean <= 0.0) {
            0.0
        } else {
            100.0 * (capture.frameMillis.mean + capture.copyMillis.mean) /
                capture.hopIntervalMillis.mean
        }
        println("  main thread busy      ${busy.round(1)} %")
        println("  worklet pool misses   ${capture.poolMisses}")
        println("  quanta                ${capture.quanta}")
        println("  base / output latency ${capture.baseLatencySeconds.round(5)} s / " +
            "${capture.outputLatencySeconds.round(5)} s")
        run.pitched().lastOrNull()?.let {
            println(
                "  last pitch            ${NoteNaming.INTERNATIONAL.labelOf(it.note)} " +
                    "${it.frequencyHz.toDouble().round(2)} Hz confidence ${it.confidence.toDouble().round(2)}",
            )
        }
    }

    private companion object {
        /** "Several minutes of continuous capture", as the milestone asks for. */
        const val STABILITY_MILLIS = 180_000

        /** Enough onsets that one unlucky alignment with the hop boundary does not stand alone. */
        const val ONSET_ATTEMPTS = 8
    }
}

private fun Double.round(digits: Int): String {
    if (isNaN() || isInfinite()) return "-"
    var scale = 1.0
    repeat(digits) { scale *= 10 }
    return (kotlin.math.round(this * scale) / scale).toString()
}

@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
private fun probe(block: suspend () -> Unit): Promise<JsAny?> =
    GlobalScope.promise<JsAny?> {
        try {
            block()
        } finally {
            WebAudioProbeTest.Run.open.toList().forEach { it.stop() }
        }
        null
    }

/** A frame takes ~43 ms; anything that has not happened in this long is stuck, not slow. */
private const val WAIT_TIMEOUT_MILLIS = 15_000

private suspend fun org.w3c.dom.Window.awaitMillis(millis: Int) {
    val done = CompletableDeferred<Unit>()
    setTimeout({ done.complete(Unit); null }, millis)
    done.await()
}

private fun org.w3c.dom.Window.timeout(millis: Int, action: () -> Unit): Int =
    setTimeout({ action(); null }, millis)

private fun org.w3c.dom.Window.cancelTimeout(handle: Int) = clearTimeout(handle)
