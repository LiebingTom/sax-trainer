package com.example.saxtrainer.probe

import org.khronos.webgl.Float32Array
import org.w3c.dom.MessagePort
import kotlin.js.Promise

/**
 * M3 PROBE - TEMPORARY WEB-SPECIFIC CODE. Not production. Delete with the probe/ directory.
 *
 * The Web Audio surface the probe needs, hand-written rather than pulled from a wrapper library.
 * Written as `@JsFun` factories and accessors instead of `external class`, because that keeps every
 * awkward part of the API - constructor option objects, constraint dictionaries, transfer lists -
 * on the JavaScript side where it is one line each.
 */

/** Opaque handles. The probe never inspects these, it only passes them back to JavaScript. */
external interface AudioContextHandle : JsAny

external interface AudioNodeHandle : JsAny

external interface MediaStreamHandle : JsAny

external interface HopMessage : JsAny

/**
 * A context at [requestedSampleRate], or the browser's own rate when that is zero.
 *
 * Asking for a rate is lever three of the performance plan: the browser may resample rather than
 * reconfigure the device, and it may refuse outright, so what it actually gave us is read back from
 * [sampleRateOf] rather than assumed.
 */
@JsFun(
    """(requestedSampleRate) => requestedSampleRate > 0
        ? new AudioContext({ sampleRate: requestedSampleRate })
        : new AudioContext()""",
)
external fun newAudioContext(requestedSampleRate: Double): AudioContextHandle

@JsFun("(context) => context.sampleRate")
external fun sampleRateOf(context: AudioContextHandle): Double

/** The context's own clock, in seconds. Free of any main-thread scheduling delay. */
@JsFun("(context) => context.currentTime")
external fun currentTimeOf(context: AudioContextHandle): Double

/** How much the browser admits to buffering. Undefined in some browsers, hence the fallback. */
@JsFun("(context) => context.baseLatency ?? -1")
external fun baseLatencyOf(context: AudioContextHandle): Double

@JsFun("(context) => context.outputLatency ?? -1")
external fun outputLatencyOf(context: AudioContextHandle): Double

@JsFun("(context) => context.state")
external fun stateOf(context: AudioContextHandle): String

@JsFun("(context) => context.resume()")
external fun resume(context: AudioContextHandle): Promise<JsAny?>

@JsFun("(context) => context.close()")
external fun close(context: AudioContextHandle): Promise<JsAny?>

@JsFun("(context, url) => context.audioWorklet.addModule(url)")
external fun addWorkletModule(context: AudioContextHandle, url: String): Promise<JsAny?>

/**
 * The microphone, with every browser "helper" turned off.
 *
 * Echo cancellation, noise suppression and automatic gain all mangle a sustained wind tone: the
 * first two gate it, the third flattens the dynamics the level meter is there to show. None of the
 * three is optional to disable.
 */
@JsFun(
    """() => navigator.mediaDevices.getUserMedia({
        audio: {
            echoCancellation: false,
            noiseSuppression: false,
            autoGainControl: false,
        },
    })""",
)
external fun getMicrophone(): Promise<MediaStreamHandle>

@JsFun("(stream) => stream.getTracks().forEach((track) => track.stop())")
external fun stopTracks(stream: MediaStreamHandle)

@JsFun("(context, stream) => context.createMediaStreamSource(stream)")
external fun microphoneSource(
    context: AudioContextHandle,
    stream: MediaStreamHandle,
): AudioNodeHandle

/**
 * A steady tone, used instead of the microphone when the probe runs unattended.
 *
 * It exercises the identical chain - the worklet cannot tell where its samples came from - so every
 * number the probe reports except the microphone driver's own latency is measured the same way.
 */
@JsFun(
    """(context, frequency, gain) => {
        const oscillator = context.createOscillator();
        const level = context.createGain();
        oscillator.frequency.value = frequency;
        level.gain.value = gain;
        oscillator.connect(level);
        oscillator.start();
        // The gain node is what the chain connects to, but the oscillator is what can be retuned,
        // so it travels along on the node the caller keeps.
        level.oscillator = oscillator;
        return level;
    }""",
)
external fun toneSource(
    context: AudioContextHandle,
    frequencyHz: Double,
    gain: Double,
): AudioNodeHandle

@JsFun("(node, frequency) => { (node.oscillator ?? node).frequency.value = frequency; }")
external fun setFrequency(node: AudioNodeHandle, frequencyHz: Double)

@JsFun(
    """(context, hopSize, poolSize) => new AudioWorkletNode(context, 'sax-capture', {
        numberOfInputs: 1,
        numberOfOutputs: 0,
        channelCount: 1,
        channelCountMode: 'explicit',
        processorOptions: { hopSize: hopSize, poolSize: poolSize },
    })""",
)
external fun captureNode(
    context: AudioContextHandle,
    hopSize: Int,
    poolSize: Int,
): AudioNodeHandle

@JsFun("(node) => node.port")
external fun portOf(node: AudioNodeHandle): MessagePort

@JsFun("(source, target) => source.connect(target)")
external fun connect(source: AudioNodeHandle, target: AudioNodeHandle)

@JsFun("(node) => node.disconnect()")
external fun disconnect(node: AudioNodeHandle)

// --- reading one batch out of the worklet -------------------------------------------------------

@JsFun("(message) => message.samples")
external fun samplesOf(message: HopMessage): Float32Array

@JsFun("(message) => message.capturedAt")
external fun capturedAtOf(message: HopMessage): Double

@JsFun("(message) => message.posted")
external fun postedCountOf(message: HopMessage): Int

@JsFun("(message) => message.poolMisses")
external fun poolMissesOf(message: HopMessage): Int

@JsFun("(message) => message.quanta")
external fun quantaOf(message: HopMessage): Int

/** Hands the batch's buffer back so the worklet can fill it again instead of allocating. */
@JsFun("(port, samples) => port.postMessage({ buffer: samples.buffer }, [samples.buffer])")
external fun returnBuffer(port: MessagePort, samples: Float32Array)

/** Monotonic wall clock, for the costs that are not measurable on the audio clock. */
@JsFun("() => performance.now()")
external fun performanceNow(): Double

/**
 * A URL the AudioWorklet can load the generated worklet source from, without a server.
 *
 * Only the headless test needs this; the page loads capture-worklet.js over HTTP like any resource.
 */
@JsFun("(source) => URL.createObjectURL(new Blob([source], { type: 'application/javascript' }))")
external fun blobUrl(source: String): String

/**
 * Two seconds of white noise on a loop: a sound with no pitch in it.
 *
 * The case that matters most for a trainer - a breath, a key click, a room - must read as unclear
 * and never as a wrong note.
 */
@JsFun(
    """(context, gain) => {
        const buffer = context.createBuffer(1, context.sampleRate * 2, context.sampleRate);
        const data = buffer.getChannelData(0);
        for (let i = 0; i < data.length; i++) data[i] = Math.random() * 2 - 1;
        const noise = context.createBufferSource();
        noise.buffer = buffer;
        noise.loop = true;
        const level = context.createGain();
        level.gain.value = gain;
        noise.connect(level);
        noise.start();
        return level;
    }""",
)
external fun noiseSource(context: AudioContextHandle, gain: Double): AudioNodeHandle

/** Turns a source on or off without rebuilding the graph, so an onset has an exact moment. */
@JsFun("(node, gain) => { node.gain.value = gain; }")
external fun setGain(node: AudioNodeHandle, gain: Double)
