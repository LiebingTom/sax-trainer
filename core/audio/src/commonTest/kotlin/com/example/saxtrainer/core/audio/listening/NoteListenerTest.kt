package com.example.saxtrainer.core.audio.listening

import com.example.saxtrainer.core.audio.capture.AudioCapture
import com.example.saxtrainer.core.audio.capture.AudioCaptureConfig
import com.example.saxtrainer.core.audio.capture.AudioCaptureError
import com.example.saxtrainer.core.audio.capture.AudioCaptureEvent
import com.example.saxtrainer.core.audio.capture.AudioFrame
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the listener makes of what it hears.
 *
 * The signals are synthesised rather than stubbed, so the real gate, the real YIN detector and the
 * real nearest-note arithmetic all run - the same idiom the trainers' tests use.
 */
class NoteListenerTest {

    private val config = AudioCaptureConfig()

    @Test
    fun `a clean tone is a pitch, and the right one`() = runTest {
        val a4 = Note(NoteName.A, 4)

        val heard = listener(tone(a4.frequencyHz())).listen().toList()

        assertTrue(heard.all { it is Hearing.Pitched }, "heard $heard")
        assertEquals(a4, (heard.first() as Hearing.Pitched).detected.note)
    }

    @Test
    fun `silence is silence, not an unclear note`() = runTest {
        val heard = listener(FloatArray(config.frameSize)).listen().toList()

        assertEquals(listOf(Hearing.Silent, Hearing.Silent), heard)
    }

    @Test
    fun `a loud sound with no pitch in it is unclear, not silence and not a note`() = runTest {
        // Above the gate, so somebody is making a sound - it just is not a note. Reporting this as
        // silence would lose it, and reporting it as a note would invent one.
        val heard = listener(noise()).listen().toList()

        assertTrue(heard.all { it is Hearing.Unclear }, "heard $heard")
    }

    @Test
    fun `a sound too quiet to be meant is below the gate`() = runTest {
        val heard = listener(tone(Note(NoteName.A, 4).frequencyHz(), amplitude = 0.0005)).listen()
            .toList()

        assertEquals(listOf(Hearing.Silent, Hearing.Silent), heard)
    }

    @Test
    fun `the plain form still reports exactly the pitches`() = runTest {
        // The regression guard for Long Tones and the sequence trainer, which never moved off it.
        val a4 = Note(NoteName.A, 4)

        val notes = listener(tone(a4.frequencyHz())).detectedNotes().toList()
        val silence = listener(FloatArray(config.frameSize)).detectedNotes().toList()
        val unclear = listener(noise()).detectedNotes().toList()

        assertEquals(a4, notes.first()?.note)
        assertTrue(silence.all { it == null }, "silence gave $silence")
        assertTrue(unclear.all { it == null }, "noise gave $unclear")
    }

    @Test
    fun `a capture that fails ends the listening instead of throwing`() = runTest {
        val failing = object : AudioCapture {
            override fun frames(config: AudioCaptureConfig): Flow<AudioCaptureEvent> =
                flow { emit(AudioCaptureEvent.Failed(AudioCaptureError.DeviceUnavailable)) }
        }

        assertTrue(NoteListener(failing, config).listen().toList().isEmpty())
    }

    @Test
    fun `nothing is reported before a frame arrives`() = runTest {
        val quiet = object : AudioCapture {
            override fun frames(config: AudioCaptureConfig): Flow<AudioCaptureEvent> = flow {}
        }

        assertNull(NoteListener(quiet, config).listen().toList().firstOrNull())
    }

    @Test
    fun `the pitch follows the rate the frames arrive at, not the rate that was asked for`() =
        runTest {
            // A browser gives whatever rate its audio context runs at and is free to ignore what was
            // requested, so the configured rate is a preference and the frame's is the fact. Reading
            // the configured one here would report this A4 as an A5.
            val a4 = Note(NoteName.A, 4)
            val actualRate = 24_000
            val samples = FloatArray(config.frameSize) { i ->
                (0.5 * sin(2.0 * PI * a4.frequencyHz() * i / actualRate)).toFloat()
            }

            val heard = NoteListener(FixedCapture(samples, sampleRate = actualRate), config)
                .listen().toList()

            val pitched = heard.filterIsInstance<Hearing.Pitched>()
            assertTrue(pitched.isNotEmpty(), "heard $heard")
            assertEquals(a4, pitched.first().detected.note, "the frame's sample rate was ignored")
        }

    private fun listener(samples: FloatArray) = NoteListener(FixedCapture(samples), config)

    private fun tone(frequencyHz: Double, amplitude: Double = 0.5): FloatArray =
        FloatArray(config.frameSize) { i ->
            (amplitude * sin(2.0 * PI * frequencyHz * i / config.sampleRate)).toFloat()
        }

    private fun noise(seed: Int = 1): FloatArray {
        val random = Random(seed)
        return FloatArray(config.frameSize) { (random.nextDouble() - 0.5).toFloat() }
    }

    /**
     * Plays the same block of samples twice, so a test can see a steady reading.
     *
     * [sampleRate] defaults to the configured rate - the Android case, where what was asked for is
     * what arrives - and can differ from it, which is the web case.
     */
    private class FixedCapture(
        private val samples: FloatArray,
        private val sampleRate: Int = 0,
    ) : AudioCapture {
        override fun frames(config: AudioCaptureConfig): Flow<AudioCaptureEvent> = flow {
            val frame = AudioFrame(samples, if (sampleRate > 0) sampleRate else config.sampleRate)
            repeat(2) { index ->
                frame.position(
                    startSample = (index * config.hopSize).toLong(),
                    newSamples = config.hopSize,
                )
                emit(AudioCaptureEvent.Frame(frame))
            }
        }
    }
}
