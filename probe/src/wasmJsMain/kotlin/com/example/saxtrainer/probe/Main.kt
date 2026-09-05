package com.example.saxtrainer.probe

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.capture.AudioCaptureEvent
import com.example.saxtrainer.core.audio.capture.MicrophoneAccess
import com.example.saxtrainer.core.audio.capture.WebAudioCapture
import com.example.saxtrainer.core.audio.capture.WebMicrophoneAccess
import com.example.saxtrainer.core.audio.listening.Hearing
import com.example.saxtrainer.core.audio.listening.NoteListener
import com.example.saxtrainer.core.audio.metronome.Metronome
import com.example.saxtrainer.core.audio.metronome.WebMetronome
import com.example.saxtrainer.core.music.NoteNaming
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSelectElement

/**
 * The web audio bench: the **production** `:core:audio` chain, with its numbers on screen.
 *
 * It began life in M3 as a throwaway spike with its own capture, its own worklet and its own copy of
 * the listener's decision. All of that is gone: everything below the page is now
 * [WebAudioCapture] -> [NoteListener] -> `core:dsp`, exactly what a trainer will use. What is left
 * here is a page, and it exists for the one check a headless browser cannot make - a person playing
 * a real saxophone into a real microphone.
 *
 * The browser tests measure the same numbers on every run, against the fake microphone a headless
 * Firefox provides. This page is the other half: the only place the web audio numbers can be read
 * off a real instrument. It is a developer page and deliberately not part of the app.
 */
private val scope = CoroutineScope(Dispatchers.Default)

private var capture: WebAudioCapture? = null
private var collecting: Job? = null
private var metronome: Metronome? = null
private var access: MicrophoneAccess? = null

private var startedAtMillis = 0.0
private var firstPitchLatencyMillis = -1.0
private var pitchedFrames = 0
private var unclearFrames = 0
private var silentFrames = 0
private var lastHeard = "-"
private var permission = "not asked"
private val recentNotes = ArrayDeque<String>()

/** Wall-clock milliseconds spent inside the shared DSP, measured the way the tests measure it. */
private val dsp = Statistic()

/** Milliseconds between one analysis frame and the next. */
private val frameInterval = Statistic()

private var handedOverAtMillis = 0.0
private var lastFrameAtMillis = 0.0

fun main() {
    document.body?.innerHTML = PAGE
    access = WebMicrophoneAccess()
    button("startMic").onclick = { start() }
    button("stop").onclick = { stop() }
    button("startClick").onclick = { startMetronome() }
    button("stopClick").onclick = { stopMetronome() }
    window.setInterval({ render(); null }, RENDER_INTERVAL_MS)
}

private fun start() {
    if (capture != null) return
    reset()

    // Permission is asked for here, at the moment capture starts, and the capture opens its own
    // stream when its flow is collected. Same rule as Android; the browser prompts once either way.
    access?.request { granted ->
        permission = if (granted) "granted" else "refused"
        if (granted) beginCapture() else element("error").textContent = "microphone refused"
    }
}

private fun beginCapture() {
    val requestedRate = selected("sampleRate").toDouble()
    val hopSize = selected("hopSize").toInt()
    val config = AudioCaptureConfig(frameSize = maxOf(DEFAULT_FRAME_SIZE, hopSize * 2), hopSize = hopSize)

    val started = WebAudioCapture(requestedSampleRateHz = requestedRate)
    capture = started
    startedAtMillis = now()

    collecting = scope.launch {
        NoteListener(Timed(started), config).listen().collect { heard -> onHeard(heard) }
    }
}

private fun onHeard(heard: Hearing) {
    val at = now()
    dsp.add(at - handedOverAtMillis)
    if (lastFrameAtMillis > 0.0) frameInterval.add(at - lastFrameAtMillis)
    lastFrameAtMillis = at

    when (heard) {
        is Hearing.Silent -> {
            silentFrames++
            lastHeard = "silent"
        }

        is Hearing.Unclear -> {
            unclearFrames++
            lastHeard = "unclear (confidence ${format(heard.confidence.toDouble(), 2)})"
        }

        is Hearing.Pitched -> {
            pitchedFrames++
            if (firstPitchLatencyMillis < 0) firstPitchLatencyMillis = at - startedAtMillis
            val detected = heard.detected
            val label = NoteNaming.INTERNATIONAL.labelOf(detected.note)
            lastHeard = "$label  ${format(detected.frequencyHz.toDouble(), 1)} Hz  " +
                "confidence ${format(detected.confidence.toDouble(), 2)}"
            if (recentNotes.lastOrNull() != label) {
                recentNotes.addLast(label)
                if (recentNotes.size > RECENT_NOTES) recentNotes.removeFirst()
            }
        }
    }
}

/**
 * Stamps the moment a frame leaves the capture, so the page can time the shared DSP without
 * `:core:audio` carrying a hook for it.
 */
private class Timed(private val delegate: AudioCapture) : AudioCapture {
    override fun frames(config: AudioCaptureConfig): Flow<AudioCaptureEvent> =
        delegate.frames(config).map { event ->
            handedOverAtMillis = now()
            event
        }
}

private fun stop() {
    collecting?.cancel()
    collecting = null
    capture = null
}

private fun startMetronome() {
    if (metronome != null) return
    val click = WebMetronome()
    metronome = click
    click.setBpm(selected("bpm").toInt())
    click.start()
}

private fun stopMetronome() {
    metronome?.release()
    metronome = null
}

private fun reset() {
    firstPitchLatencyMillis = -1.0
    pitchedFrames = 0
    unclearFrames = 0
    silentFrames = 0
    lastHeard = "-"
    lastFrameAtMillis = 0.0
    dsp.clear()
    frameInterval.clear()
    recentNotes.clear()
    element("error").textContent = ""
}

private fun render() {
    val active = capture
    val click = metronome
    element("readout").innerHTML = buildString {
        row("microphone permission", permission)
        if (active == null) {
            row("capture", "stopped")
        } else {
            val hopSize = selected("hopSize").toInt()
            val expectedInterval = if (active.sampleRate == 0) {
                0.0
            } else {
                1000.0 * hopSize / active.sampleRate
            }
            row("running for", "${format((now() - startedAtMillis) / 1000.0, 1)} s")
            row("heard now", lastHeard)
            row("recent notes", recentNotes.joinToString(" "))
            separator()
            row("sample rate", "${active.sampleRate} Hz")
            row("hop size", "$hopSize samples")
            row("base latency", latency(active.baseLatencySeconds))
            row("output latency", latency(active.outputLatencySeconds))
            separator()
            row("batches", "${active.batchesReceived}")
            row("frames analysed", "${active.framesEmitted}")
            row("frames dropped", "${active.droppedFrames}")
            row(
                "frame interval",
                "${format(frameInterval.mean, 1)} ms mean, " +
                    "${format(frameInterval.percentile(0.95), 1)} ms p95, " +
                    "${format(frameInterval.max, 1)} ms max " +
                    "(expected ${format(expectedInterval, 1)} ms)",
            )
            row(
                "copy out of JS",
                "${format(active.copyMillisTotal / maxOf(1, active.batchesReceived), 3)} ms mean",
            )
            row(
                "DSP per frame",
                "${format(dsp.mean, 2)} ms mean, " +
                    "${format(dsp.percentile(0.95), 2)} ms p95, " +
                    "${format(dsp.max, 2)} ms max",
            )
            row("main thread busy", "${format(dutyCyclePercent(active), 1)} %")
            separator()
            row(
                "first pitch after start",
                if (firstPitchLatencyMillis < 0) "-" else "${format(firstPitchLatencyMillis, 0)} ms",
            )
            row("pitched / unclear / silent", "$pitchedFrames / $unclearFrames / $silentFrames")
            row("worklet buffer misses", "${active.poolMisses}")
            row(
                "render quanta vs batches",
                "${active.quanta} vs ${active.batchesReceived} " +
                    "(${hopSize / RENDER_QUANTUM} quanta per batch expected)",
            )
        }
        if (click != null) {
            separator()
            val state = click.state.value
            row("metronome", "${state.bpm} bpm, beat ${state.beatInBar + 1}/${state.beatsPerBar}")
            row("beats played", "${state.beatCounter}")
            state.error?.let { row("metronome error", it.name) }
        }
    }
}

/** What fraction of real time the main thread spends in the audio path. Below 100 % or it falls behind. */
private fun dutyCyclePercent(capture: WebAudioCapture): Double {
    val interval = frameInterval.mean
    if (interval <= 0.0) return 0.0
    val copy = capture.copyMillisTotal / maxOf(1, capture.batchesReceived)
    return 100.0 * (dsp.mean + copy) / interval
}

/** Mean, maximum and a rough p95 over a bounded sample, so a long run does not grow without limit. */
private class Statistic(private val capacity: Int = 4096) {
    private val values = DoubleArray(capacity)
    private var count = 0
    private var next = 0

    var max = 0.0
        private set

    fun add(value: Double) {
        if (value > max) max = value
        values[next] = value
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    fun clear() {
        count = 0
        next = 0
        max = 0.0
    }

    val mean: Double get() = if (count == 0) 0.0 else (0 until count).sumOf { values[it] } / count

    /** Not exact: the percentile of the last [capacity] samples, which is what a bench needs. */
    fun percentile(fraction: Double): Double {
        if (count == 0) return 0.0
        val sorted = DoubleArray(count) { values[it] }
        sorted.sort()
        return sorted[((count - 1) * fraction).toInt()]
    }
}

private fun now(): Double = window.performance.now()

private fun latency(seconds: Double): String =
    if (seconds < 0) "not reported" else "${format(seconds * 1000, 2)} ms"

private fun StringBuilder.row(name: String, value: String) {
    append("<tr><th>").append(name).append("</th><td>").append(value).append("</td></tr>")
}

private fun StringBuilder.separator() {
    append("<tr class=\"gap\"><th></th><td></td></tr>")
}

/** The design system's `toFixed` lives in an Android module, so this page has its own two lines. */
private fun format(value: Double, digits: Int): String {
    if (value.isNaN() || value.isInfinite()) return "-"
    var scale = 1.0
    repeat(digits) { scale *= 10 }
    val rounded = kotlin.math.round(value * scale) / scale
    return rounded.toString()
}

private fun button(id: String) = document.getElementById(id) as HTMLButtonElement

private fun element(id: String) = document.getElementById(id) as HTMLElement

private fun selected(id: String) = (document.getElementById(id) as HTMLSelectElement).value

private const val RENDER_INTERVAL_MS = 250
private const val RECENT_NOTES = 12
private const val RENDER_QUANTUM = 128
private const val DEFAULT_FRAME_SIZE = 4096

private const val PAGE = """
<h1>Sax Trainer - web audio bench</h1>
<p>
  The production chain: microphone &rarr; AudioWorklet &rarr; Kotlin/Wasm &rarr;
  <code>WebAudioCapture</code> &rarr; <code>NoteListener</code> &rarr; <code>core:dsp</code>.
  Play a note and watch <em>heard now</em>.
</p>
<p>
  hop <select id="hopSize">
    <option>1024</option><option selected>2048</option><option>4096</option>
  </select>
  sample rate <select id="sampleRate">
    <option value="0" selected>browser default</option>
    <option value="24000">24000</option><option value="16000">16000</option>
  </select>
  click <select id="bpm">
    <option>60</option><option selected>80</option><option>120</option><option>160</option>
  </select>
</p>
<p>
  <button id="startMic">Start microphone</button>
  <button id="stop">Stop</button>
  <button id="startClick">Start click</button>
  <button id="stopClick">Stop click</button>
</p>
<p id="error" style="color:#b00"></p>
<table id="readout"></table>
<style>
  body { font: 14px system-ui, sans-serif; margin: 2rem; max-width: 46rem; }
  table { border-collapse: collapse; }
  th { text-align: left; padding: 2px 16px 2px 0; font-weight: 600; white-space: nowrap; }
  td { padding: 2px 0; font-variant-numeric: tabular-nums; }
  tr.gap th, tr.gap td { padding-top: 10px; }
  button, select { font: inherit; padding: 4px 8px; }
</style>
"""
