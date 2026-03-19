package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HiResSampleRateCapAudioProcessorTest {

    @Test
    fun configure_keepsSupportedSampleRatesUntouched() {
        val processor = HiResSampleRateCapAudioProcessor()
        val inputFormat = AudioFormat(192_000, 2, C.ENCODING_PCM_16BIT)

        val outputFormat = processor.configure(inputFormat)

        assertThat(outputFormat).isEqualTo(inputFormat)
        assertThat(processor.isActive()).isFalse()
    }

    @Test
    fun queueInput_downsamples384KhzStereoTo192Khz() {
        val processor = HiResSampleRateCapAudioProcessor()
        val outputFormat = processor.configure(AudioFormat(384_000, 2, C.ENCODING_PCM_16BIT))

        processor.queueInput(
            shortBufferOf(
                1_000, -1_000,
                3_000, 1_000,
                5_000, -3_000,
                7_000, -1_000
            )
        )

        assertThat(processor.isActive()).isTrue()
        assertThat(outputFormat.sampleRate).isEqualTo(192_000)
        assertThat(outputFormat.channelCount).isEqualTo(2)
        assertThat(readShorts(processor.getOutput()))
            .containsExactly(2_000, 0, 6_000, -2_000)
            .inOrder()
    }

    @Test
    fun queueInput_carriesPartialFramesAcrossCalls() {
        val processor = HiResSampleRateCapAudioProcessor()
        processor.configure(AudioFormat(384_000, 2, C.ENCODING_PCM_16BIT))

        processor.queueInput(shortBufferOf(100, 200))
        assertThat(processor.getOutput().remaining()).isEqualTo(0)

        processor.queueInput(shortBufferOf(300, 400))

        assertThat(readShorts(processor.getOutput()))
            .containsExactly(200, 300)
            .inOrder()
    }

    private fun shortBufferOf(vararg samples: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        for (sample in samples) {
            buffer.putShort(sample.toShort())
        }
        buffer.flip()
        return buffer
    }

    private fun readShorts(buffer: ByteBuffer): List<Int> {
        val duplicate = buffer.duplicate().order(ByteOrder.nativeOrder())
        val samples = mutableListOf<Int>()
        while (duplicate.remaining() >= Short.SIZE_BYTES) {
            samples += duplicate.short.toInt()
        }
        return samples
    }
}
