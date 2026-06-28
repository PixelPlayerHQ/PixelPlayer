package com.theveloper.pixelplay.data.service.cast

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class IsoBmffAudioCodecDetectorTest {

    @Test
    fun detectAudioCodec_returnsAlacSampleEntry() {
        val bytes = mp4WithTracks(audioTrack("alac"))

        val codec = IsoBmffAudioCodecDetector.detectAudioCodec(bytes)

        assertThat(codec).isEqualTo("audio/alac")
    }

    @Test
    fun detectAudioCodec_returnsAacSampleEntry() {
        val bytes = mp4WithTracks(audioTrack("mp4a"))

        val codec = IsoBmffAudioCodecDetector.detectAudioCodec(bytes)

        assertThat(codec).isEqualTo("audio/mp4a-latm")
    }

    @Test
    fun detectAudioCodec_returnsAc4SampleEntry() {
        val bytes = mp4WithTracks(audioTrack("ac-4"))

        val codec = IsoBmffAudioCodecDetector.detectAudioCodec(bytes)

        assertThat(codec).isEqualTo("audio/ac4")
    }

    @Test
    fun detectAudioCodec_skipsVideoTracks() {
        val bytes = mp4WithTracks(
            track(handler = "vide", sampleEntry = "hvc1"),
            audioTrack("mp4a")
        )

        val codec = IsoBmffAudioCodecDetector.detectAudioCodec(bytes)

        assertThat(codec).isEqualTo("audio/mp4a-latm")
    }

    @Test
    fun detectAudioCodec_returnsNullWithoutAudioTrack() {
        val bytes = mp4WithTracks(track(handler = "vide", sampleEntry = "hvc1"))

        val codec = IsoBmffAudioCodecDetector.detectAudioCodec(bytes)

        assertThat(codec).isNull()
    }

    private fun mp4WithTracks(vararg tracks: ByteArray): ByteArray {
        return box("ftyp", "M4A ".encodeToByteArray()) +
            box("moov", tracks.reduce(ByteArray::plus))
    }

    private fun audioTrack(sampleEntry: String): ByteArray = track(
        handler = "soun",
        sampleEntry = sampleEntry
    )

    private fun track(handler: String, sampleEntry: String): ByteArray {
        return box(
            "trak",
            box(
                "mdia",
                hdlr(handler) +
                    box(
                        "minf",
                        box(
                            "stbl",
                            stsd(sampleEntry)
                        )
                    )
            )
        )
    }

    private fun hdlr(handler: String): ByteArray {
        return box(
            "hdlr",
            byteArrayOf(0, 0, 0, 0) +
                byteArrayOf(0, 0, 0, 0) +
                handler.encodeToByteArray()
        )
    }

    private fun stsd(sampleEntry: String): ByteArray {
        return box(
            "stsd",
            byteArrayOf(0, 0, 0, 0) +
                intBytes(1) +
                box(sampleEntry, ByteArray(8))
        )
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        require(type.length == 4)
        val size = 8 + payload.size
        return intBytes(size) + type.encodeToByteArray() + payload
    }

    private fun intBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte()
        )
    }
}
