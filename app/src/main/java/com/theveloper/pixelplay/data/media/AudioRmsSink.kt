package com.theveloper.pixelplay.data.media

import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.sqrt

@UnstableApi
class AudioRmsSink(
    private val onRmsChanged: (Float) -> Unit
) : TeeAudioProcessor.AudioBufferSink {

    private var maxRms = 0f // 用于归一化

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        // 重置状态
        maxRms = 0f
        onRmsChanged(0f)
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!buffer.hasRemaining()) return

        // 假设标准的 16-bit PCM 编码
        val shortBuffer = buffer.asShortBuffer()
        var sumSquares = 0.0
        val sampleCount = shortBuffer.remaining()

        if (sampleCount == 0) return

        while (shortBuffer.hasRemaining()) {
            val sample = shortBuffer.get().toDouble()
            sumSquares += sample * sample
        }

        // 计算均方根 (RMS)
        val rms = sqrt(sumSquares / sampleCount).toFloat()

        // 动态调整最大基准值以适应不同音量的歌曲
        if (rms > maxRms) maxRms = rms

        // 归一化到 0.0f ~ 1.0f 之间
        val normalizedRms = if (maxRms > 0) (rms / maxRms).coerceIn(0f, 1f) else 0f

        // 将数据回调出去
        onRmsChanged(normalizedRms)
    }
}