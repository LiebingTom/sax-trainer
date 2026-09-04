package com.example.saxtrainer.core.audio.metronome

import android.os.Process
import com.example.saxtrainer.core.audio.AudioConfig
import com.example.saxtrainer.core.audio.playback.PcmAudioOutput
import com.example.saxtrainer.core.music.Tempo
import com.example.saxtrainer.core.music.TimeSignature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sample-accurate metronome.
 *
 * Timing is derived from the audio clock, not from coroutine delays or Compose recomposition: a
 * dedicated thread renders one continuous PCM stream and places each click at an exact sample
 * position. Beat boundaries are accumulated as fractional sample counts, so no rounding error can
 * build up over a long practice session, and the blocking write to [PcmAudioOutput] paces the loop.
 *
 * Beats are published to the UI only once the hardware playback head has passed them, which keeps
 * the visual indicator aligned with what the player hears. Anything that has to line up with the
 * click - a sequence exercise, say - should start from that first published beat rather than from
 * the call to [start], because the stream takes a moment to open.
 */
class MetronomeEngine(
    private val audioConfig: AudioConfig = AudioConfig(),
    private val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
) : Metronome {
    private val _state = MutableStateFlow(
        MetronomeEngineState(beatsPerBar = timeSignature.beatsPerBar),
    )
    override val state: StateFlow<MetronomeEngineState> = _state.asStateFlow()

    @Volatile
    private var running = false

    @Volatile
    private var targetBpm = Metronome.DEFAULT_BPM

    private var renderThread: Thread? = null

    override fun setBpm(bpm: Int) {
        val clamped = bpm.coerceIn(Tempo.SUPPORTED_RANGE)
        targetBpm = clamped
        _state.value = _state.value.copy(bpm = clamped)
    }

    override fun start() {
        if (running) return
        running = true
        _state.value = _state.value.copy(
            isRunning = true,
            beatInBar = 0,
            error = null,
        )
        renderThread = Thread(::renderLoop, "MetronomeEngine").also { it.start() }
    }

    override fun stop() {
        running = false
        renderThread?.join(JOIN_TIMEOUT_MS)
        renderThread = null
        _state.value = _state.value.copy(isRunning = false, beatInBar = 0)
    }

    /** Releases the render thread. The engine can be started again afterwards. */
    override fun release() {
        stop()
    }

    private fun renderLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val sampleRate = audioConfig.sampleRate
        val output = PcmAudioOutput(audioConfig, bufferFrames = RENDER_FRAMES * BUFFER_MULTIPLIER)
        val openError = output.open()
        if (openError != null) {
            running = false
            _state.value = _state.value.copy(
                isRunning = false,
                error = MetronomeError.AUDIO_UNAVAILABLE,
            )
            return
        }

        val clicks = ClickSynth(sampleRate)
        val buffer = ShortArray(RENDER_FRAMES)
        val pendingBeatFrames = LongArray(MAX_PENDING_BEATS)
        val pendingBeatInBar = IntArray(MAX_PENDING_BEATS)
        var pendingHead = 0
        var pendingSize = 0

        var framesWritten = 0L
        var framesToNextBeat = 0.0
        var beatInBar = 0
        var beatCounter = _state.value.beatCounter
        var activeClick: ShortArray? = null
        var clickPosition = 0

        // playbackHeadPosition is a 32-bit frame counter; unwrap it into a monotonic long.
        var previousHead = 0L
        var headWrapOffset = 0L

        try {
            output.play()
            while (running) {
                val samplesPerBeat = sampleRate * SECONDS_PER_MINUTE / targetBpm

                for (i in 0 until RENDER_FRAMES) {
                    if (framesToNextBeat <= 0.0) {
                        activeClick = if (beatInBar == 0) clicks.accentClick else clicks.normalClick
                        clickPosition = 0
                        if (pendingSize < MAX_PENDING_BEATS) {
                            val slot = (pendingHead + pendingSize) % MAX_PENDING_BEATS
                            pendingBeatFrames[slot] = framesWritten + i
                            pendingBeatInBar[slot] = beatInBar
                            pendingSize++
                        }
                        beatInBar = (beatInBar + 1) % timeSignature.beatsPerBar
                        framesToNextBeat += samplesPerBeat
                    }

                    val click = activeClick
                    buffer[i] = if (click != null && clickPosition < click.size) {
                        click[clickPosition++]
                    } else {
                        0
                    }
                    framesToNextBeat -= 1.0
                }

                val written = output.write(buffer, RENDER_FRAMES)
                if (written < 0) {
                    running = false
                    _state.value = _state.value.copy(
                        isRunning = false,
                        error = MetronomeError.AUDIO_UNAVAILABLE,
                    )
                    break
                }
                framesWritten += written

                val rawHead = output.playbackHeadFrames.toLong() and HEAD_MASK
                if (rawHead < previousHead) headWrapOffset += HEAD_WRAP
                previousHead = rawHead
                val playedFrames = headWrapOffset + rawHead

                while (pendingSize > 0 && pendingBeatFrames[pendingHead] < playedFrames) {
                    beatCounter++
                    _state.value = _state.value.copy(
                        beatInBar = pendingBeatInBar[pendingHead],
                        beatCounter = beatCounter,
                    )
                    pendingHead = (pendingHead + 1) % MAX_PENDING_BEATS
                    pendingSize--
                }
            }
        } finally {
            output.close()
        }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
        private const val RENDER_FRAMES = 256
        private const val BUFFER_MULTIPLIER = 4
        private const val MAX_PENDING_BEATS = 16
        private const val JOIN_TIMEOUT_MS = 500L
        private const val HEAD_WRAP = 1L shl 32
        private const val HEAD_MASK = 0xFFFFFFFFL
    }
}
