package com.example.saxtrainer.probe

import com.example.saxtrainer.core.dsp.pitch.YinConfig
import com.example.saxtrainer.core.music.NoteNaming
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSelectElement

/**
 * M3 PROBE - TEMPORARY. Delete with the probe/ directory.
 *
 * A page with three buttons and a table of numbers. Everything it shows is measured; the qualitative
 * observations belong in the milestone report, not here.
 */
private const val DEFAULT_FRAME_SIZE = 4096
private const val DEFAULT_HOP_SIZE = 2048

private val scope = CoroutineScope(Dispatchers.Main)

private var capture: ProbeCapture? = null
private var listener: ProbeListener? = null
private var startedAtMillis = 0.0
private var firstPitchLatencyMillis = -1.0
private var toneStartedAtMillis = 0.0
private var pitchedFrames = 0
private var unclearFrames = 0
private var silentFrames = 0
private var lastHeard = "-"
private val recentNotes = ArrayDeque<String>()

fun main() {
    document.body?.innerHTML = PAGE
    button("startMic").onclick = { start(ProbeCapture.Source.MICROPHONE) }
    button("startTone").onclick = { start(ProbeCapture.Source.TONE) }
    button("stop").onclick = { stop() }
    button("retune").onclick = { retuneToNextNote() }
    window.setInterval({ render(); null }, RENDER_INTERVAL_MS)
}

private fun start(source: ProbeCapture.Source) {
    if (capture != null) return
    reset()

    val hopSize = selected("hopSize").toInt()
    val frameSize = maxOf(DEFAULT_FRAME_SIZE, hopSize * 2)
    val minFrequencyHz = selected("minFrequency").toFloat()
    val requestedRate = selected("sampleRate").toDouble()

    val probe = ProbeCapture(frameSize = frameSize, hopSize = hopSize)
    capture = probe
    startedAtMillis = performanceNow()
    toneStartedAtMillis = startedAtMillis

    scope.launch {
        runCatching {
            probe.start(
                source = source,
                requestedSampleRate = requestedRate,
                toneHz = TENOR_NOTES[0],
            ) { window, _ ->
                onFrame(window)
            }
        }.onFailure { failure ->
            element("error").textContent = "start failed: ${failure.message}"
            capture = null
            return@launch
        }
        listener = ProbeListener(
            sampleRate = probe.sampleRate,
            yin = YinConfig(minFrequencyHz = minFrequencyHz),
        )
    }
}

private fun onFrame(samples: FloatArray) {
    val heard = listener?.hear(samples) ?: return
    when (heard) {
        is ProbeListener.Heard.Silent -> {
            silentFrames++
            lastHeard = "silent"
        }

        is ProbeListener.Heard.Unclear -> {
            unclearFrames++
            lastHeard = "unclear (confidence ${format(heard.confidence.toDouble(), 2)})"
        }

        is ProbeListener.Heard.Pitched -> {
            pitchedFrames++
            if (firstPitchLatencyMillis < 0) {
                firstPitchLatencyMillis = performanceNow() - toneStartedAtMillis
            }
            val label = NoteNaming.INTERNATIONAL.labelOf(heard.note)
            lastHeard = "$label  ${format(heard.frequencyHz.toDouble(), 1)} Hz  " +
                "confidence ${format(heard.confidence.toDouble(), 2)}"
            if (recentNotes.lastOrNull() != label) {
                recentNotes.addLast(label)
                if (recentNotes.size > RECENT_NOTES) recentNotes.removeFirst()
            }
        }
    }
}

private var toneIndex = 0

/** Steps the tone through several tenor notes, which is test case four. */
private fun retuneToNextNote() {
    val probe = capture ?: return
    toneIndex = (toneIndex + 1) % TENOR_NOTES.size
    probe.retune(TENOR_NOTES[toneIndex])
    toneStartedAtMillis = performanceNow()
    firstPitchLatencyMillis = -1.0
}

private fun stop() {
    capture?.stop()
    capture = null
    listener = null
}

private fun reset() {
    firstPitchLatencyMillis = -1.0
    pitchedFrames = 0
    unclearFrames = 0
    silentFrames = 0
    lastHeard = "-"
    recentNotes.clear()
    element("error").textContent = ""
}

private fun render() {
    val probe = capture ?: return
    val elapsedSeconds = (performanceNow() - startedAtMillis) / 1000.0
    val expectedIntervalMs = if (probe.sampleRate == 0) {
        0.0
    } else {
        probe.hopIntervalExpectationMillis()
    }

    element("readout").innerHTML = buildString {
        row("running for", "${format(elapsedSeconds, 1)} s")
        row("heard now", lastHeard)
        row("recent notes", recentNotes.joinToString(" "))
        row("level", "${format(probe.levelDbfs(), 1)} dBFS")
        separator()
        row("sample rate", "${probe.sampleRate} Hz")
        row("hop size", "${selected("hopSize")} samples")
        row("YIN minimum", "${selected("minFrequency")} Hz")
        row("base latency", latency(probe.baseLatencySeconds))
        row("output latency", latency(probe.outputLatencySeconds))
        separator()
        row("batches", "${probe.batchesReceived}")
        row("frames analysed", "${probe.framesEmitted}")
        row(
            "hop interval",
            "${format(probe.hopIntervalMillis.mean, 1)} ms mean, " +
                "${format(probe.hopIntervalMillis.percentile(0.95), 1)} ms p95, " +
                "${format(probe.hopIntervalMillis.max, 1)} ms max " +
                "(expected ${format(expectedIntervalMs, 1)} ms)",
        )
        row(
            "copy out of JS",
            "${format(probe.copyMillis.mean, 3)} ms mean, " +
                "${format(probe.copyMillis.percentile(0.95), 3)} ms p95",
        )
        row(
            "DSP per frame",
            "${format(probe.frameMillis.mean, 2)} ms mean, " +
                "${format(probe.frameMillis.percentile(0.95), 2)} ms p95, " +
                "${format(probe.frameMillis.max, 2)} ms max",
        )
        row("main thread busy", "${format(dutyCyclePercent(probe), 1)} %")
        separator()
        row("first pitch after", if (firstPitchLatencyMillis < 0) "-" else "${format(firstPitchLatencyMillis, 0)} ms")
        row("pitched / unclear / silent", "$pitchedFrames / $unclearFrames / $silentFrames")
        row("worklet buffer misses", "${probe.poolMisses}")
        row(
            "render quanta vs batches",
            "${probe.quanta} vs ${probe.batchesReceived} " +
                "(${probe.expectedQuantaPerBatch()} quanta per batch expected)",
        )
    }
}

/** What fraction of real time the main thread spends inside the DSP. Below 100 % or it cannot keep up. */
private fun dutyCyclePercent(probe: ProbeCapture): Double {
    val interval = probe.hopIntervalMillis.mean
    if (interval <= 0.0) return 0.0
    return 100.0 * (probe.frameMillis.mean + probe.copyMillis.mean) / interval
}

private fun ProbeCapture.hopIntervalExpectationMillis(): Double =
    if (sampleRate == 0) 0.0 else 1000.0 * selected("hopSize").toDouble() / sampleRate

private fun ProbeCapture.expectedQuantaPerBatch(): Int = selected("hopSize").toInt() / RENDER_QUANTUM

private fun ProbeCapture.levelDbfs(): Double = (listener?.lastLevelDbfs ?: -120f).toDouble()

private fun latency(seconds: Double): String =
    if (seconds < 0) "not reported" else "${format(seconds * 1000, 2)} ms"

private fun StringBuilder.row(name: String, value: String) {
    append("<tr><th>").append(name).append("</th><td>").append(value).append("</td></tr>")
}

private fun StringBuilder.separator() {
    append("<tr class=\"gap\"><th></th><td></td></tr>")
}

/** The design system's `toFixed` lives in an Android module, so the probe has its own two lines. */
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

/** Written B♭3, C4, F4 and A4 on a tenor, sounding: the range the trainers actually ask for. */
private val TENOR_NOTES = doubleArrayOf(103.83, 116.54, 155.56, 195.998)

private const val PAGE = """
<h1>Sax Trainer - Web Audio probe (M3)</h1>
<p>Throwaway spike. Microphone or tone &rarr; AudioWorklet &rarr; Kotlin/Wasm &rarr; core:dsp.</p>
<p>
  hop <select id="hopSize">
    <option>1024</option><option selected>2048</option><option>4096</option>
  </select>
  YIN minimum <select id="minFrequency">
    <option selected>60</option><option>100</option><option>120</option>
  </select>
  sample rate <select id="sampleRate">
    <option value="0" selected>browser default</option>
    <option value="24000">24000</option><option value="16000">16000</option>
  </select>
</p>
<p>
  <button id="startMic">Start microphone</button>
  <button id="startTone">Start tone</button>
  <button id="retune">Next tone</button>
  <button id="stop">Stop</button>
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
