package com.theveloper.pixelplay.data.service.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * A wrapper that intercepts and downsamples audio whose
 * sample rate exceeds (192 kHz) before it reaches [DefaultAudioSink].
 */
@OptIn(UnstableApi::class)
class DownsamplingAudioSink(private val inner: DefaultAudioSink) : AudioSink by inner {

    companion object {
        private const val MAX_RATE = 192_000
        private const val TAG = "DownsamplingAudioSink"
    }

    private val resampler = HighResSamplerAudioProcessor()
    /**
     * Holds resampled output that couldn't be sent to [inner] on the previous
     * [handleBuffer] call (inner buffer was full). Drained first on the next call.
     */
    private var pendingOutput: ByteBuffer = AudioProcessor.EMPTY_BUFFER

    // -------------------------------------------------------------------------
    // supportsFormat() — advertise capability for high-rate PCM by checking
    // at the downsampled rate, so FfmpegAudioRenderer returns FORMAT_HANDLED.
    //
    // Background: DefaultAudioSink.supportsFormat() calls
    // AudioTrack.getMinBufferSize(sampleRate, …) to validate the rate. For
    // rates > 192 kHz that call returns ERROR_BAD_VALUE → false (unsupported).
    // -------------------------------------------------------------------------

    override fun supportsFormat(format: Format): Boolean {
        val rate = format.sampleRate
        if (rate != Format.NO_VALUE && rate > MAX_RATE) {
            val factor = (rate + MAX_RATE - 1) / MAX_RATE
            val cappedFormat = format.buildUpon().setSampleRate(rate / factor).build()
            return inner.supportsFormat(cappedFormat)
        }
        return inner.supportsFormat(format)
    }

    // -------------------------------------------------------------------------
    // configure() — intercept before inner sees the format
    // -------------------------------------------------------------------------

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?
    ) {
        resampler.reset()
        pendingOutput = AudioProcessor.EMPTY_BUFFER

        val rate = inputFormat.sampleRate
        val encoding = inputFormat.pcmEncoding

        val needsDownsample = rate != Format.NO_VALUE
                && rate > MAX_RATE
                && encoding != Format.NO_VALUE
                && encoding != C.ENCODING_INVALID

        if (needsDownsample) {
            val outputFmt: AudioFormat = resampler.configure(
                AudioFormat(rate, inputFormat.channelCount, encoding)
            )

            if (resampler.isActive) {
                // Tell the inner sink the capped rate so AudioTrack is created correctly
                val cappedFormat = inputFormat.buildUpon()
                    .setSampleRate(outputFmt.sampleRate)
                    .setPcmEncoding(outputFmt.encoding) // always PCM_FLOAT out of the resampler
                    .build()

                Timber.tag(TAG).i(
                    "configure: %d Hz → %d Hz (factor %d), encoding %d → %d, %d ch",
                    rate, outputFmt.sampleRate,
                    rate / outputFmt.sampleRate,
                    encoding, outputFmt.encoding,
                    inputFormat.channelCount
                )

                inner.configure(cappedFormat, specifiedBufferSize, outputChannels)
                return
            }
        }

        inner.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    // -------------------------------------------------------------------------
    // handleBuffer() — resample before passing to inner
    // -------------------------------------------------------------------------

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        if (!resampler.isActive) {
            return inner.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        // 1. Try to drain any output that the inner sink couldn't accept previously.
        if (pendingOutput.hasRemaining()) {
            val sent = inner.handleBuffer(pendingOutput, presentationTimeUs, encodedAccessUnitCount)
            if (!sent) return false // inner still full; caller must retry
            pendingOutput = AudioProcessor.EMPTY_BUFFER
        }

        // 2. Resample the new input.
        if (!buffer.hasRemaining()) return true
        resampler.queueInput(buffer)
        val output = resampler.getOutput()
        if (!output.hasRemaining()) return true

        // 3. Forward resampled data to the inner sink.
        val sent = inner.handleBuffer(output, presentationTimeUs, encodedAccessUnitCount)
        if (!sent) {
            // Inner is full. Save output; signal caller that input *was* consumed so
            // ExoPlayer advances to the next buffer. Pending output will be drained above.
            pendingOutput = output
        }
        return true // input buffer was fully consumed by the resampler
    }

    // -------------------------------------------------------------------------
    // Lifecycle — clear resampler state on flush/reset
    // -------------------------------------------------------------------------

    override fun flush() {
        resampler.flush()
        pendingOutput = AudioProcessor.EMPTY_BUFFER
        inner.flush()
    }

    override fun reset() {
        resampler.reset()
        pendingOutput = AudioProcessor.EMPTY_BUFFER
        inner.reset()
    }
}
