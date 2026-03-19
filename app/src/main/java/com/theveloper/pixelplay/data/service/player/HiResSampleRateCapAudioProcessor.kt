package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil

/**
 * Caps ultra-high-rate PCM to a rate Android devices handle more reliably.
 *
 * Media3/AudioTrack can end up in unstable device-specific paths for sample rates above 192 kHz.
 * Reducing 352.8/384 kHz streams before they reach the sink avoids the "loading audio" hang while
 * preserving normal playback for standard and regular hi-res files.
 */
@UnstableApi
class HiResSampleRateCapAudioProcessor(
    private val maxOutputSampleRateHz: Int = 192_000
) : AudioProcessor {

    private var inputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputFormat: AudioFormat = AudioFormat.NOT_SET
    private var downsampleFactor: Int = 1
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var pendingBytes = ByteArray(0)
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        val shouldDownsample = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount > 0 &&
            inputAudioFormat.sampleRate > maxOutputSampleRateHz

        if (!shouldDownsample) {
            inputFormat = AudioFormat.NOT_SET
            outputFormat = AudioFormat.NOT_SET
            downsampleFactor = 1
            pendingBytes = ByteArray(0)
            return inputAudioFormat
        }

        downsampleFactor = ceil(
            inputAudioFormat.sampleRate.toDouble() / maxOutputSampleRateHz.toDouble()
        ).toInt().coerceAtLeast(2)

        inputFormat = inputAudioFormat
        outputFormat = AudioFormat(
            inputAudioFormat.sampleRate / downsampleFactor,
            inputAudioFormat.channelCount,
            C.ENCODING_PCM_16BIT
        )
        pendingBytes = ByteArray(0)
        return outputFormat
    }

    override fun isActive(): Boolean = outputFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) {
            return
        }

        val newBytes = ByteArray(inputBuffer.remaining())
        inputBuffer.get(newBytes)

        val combinedBytes = ByteArray(pendingBytes.size + newBytes.size)
        if (pendingBytes.isNotEmpty()) {
            pendingBytes.copyInto(combinedBytes, endIndex = pendingBytes.size)
        }
        if (newBytes.isNotEmpty()) {
            newBytes.copyInto(combinedBytes, destinationOffset = pendingBytes.size)
        }

        val bytesPerFrame = inputFormat.channelCount * Short.SIZE_BYTES
        val processableFrameCount = (combinedBytes.size / bytesPerFrame) / downsampleFactor
        val processableBytes = processableFrameCount * downsampleFactor * bytesPerFrame
        val requiredCapacity = processableFrameCount * bytesPerFrame

        pendingBytes = combinedBytes.copyOfRange(processableBytes, combinedBytes.size)

        if (processableFrameCount == 0) {
            outputBuffer = AudioProcessor.EMPTY_BUFFER
            return
        }

        outputBuffer = if (outputBuffer.capacity() < requiredCapacity) {
            ByteBuffer.allocateDirect(requiredCapacity).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
            outputBuffer
        }

        val shortInput = ByteBuffer
            .wrap(combinedBytes, 0, processableBytes)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()

        val channelAccumulator = IntArray(inputFormat.channelCount)

        repeat(processableFrameCount) {
            java.util.Arrays.fill(channelAccumulator, 0)

            repeat(downsampleFactor) {
                for (channel in 0 until inputFormat.channelCount) {
                    channelAccumulator[channel] += shortInput.get().toInt()
                }
            }

            for (channel in 0 until inputFormat.channelCount) {
                val averaged = (channelAccumulator[channel] / downsampleFactor)
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                outputBuffer.putShort(averaged.toShort())
            }
        }

        outputBuffer.flip()
    }

    override fun getOutput(): ByteBuffer {
        val pendingOutput = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return pendingOutput
    }

    override fun isEnded(): Boolean = inputEnded && pendingBytes.isEmpty() && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun queueEndOfStream() {
        pendingBytes = ByteArray(0)
        inputEnded = true
    }

    @Deprecated("Media3 AudioProcessor now prefers flush(StreamMetadata); kept for interface compatibility")
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        pendingBytes = ByteArray(0)
        inputEnded = false
    }

    override fun reset() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        pendingBytes = ByteArray(0)
        inputEnded = false
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
        downsampleFactor = 1
    }
}
