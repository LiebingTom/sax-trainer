package com.example.saxtrainer.core.audio.web

import org.khronos.webgl.Float32Array
import org.w3c.dom.MessagePort
import kotlin.js.Promise

/**
 * The Web Audio surface this module needs, hand-written rather than pulled from a wrapper library.
 *
 * Written as `@JsFun` factories and accessors instead of `external class`, because that keeps every
 * awkward part of the API - constructor option objects, constraint dictionaries, transfer lists -
 * on the JavaScript side where it is one line each. Promoted from the M3 probe, which measured this
 * exact surface at 44.5 ms end to end and 4.9 % of the main thread.
 */

/** Opaque handles. Nothing here inspects these, it only passes them back to JavaScript. */
internal external interface AudioContextHandle : JsAny

internal external interface AudioNodeHandle : JsAny

internal external interface MediaStreamHandle : JsAny

internal external interface HopMessage : JsAny

/** The outcome of asking for a microphone: one of the two fields is null. */
internal external interface MicrophoneResult : JsAny

/** An AudioBuffer holding a pre-rendered click. */
internal external interface AudioBufferHandle : JsAny

// --- context ------------------------------------------------------------------------------------

/**
 * A context at [requestedSampleRate], or the browser's own rate when that is zero.
 *
 * The browser may resample rather than reconfigure the device, and it may refuse outright, so what
 * it actually gave us is read back from [sampleRateOf] and never assumed.
 */
@JsFun(
    """(requestedSampleRate) => requestedSampleRate > 0
        ? new AudioContext({ sampleRate: requestedSampleRate })
        : new AudioContext()""",
)
internal external fun newAudioContext(requestedSampleRate: Double): AudioContextHandle

@JsFun("(context) => context.sampleRate")
internal external fun sampleRateOf(context: AudioContextHandle): Double

/** The context's own clock, in seconds. Free of any main-thread scheduling delay. */
@JsFun("(context) => context.currentTime")
internal external fun currentTimeOf(context: AudioContextHandle): Double

/** How much the browser admits to buffering. Undefined in some browsers, hence the fallback. */
@JsFun("(context) => context.baseLatency ?? -1")
internal external fun baseLatencyOf(context: AudioContextHandle): Double

@JsFun("(context) => context.outputLatency ?? -1")
internal external fun outputLatencyOf(context: AudioContextHandle): Double

@JsFun("(context) => context.state")
internal external fun stateOf(context: AudioContextHandle): String

@JsFun("(context) => context.resume()")
internal external fun resume(context: AudioContextHandle): Promise<JsAny?>

@JsFun("(context) => context.close()")
internal external fun closeContext(context: AudioContextHandle): Promise<JsAny?>

@JsFun("(context, url) => context.audioWorklet.addModule(url)")
internal external fun addWorkletModule(context: AudioContextHandle, url: String): Promise<JsAny?>

// --- microphone ---------------------------------------------------------------------------------

/**
 * The microphone, with every browser "helper" turned off.
 *
 * Echo cancellation, noise suppression and automatic gain all mangle a sustained wind tone: the
 * first two gate it, the third flattens the dynamics the level meter is there to show. None of the
 * three is optional to disable.
 *
 * The rejection is caught here and reported as a name rather than thrown, so the capture can turn it
 * into an `AudioCaptureError` value - failures are values on this interface, on both platforms.
 */
@JsFun(
    """() => {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            return Promise.resolve({ stream: null, error: 'Unsupported' });
        }
        return navigator.mediaDevices.getUserMedia({
            audio: {
                echoCancellation: false,
                noiseSuppression: false,
                autoGainControl: false,
            },
        }).then(
            (stream) => ({ stream: stream, error: null }),
            (failure) => ({ stream: null, error: (failure && failure.name) || 'Error' }),
        );
    }""",
)
internal external fun requestMicrophone(): Promise<MicrophoneResult>

@JsFun("(result) => result.error")
internal external fun errorNameOf(result: MicrophoneResult): String?

@JsFun("(result) => result.stream")
internal external fun streamOf(result: MicrophoneResult): MediaStreamHandle

@JsFun("(stream) => stream.getTracks().forEach((track) => track.stop())")
internal external fun stopTracks(stream: MediaStreamHandle)

@JsFun("(context, stream) => context.createMediaStreamSource(stream)")
internal external fun microphoneSource(
    context: AudioContextHandle,
    stream: MediaStreamHandle,
): AudioNodeHandle

/**
 * The browser's own answer to "may this page record?", where it has one.
 *
 * `permissions.query` is not implemented for the microphone everywhere, and it never prompts, so a
 * missing or `prompt` answer means "ask properly" rather than "no".
 */
@JsFun(
    """() => {
        if (!navigator.permissions || !navigator.permissions.query) return Promise.resolve('unknown');
        return navigator.permissions.query({ name: 'microphone' })
            .then((status) => status.state, () => 'unknown');
    }""",
)
internal external fun queryMicrophonePermission(): Promise<JsString>

// --- the capture worklet ------------------------------------------------------------------------

@JsFun(
    """(context, hopSize, poolSize) => new AudioWorkletNode(context, 'sax-capture', {
        numberOfInputs: 1,
        numberOfOutputs: 0,
        channelCount: 1,
        channelCountMode: 'explicit',
        processorOptions: { hopSize: hopSize, poolSize: poolSize },
    })""",
)
internal external fun captureNode(
    context: AudioContextHandle,
    hopSize: Int,
    poolSize: Int,
): AudioNodeHandle

@JsFun("(node) => node.port")
internal external fun portOf(node: AudioNodeHandle): MessagePort

@JsFun("(source, target) => source.connect(target)")
internal external fun connect(source: AudioNodeHandle, target: AudioNodeHandle)

@JsFun("(node) => node.disconnect()")
internal external fun disconnect(node: AudioNodeHandle)

@JsFun("(message) => message.samples")
internal external fun samplesOf(message: HopMessage): Float32Array

@JsFun("(message) => message.capturedAt")
internal external fun capturedAtOf(message: HopMessage): Double

@JsFun("(message) => message.poolMisses")
internal external fun poolMissesOf(message: HopMessage): Int

@JsFun("(message) => message.quanta")
internal external fun quantaOf(message: HopMessage): Int

/** Hands the batch's buffer back so the worklet can fill it again instead of allocating. */
@JsFun("(port, samples) => port.postMessage({ buffer: samples.buffer }, [samples.buffer])")
internal external fun returnBuffer(port: MessagePort, samples: Float32Array)

/**
 * A URL the AudioWorklet can load the worklet source from.
 *
 * The worklet is compiled into the Wasm module as a string rather than shipped as a file, so there
 * is nothing for a consumer to copy into its own resources and nothing that can drift out of step.
 */
@JsFun("(source) => URL.createObjectURL(new Blob([source], { type: 'application/javascript' }))")
internal external fun blobUrl(source: String): String

@JsFun("(url) => URL.revokeObjectURL(url)")
internal external fun revokeBlobUrl(url: String)

// --- the metronome's output ---------------------------------------------------------------------

@JsFun("(context, length) => context.createBuffer(1, length, context.sampleRate)")
internal external fun createMonoBuffer(context: AudioContextHandle, length: Int): AudioBufferHandle

@JsFun("(buffer, index, value) => { buffer.getChannelData(0)[index] = value; }")
internal external fun setBufferSample(buffer: AudioBufferHandle, index: Int, value: Float)

/**
 * Plays [buffer] at audio-clock time [whenSeconds].
 *
 * The scheduling is sample-accurate on the audio thread even though the coroutine that asks for it
 * is not, which is the whole point of scheduling ahead.
 */
@JsFun(
    """(context, buffer, when) => {
        const source = context.createBufferSource();
        source.buffer = buffer;
        source.connect(context.destination);
        source.start(when);
        return source;
    }""",
)
internal external fun playBufferAt(
    context: AudioContextHandle,
    buffer: AudioBufferHandle,
    whenSeconds: Double,
): AudioNodeHandle

/** Monotonic wall clock, for the costs that are not measurable on the audio clock. */
@JsFun("() => performance.now()")
internal external fun performanceNow(): Double
