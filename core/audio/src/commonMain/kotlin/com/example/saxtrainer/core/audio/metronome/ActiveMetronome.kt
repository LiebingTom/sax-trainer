package com.example.saxtrainer.core.audio.metronome

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The metronome the player has open, if any - so something outside its screen can show it and stop
 * it: on Android, the notification that keeps it playing with the phone locked.
 *
 * Only ever the one on the metronome screen: it is attached while that screen exists and detached
 * when it goes, so nothing can reach a click no screen owns, and nothing is left behind to tick on
 * after the screen is gone.
 */
class ActiveMetronome {
    private val current = MutableStateFlow<Metronome?>(null)

    /** What a notification shows of the open metronome - null while none is open. */
    data class Summary(val isRunning: Boolean, val bpm: Int, val beatsPerBar: Int)

    /** The open metronome's [Summary], changing only when that does - not with every beat. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val summary: Flow<Summary?> = current
        .flatMapLatest { metronome -> metronome?.state?.map { it.summary() } ?: flowOf(null) }
        .distinctUntilChanged()

    /** The open metronome's [Summary] right now, or null while none is open. */
    val currentSummary: Summary? get() = current.value?.state?.value?.summary()

    val isRunning: Boolean get() = current.value?.state?.value?.isRunning == true

    /** Makes [metronome] the open one. */
    fun attach(metronome: Metronome) {
        current.value = metronome
    }

    /** Lets go of [metronome] - only if it is still the open one, so a newer screen's stays. */
    fun detach(metronome: Metronome) {
        current.compareAndSet(metronome, null)
    }

    /** Starts the open metronome if it is stopped, stops it if it is running. */
    fun toggle() {
        val metronome = current.value ?: return
        if (metronome.state.value.isRunning) metronome.stop() else metronome.start()
    }

    fun stop() {
        current.value?.stop()
    }

    private fun MetronomeEngineState.summary() = Summary(isRunning, bpm, beatsPerBar)
}
