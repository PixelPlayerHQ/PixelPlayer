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

    private companion object {
        private val NATIVE_ORDER = ByteOrder.nativeOrder()
        private val NATIVE_ORDER_IS_BIG_ENDIAN = NATIVE_ORDER == ByteOrder.BIG_ENDIAN
    }

    private var inputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputFormat: AudioFormat = AudioFormat.NOT_SET
    private var downsampleFactor: Int = 1
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var pendingBytes = ByteArray(0)
    private var pendingSize = 0
    private var shortAccumulators = IntArray(0)
    private var floatAccumulators = FloatArray(0)
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        val shouldDownsample = (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) &&
            inputAudioFormat.channelCount > 0 &&
            inputAudioFormat.sampleRate > maxOutputSampleRateHz

        if (!shouldDownsample) {
            inputFormat = AudioFormat.NOT_SET
            outputFormat = AudioFormat.NOT_SET
            downsampleFactor = 1
            pendingSize = 0
            return inputAudioFormat
        }

        downsampleFactor = ceil(
            inputAudioFormat.sampleRate.toDouble() / maxOutputSampleRateHz.toDouble()
        ).toInt().coerceAtLeast(2)

        inputFormat = inputAudioFormat
        outputFormat = AudioFormat(
            inputAudioFormat.sampleRate / downsampleFactor,
            inputAudioFormat.channelCount,
            inputAudioFormat.encoding
        )
        pendingSize = 0
        ensurePendingCapacity(maxPendingBytes())
        ensureAccumulatorCapacity(inputAudioFormat.channelCount)
        return outputFormat
    }

    override fun isActive(): Boolean = outputFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) return

        if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) {
            processFloat(inputBuffer)
        } else {
            process16Bit(inputBuffer)
        }
        inputBuffer.position(inputBuffer.limit())
    }

    private fun process16Bit(inputBuffer: ByteBuffer) {
        val bytesPerFrame = inputFormat.channelCount * Short.SIZE_BYTES
        val processableFrameCount = processableFrameCount(bytesPerFrame, inputBuffer.remaining())
        if (processableFrameCount == 0) {
            outputBuffer = AudioProcessor.EMPTY_BUFFER
            stashPendingBytes(inputBuffer)
            return
        }

        outputBuffer = ensureOutputBuffer(processableFrameCount * bytesPerFrame)
        val shortInput = inputBuffer.duplicate().order(NATIVE_ORDER)
        var pendingReadOffset = 0

        repeat(processableFrameCount) {
            java.util.Arrays.fill(shortAccumulators, 0)
            repeat(downsampleFactor) {
                if (pendingReadOffset < pendingSize) {
                    for (ch in 0 until inputFormat.channelCount) {
                        shortAccumulators[ch] += readShortFromPending(pendingReadOffset).toInt()
                        pendingReadOffset += Short.SIZE_BYTES
                    }
                } else {
                    for (ch in 0 until inputFormat.channelCount) {
                        shortAccumulators[ch] += shortInput.short.toInt()
                    }
                }
            }
            for (ch in 0 until inputFormat.channelCount) {
                outputBuffer.putShort(
                    (shortAccumulators[ch] / downsampleFactor)
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                )
            }
        }
        storePendingRemainder(pendingReadOffset, shortInput)
        outputBuffer.flip()
    }

    private fun processFloat(inputBuffer: ByteBuffer) {
        val bytesPerSample = Float.SIZE_BYTES
        val bytesPerFrame = inputFormat.channelCount * bytesPerSample
        val processableFrameCount = processableFrameCount(bytesPerFrame, inputBuffer.remaining())
        if (processableFrameCount == 0) {
            outputBuffer = AudioProcessor.EMPTY_BUFFER
            stashPendingBytes(inputBuffer)
            return
        }

        outputBuffer = ensureOutputBuffer(processableFrameCount * bytesPerFrame)
        val floatInput = inputBuffer.duplicate().order(NATIVE_ORDER)
        var pendingReadOffset = 0

        repeat(processableFrameCount) {
            java.util.Arrays.fill(floatAccumulators, 0f)
            repeat(downsampleFactor) {
                if (pendingReadOffset < pendingSize) {
                    for (ch in 0 until inputFormat.channelCount) {
                        floatAccumulators[ch] += readFloatFromPending(pendingReadOffset)
                        pendingReadOffset += Float.SIZE_BYTES
                    }
                } else {
                    for (ch in 0 until inputFormat.channelCount) {
                        floatAccumulators[ch] += floatInput.float
                    }
                }
            }
            for (ch in 0 until inputFormat.channelCount) {
                outputBuffer.putFloat((floatAccumulators[ch] / downsampleFactor).coerceIn(-1f, 1f))
            }
        }
        storePendingRemainder(pendingReadOffset, floatInput)
        outputBuffer.flip()
    }

    private fun processableFrameCount(bytesPerFrame: Int, incomingBytes: Int): Int {
        val totalFrames = (pendingSize + incomingBytes) / bytesPerFrame
        return totalFrames / downsampleFactor
    }

    private fun maxPendingBytes(): Int {
        val sampleBytes = if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) Float.SIZE_BYTES else Short.SIZE_BYTES
        return inputFormat.channelCount * sampleBytes * downsampleFactor
    }

    private fun ensurePendingCapacity(requiredCapacity: Int) {
        if (pendingBytes.size >= requiredCapacity) return
        pendingBytes = pendingBytes.copyOf(requiredCapacity.coerceAtLeast(pendingBytes.size * 2).coerceAtLeast(1))
    }

    private fun ensureAccumulatorCapacity(channelCount: Int) {
        if (shortAccumulators.size < channelCount) {
            shortAccumulators = IntArray(channelCount)
        }
        if (floatAccumulators.size < channelCount) {
            floatAccumulators = FloatArray(channelCount)
        }
    }

    private fun ensureOutputBuffer(requiredCapacity: Int): ByteBuffer {
        return if (outputBuffer.capacity() < requiredCapacity) {
            ByteBuffer.allocateDirect(requiredCapacity).order(NATIVE_ORDER).also {
                outputBuffer = it
            }
        } else {
            outputBuffer.clear()
            outputBuffer
        }
    }

    private fun stashPendingBytes(inputBuffer: ByteBuffer) {
        ensurePendingCapacity(pendingSize + inputBuffer.remaining())
        val source = inputBuffer.duplicate()
        source.get(pendingBytes, pendingSize, source.remaining())
        pendingSize += inputBuffer.remaining()
    }

    private fun storePendingRemainder(pendingReadOffset: Int, inputBuffer: ByteBuffer) {
        val unreadPendingBytes = pendingSize - pendingReadOffset
        val remainingInputBytes = inputBuffer.remaining()
        ensurePendingCapacity(unreadPendingBytes + remainingInputBytes)
        if (unreadPendingBytes > 0 && pendingReadOffset > 0) {
            pendingBytes.copyInto(
                destination = pendingBytes,
                destinationOffset = 0,
                startIndex = pendingReadOffset,
                endIndex = pendingSize
            )
        }
        if (remainingInputBytes > 0) {
            inputBuffer.get(pendingBytes, unreadPendingBytes, remainingInputBytes)
        }
        pendingSize = unreadPendingBytes + remainingInputBytes
    }

    private fun readShortFromPending(offset: Int): Short {
        val byte0 = pendingBytes[offset].toInt() and 0xFF
        val byte1 = pendingBytes[offset + 1].toInt() and 0xFF
        val bits = if (NATIVE_ORDER_IS_BIG_ENDIAN) {
            (byte0 shl 8) or byte1
        } else {
            (byte1 shl 8) or byte0
        }
        return bits.toShort()
    }

    private fun readFloatFromPending(offset: Int): Float {
        val intBits = readIntFromPending(offset)
        return Float.fromBits(intBits)
    }

    private fun readIntFromPending(offset: Int): Int {
        val byte0 = pendingBytes[offset].toInt() and 0xFF
        val byte1 = pendingBytes[offset + 1].toInt() and 0xFF
        val byte2 = pendingBytes[offset + 2].toInt() and 0xFF
        val byte3 = pendingBytes[offset + 3].toInt() and 0xFF
        return if (NATIVE_ORDER_IS_BIG_ENDIAN) {
            (byte0 shl 24) or (byte1 shl 16) or (byte2 shl 8) or byte3
        } else {
            (byte3 shl 24) or (byte2 shl 16) or (byte1 shl 8) or byte0
        }
    }

    override fun getOutput(): ByteBuffer {
        val pendingOutput = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return pendingOutput
    }

    override fun isEnded(): Boolean = inputEnded && pendingSize == 0 && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun queueEndOfStream() {
        pendingSize = 0
        inputEnded = true
    }

    @Deprecated("Media3 AudioProcessor now prefers flush(StreamMetadata); kept for interface compatibility")
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        pendingSize = 0
        inputEnded = false
    }

    override fun reset() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        pendingSize = 0
        inputEnded = false
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
        downsampleFactor = 1
    }
}
