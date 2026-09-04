package com.example.saxtrainer.core.audio.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * [AudioCapture] backed by [AudioRecord].
 *
 * The recorder is opened when collection starts and always released again, including on
 * cancellation, so leaving a screen or backgrounding the app frees the microphone. Samples are read
 * into one reusable short buffer and converted into one reusable float window that slides by
 * [AudioCaptureConfig.hopSize]; nothing is allocated per frame.
 */
class AudioRecordCapture(
    private val context: Context,
) : AudioCapture {

    override fun frames(config: AudioCaptureConfig): Flow<AudioCaptureEvent> = flow {
        // Checked here as well as by whoever asked for it: permission can be revoked while the
        // app is in the background, and opening the recorder without it throws.
        if (!isRecordingPermitted()) {
            emit(AudioCaptureEvent.Failed(AudioCaptureError.PermissionDenied))
            return@flow
        }

        val minBytes = AudioRecord.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBytes <= 0) {
            emit(AudioCaptureEvent.Failed(AudioCaptureError.UnsupportedConfiguration))
            return@flow
        }

        val bufferBytes = maxOf(minBytes, config.frameSize * BYTES_PER_SAMPLE * BUFFER_MULTIPLIER)
        val recorder = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(config.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()
        } catch (e: SecurityException) {
            emit(AudioCaptureEvent.Failed(AudioCaptureError.PermissionDenied))
            return@flow
        } catch (e: IllegalArgumentException) {
            emit(AudioCaptureEvent.Failed(AudioCaptureError.UnsupportedConfiguration))
            return@flow
        } catch (e: UnsupportedOperationException) {
            emit(AudioCaptureEvent.Failed(AudioCaptureError.InitializationFailed))
            return@flow
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            emit(AudioCaptureEvent.Failed(AudioCaptureError.InitializationFailed))
            return@flow
        }

        val window = FloatArray(config.frameSize)
        val readBuffer = ShortArray(config.hopSize)
        val frame = AudioFrame(window, config.sampleRate)
        var filled = 0
        var samplesRead = 0L
        var samplesSinceLastFrame = 0

        try {
            try {
                recorder.startRecording()
            } catch (e: IllegalStateException) {
                emit(AudioCaptureEvent.Failed(AudioCaptureError.DeviceUnavailable))
                return@flow
            }
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                emit(AudioCaptureEvent.Failed(AudioCaptureError.DeviceUnavailable))
                return@flow
            }

            while (currentCoroutineContext().isActive) {
                val read = recorder.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                if (read < 0) {
                    emit(AudioCaptureEvent.Failed(AudioCaptureError.ReadFailed(read)))
                    return@flow
                }
                if (read == 0) continue

                appendToWindow(window, readBuffer, read)
                filled = minOf(config.frameSize, filled + read)
                samplesRead += read
                samplesSinceLastFrame += read
                if (filled == config.frameSize) {
                    // The first window is new in full; later ones only advance by what was read.
                    frame.position(
                        startSample = samplesRead - config.frameSize,
                        newSamples = samplesSinceLastFrame,
                    )
                    samplesSinceLastFrame = 0
                    emit(AudioCaptureEvent.Frame(frame))
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    /** Shifts the window left by [count] samples and appends the newly read block, converted to float. */
    private fun appendToWindow(window: FloatArray, source: ShortArray, count: Int) {
        val keep = window.size - count
        if (keep > 0) {
            // Always shifts towards zero, so copying a range of the array onto itself is safe.
            window.copyInto(window, 0, count, window.size)
        }
        val destinationStart = maxOf(0, keep)
        val sourceStart = maxOf(0, count - window.size)
        var destination = destinationStart
        for (i in sourceStart until count) {
            window[destination++] = source[i] * SHORT_TO_FLOAT
        }
    }

    private fun isRecordingPermitted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val BUFFER_MULTIPLIER = 2
        const val SHORT_TO_FLOAT = 1f / 32768f
    }
}
