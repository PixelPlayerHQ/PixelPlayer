package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * An [AudioProcessor] that downmixes 5.1 (6-channel) and 7.1 (8-channel) surround audio
 * to stereo (2-channel) PCM using the standard Dolby downmix matrix coefficients.
 *
 * Downmix matrix:
 * ```
 *   L = FL + 0.707·FC + 0.707·SL [+ 0.707·SBL] + 0.707·LFE
 *   R = FR + 0.707·FC + 0.707·SR [+ 0.707·SBR] + 0.707·LFE
 * ```
 *
 * FFmpeg output channel order assumed:
 * - 5.1: FL, FR, FC, LFE, SL, SR
 * - 7.1: FL, FR, FC, LFE, SL, SR, SBL, SBR
 *
 * This processor is only active for 6-channel or 8-channel 16-bit PCM input.
 * All other formats are passed through without modification.
 */
@UnstableApi
class SurroundDownmixProcessor : AudioProcessor {

    companion object {
        /** Dolby standard surround downmix coefficient: 1/√2 ≈ 0.707 */
        private const val COEFF_SURROUND = 0.707f

        /** LFE (subwoofer) mix coefficient */
        private const val COEFF_LFE = 0.707f

        // 5.1 channel indices (FFmpeg order)
        private const val FL_51  = 0
        private const val FR_51  = 1
        private const val FC_51  = 2
        private const val LFE_51 = 3
        private const val SL_51  = 4
        private const val SR_51  = 5

        // 7.1 channel indices (FFmpeg order)
        private const val FL_71  = 0
        private const val FR_71  = 1
        private const val FC_71  = 2
        private const val LFE_71 = 3
        private const val SL_71  = 4
        private const val SR_71  = 5
        private const val SBL_71 = 6
        private const val SBR_71 = 7
    }

    private var inputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        val isSupported = (inputAudioFormat.channelCount == 6 || inputAudioFormat.channelCount == 8)
                && inputAudioFormat.encoding == C.ENCODING_PCM_16BIT

        return if (isSupported) {
            inputFormat = inputAudioFormat
            outputFormat = AudioFormat(
                inputAudioFormat.sampleRate,
                /* channelCount = */ 2,
                C.ENCODING_PCM_16BIT
            )
            outputFormat
        } else {
            inputFormat = AudioFormat.NOT_SET
            outputFormat = AudioFormat.NOT_SET
            inputAudioFormat // pass-through
        }
    }

    override fun isActive(): Boolean = outputFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) return

        val channelCount = inputFormat.channelCount
        val bytesPerFrame = channelCount * Short.SIZE_BYTES
        val frameCount = inputBuffer.remaining() / bytesPerFrame

        // Stereo output: 2 channels × 2 bytes per frame
        val requiredCapacity = frameCount * 2 * Short.SIZE_BYTES
        if (outputBuffer.capacity() < requiredCapacity) {
            outputBuffer = ByteBuffer.allocateDirect(requiredCapacity).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        val shortInput = inputBuffer.asShortBuffer()

        repeat(frameCount) {
            val samples = ShortArray(channelCount) { shortInput.get() }
            val (left, right) = if (channelCount == 6) downmix51(samples) else downmix71(samples)

            outputBuffer.putShort(left.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            outputBuffer.putShort(right.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }

        inputBuffer.position(inputBuffer.position() + frameCount * bytesPerFrame)
        outputBuffer.flip()
    }

    override fun getOutput(): ByteBuffer {
        val pending = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return pending
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun queueEndOfStream() { inputEnded = true }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
    }

    /**
     * Applies the Dolby downmix matrix for 5.1 surround (FL, FR, FC, LFE, SL, SR).
     *
     * @return A [Pair] of (left, right) float samples ready for stereo output.
     */
    private fun downmix51(s: ShortArray): Pair<Float, Float> {
        val left  = s[FL_51]  + COEFF_SURROUND * s[FC_51]  + COEFF_SURROUND * s[SL_51]  + COEFF_LFE * s[LFE_51]
        val right = s[FR_51]  + COEFF_SURROUND * s[FC_51]  + COEFF_SURROUND * s[SR_51]  + COEFF_LFE * s[LFE_51]
        return Pair(left, right)
    }

    /**
     * Applies the Dolby downmix matrix for 7.1 surround (FL, FR, FC, LFE, SL, SR, SBL, SBR).
     *
     * @return A [Pair] of (left, right) float samples ready for stereo output.
     */
    private fun downmix71(s: ShortArray): Pair<Float, Float> {
        val left  = s[FL_71]  + COEFF_SURROUND * s[FC_71]  + COEFF_SURROUND * s[SL_71]  + COEFF_SURROUND * s[SBL_71] + COEFF_LFE * s[LFE_71]
        val right = s[FR_71]  + COEFF_SURROUND * s[FC_71]  + COEFF_SURROUND * s[SR_71]  + COEFF_SURROUND * s[SBR_71] + COEFF_LFE * s[LFE_71]
        return Pair(left, right)
    }
}