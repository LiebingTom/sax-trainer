package com.example.saxtrainer.core.audio.metronome

import com.example.saxtrainer.core.audio.web.AudioBufferHandle
import com.example.saxtrainer.core.audio.web.AudioContextHandle
import com.example.saxtrainer.core.audio.web.closeContext
import com.example.saxtrainer.core.audio.web.createMonoBuffer
import com.example.saxtrainer.core.audio.web.currentTimeOf
import com.example.saxtrainer.core.audio.web.newAudioContext
import com.example.saxtrainer.core.audio.web.playBufferAt
import com.example.saxtrainer.core.audio.web.resume
import com.example.saxtrainer.core.audio.web.sampleRateOf
import com.example.saxtrainer.core.audio.web.setBufferSample
import com.example.saxtrainer.core.audio.web.stateOf
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.TimeSignature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [Metronome] for the browser: a lookahead scheduler against the audio context's clock.
 *
 * Kotlin/Wasm has no threads, so the Android engine's dedicated render loop has no counterpart here.
 * The design survives the move because what makes that loop accurate is not the thread - it is that
 * every click is placed at an exact position on the audio clock rather than played when a timer
 * happens to fire. Web Audio offers the same thing through `AudioBufferSourceNode.start(when)`: a
 * coroutine wakes every [TICK_MILLIS], schedules whatever falls inside the next [LOOKAHEAD_SECONDS],
 * and goes back to sleep. The scheduling coroutine is late by whatever the main thread is busy with;
 * the clicks are not.
 *
 * The published-beat contract is preserved exactly, and it is the part everything else depends on: a
 * beat appears in [state] once `currentTime` has passed the moment it was scheduled for - that is,
 * once it has been heard - never when it was scheduled. `clickCountIn` therefore works here
 * unchanged, and its fifth click is still the downbeat the exercise starts on.
 *
 * What is deliberately *not* solved here is calibration: the click is scheduled against the context
 * clock, and the browser's own `outputLatency` between that clock and the speaker is not compensated
 * for. On the machine the browser tests run on that is 34 ms, and it is a constant offset rather
 * than drift, so the click stays in time with itself and with the exercise; what it does not do is
 * agree with the on-screen beat to the millisecond. Android does not compensate for its own output
 * latency either, so this is the same behaviour on both platforms rather than a web shortfall.
 */
class WebMetronome(
    private val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
) : Metronome {

    private val _state = MutableStateFlow(
        MetronomeEngineState(beatsPerBar = timeSignature.beatsPerBar),
    )
    override val state: StateFlow<MetronomeEngineState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var running = false
    private var targetBpm = Metronome.DEFAULT_BPM
    private var job: Job? = null
    private var context: AudioContextHandle? = null

    /** The context's own rate, once started. The clicks are rendered to match it. */
    var sampleRate: Int = 0
        private set

    override fun setBpm(bpm: Int) {
        val clamped = bpm.coerceIn(Tempo.SUPPORTED_RANGE)
        targetBpm = clamped
        _state.value = _state.value.copy(bpm = clamped)
    }

    override fun start() {
        if (running) return
        running = true
        _state.value = _state.value.copy(isRunning = true, beatInBar = 0, error = null)
        job = scope.launch { schedule() }
    }

    override fun stop() {
        running = false
        job?.cancel()
        job = null
        closeAudio()
        _state.value = _state.value.copy(isRunning = false, beatInBar = 0)
    }

    /** Gives up the audio context. The metronome can be started again afterwards. */
    override fun release() {
        stop()
    }

    private suspend fun schedule() {
        val opened = openContext() ?: run {
            running = false
            _state.value = _state.value.copy(
                isRunning = false,
                error = MetronomeError.AUDIO_UNAVAILABLE,
            )
            return
        }

        val clicks = ClickSynth(sampleRate)
        val accent = opened.bufferOf(clicks.accentClick)
        val normal = opened.bufferOf(clicks.normalClick)

        // Scheduled beats waiting to be heard, oldest first: their audio-clock times and which beat
        // of the bar each is. The queue is what turns "scheduled" into "published once played".
        val pendingTime = ArrayDeque<Double>()
        val pendingBeatInBar = ArrayDeque<Int>()

        var beatInBar = 0
        var beatCounter = _state.value.beatCounter
        // Far enough ahead that the first click is scheduled rather than already late.
        var nextBeat = currentTimeOf(opened) + FIRST_BEAT_DELAY_SECONDS

        while (currentCoroutineIsActive() && running) {
            val now = currentTimeOf(opened)

            while (nextBeat < now + LOOKAHEAD_SECONDS) {
                playBufferAt(opened, if (beatInBar == 0) accent else normal, nextBeat)
                pendingTime.addLast(nextBeat)
                pendingBeatInBar.addLast(beatInBar)
                beatInBar = (beatInBar + 1) % timeSignature.beatsPerBar
                // Read afresh each time, so a tempo change takes effect from the next beat that has
                // not been scheduled yet - the same behaviour the Android engine has.
                nextBeat += SECONDS_PER_MINUTE / targetBpm
            }

            while (pendingTime.isNotEmpty() && pendingTime.first() <= now) {
                pendingTime.removeFirst()
                beatCounter++
                _state.value = _state.value.copy(
                    beatInBar = pendingBeatInBar.removeFirst(),
                    beatCounter = beatCounter,
                )
            }

            delay(TICK_MILLIS)
        }
    }

    private suspend fun openContext(): AudioContextHandle? {
        val created = runCatching { newAudioContext(0.0) }.getOrNull() ?: return null
        context = created
        val rate = sampleRateOf(created).toInt()
        if (rate <= 0) {
            closeAudio()
            return null
        }
        sampleRate = rate
        // A context made before the user has interacted with the page starts suspended; start() is
        // reached from a button, so by here there has been a gesture.
        if (stateOf(created) != "running") {
            runCatching { resume(created).await<JsAny?>() }
        }
        return created
    }

    /**
     * The shared [ClickSynth]'s 16-bit click as an AudioBuffer.
     *
     * Both platforms play the same click, rendered by the same code at the platform's own rate:
     * Android writes the shorts to an AudioTrack, the browser wants floats in a buffer. This runs
     * twice per start, never while audio is flowing.
     */
    private fun AudioContextHandle.bufferOf(click: ShortArray): AudioBufferHandle {
        val buffer = createMonoBuffer(this, click.size)
        for (i in click.indices) {
            setBufferSample(buffer, i, click[i] * SHORT_TO_FLOAT)
        }
        return buffer
    }

    private fun closeAudio() {
        context?.let { runCatching { closeContext(it) } }
        context = null
    }

    private suspend fun currentCoroutineIsActive(): Boolean =
        kotlinx.coroutines.currentCoroutineContext().isActive

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
        const val SHORT_TO_FLOAT = 1f / 32768f

        /** How often the scheduler wakes. Short enough that a published beat is barely late. */
        const val TICK_MILLIS = 25L

        /** How far ahead clicks are placed. Comfortably more than several ticks. */
        const val LOOKAHEAD_SECONDS = 0.15

        /** Head start for the first click, so it is scheduled in the future rather than the past. */
        const val FIRST_BEAT_DELAY_SECONDS = 0.12
    }
}
