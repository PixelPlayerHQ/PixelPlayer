package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * An [AudioProcessor] that downsamples audio whose sample rate exceeds
 * [MAX_OUTPUT_SAMPLE_RATE] (192 kHz).
 *
 * Activates for any PCM encoding ([C.ENCODING_PCM_FLOAT], [C.ENCODING_PCM_32BIT],
 * [C.ENCODING_PCM_16BIT]) when the sample rate exceeds
 * the ceiling. Always outputs [C.ENCODING_PCM_FLOAT] so AudioTrack can play it.
 *
 * Downsampling method: box-filter decimation (averaging [downsampleFactor] consecutive
 * input frames).
 */
@UnstableApi
class HighResSamplerAudioProcessor : AudioProcessor {

    companion object {
        /** Android's AudioTrack.SAMPLE_RATE_HZ_MAX */
        private const val MAX_OUTPUT_SAMPLE_RATE = 192_000

        private val SUPPORTED_ENCODINGS = setOf(
            C.ENCODING_PCM_FLOAT,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_16BIT
        )
    }

    private var inputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private var downsampleFactor: Int = 1

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET

        if (inputAudioFormat == AudioFormat.NOT_SET) return inputAudioFormat

        val sampleRate = inputAudioFormat.sampleRate

        if (sampleRate <= MAX_OUTPUT_SAMPLE_RATE || inputAudioFormat.encoding !in SUPPORTED_ENCODINGS) {
            return inputAudioFormat // pass-through
        }

        downsampleFactor = (sampleRate + MAX_OUTPUT_SAMPLE_RATE - 1) / MAX_OUTPUT_SAMPLE_RATE
        val outputSampleRate = sampleRate / downsampleFactor

        Timber.tag("HighResSampler").i(
            "Downsampling %d Hz → %d Hz (factor %d, %d-ch, encoding=%d)",
            sampleRate, outputSampleRate, downsampleFactor,
            inputAudioFormat.channelCount, inputAudioFormat.encoding
        )

        inputFormat = inputAudioFormat
        // Always output PCM_FLOAT — AudioTrack supports it up to 192 kHz
        outputFormat = AudioFormat(outputSampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_FLOAT)
        return outputFormat
    }

    override fun isActive(): Boolean = outputFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) return

        val channels = inputFormat.channelCount
        val encoding = inputFormat.encoding

        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_32BIT -> 4
            C.ENCODING_PCM_16BIT -> 2
            else -> return
        }
        val bytesPerFrame = channels * bytesPerSample
        val bytesPerInputGroup = bytesPerFrame * downsampleFactor

        val inputRemaining = inputBuffer.remaining()
        val outputFrameCount = inputRemaining / bytesPerInputGroup
        if (outputFrameCount == 0) return

        // Output is always PCM_FLOAT (4 bytes per sample)
        val requiredCapacity = outputFrameCount * channels * 4
        if (outputBuffer.capacity() < requiredCapacity) {
            outputBuffer = ByteBuffer.allocateDirect(requiredCapacity).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        val inputBytes = inputBuffer

        repeat(outputFrameCount) {
            val sums = FloatArray(channels)
            repeat(downsampleFactor) {
                for (ch in 0 until channels) {
                    sums[ch] += readSampleAsFloat(inputBytes, encoding)
                }
            }
            for (ch in 0 until channels) {
                outputBuffer.putFloat(sums[ch] / downsampleFactor)
            }
        }

        // Note: inputBuffer.position() was already advanced by readSampleAsFloat() calls above.
        // Do NOT advance it again here.
        outputBuffer.flip()
    }

    /**
     * Reads one sample from [buf] (advancing its position) and converts it to [-1.0, 1.0] float.
     */
    private fun readSampleAsFloat(buf: ByteBuffer, encoding: Int): Float {
        return when (encoding) {
            C.ENCODING_PCM_FLOAT -> buf.float
            C.ENCODING_PCM_32BIT -> buf.int / 2147483648f   // / 2^31
            C.ENCODING_PCM_16BIT -> buf.short / 32768f      // / 2^15
            else -> 0f
        }
    }

    override fun getOutput(): ByteBuffer {
        val pending = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return pending
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
        downsampleFactor = 1
    }
}
