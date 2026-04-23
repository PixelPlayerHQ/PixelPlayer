package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SurroundDownmixProcessorTest {

    @Test
    fun queueInput_downmixes51Pcm16BitToStereo() {
        val processor = SurroundDownmixProcessor()
        val outputFormat = processor.configure(AudioFormat(48_000, 6, C.ENCODING_PCM_16BIT))

        processor.queueInput(
            shortBufferOf(
                1_000, 2_000, 3_000, 4_000, 5_000, 6_000
            )
        )

        assertThat(processor.isActive()).isTrue()
        assertThat(outputFormat.channelCount).isEqualTo(2)
        assertThat(readShorts(processor.getOutput()))
            .containsExactly(9_484, 11_191)
            .inOrder()
    }

    @Test
    fun queueInput_downmixes71FloatToStereo() {
        val processor = SurroundDownmixProcessor()
        val outputFormat = processor.configure(AudioFormat(48_000, 8, C.ENCODING_PCM_FLOAT))

        processor.queueInput(
            floatBufferOf(
                0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f
            )
        )

        assertThat(processor.isActive()).isTrue()
        assertThat(outputFormat.channelCount).isEqualTo(2)

        val output = readFloats(processor.getOutput())
        assertThat(output).hasSize(2)
        assertThat(output[0]).isWithin(1e-6f).of(1f.coerceAtMost(0.1f + 0.707f * 0.3f + 0.707f * 0.5f + 0.707f * 0.7f + 0.707f * 0.4f))
        assertThat(output[1]).isWithin(1e-6f).of(1f.coerceAtMost(0.2f + 0.707f * 0.3f + 0.707f * 0.6f + 0.707f * 0.8f + 0.707f * 0.4f))
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

    private fun floatBufferOf(vararg samples: Float): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(samples.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        for (sample in samples) {
            buffer.putFloat(sample)
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

    private fun readFloats(buffer: ByteBuffer): List<Float> {
        val duplicate = buffer.duplicate().order(ByteOrder.nativeOrder())
        val samples = mutableListOf<Float>()
        while (duplicate.remaining() >= Float.SIZE_BYTES) {
            samples += duplicate.float
        }
        return samples
    }
}
