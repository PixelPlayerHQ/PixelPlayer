package com.theveloper.pixelplay.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioMetaUtilsTest {

    @Test
    fun mimeTypeToFormat_mapsM4aVariants() {
        assertEquals("m4a", AudioMetaUtils.mimeTypeToFormat("audio/mp4"))
        assertEquals("m4a", AudioMetaUtils.mimeTypeToFormat("audio/m4a"))
        assertEquals("m4a", AudioMetaUtils.mimeTypeToFormat("audio/x-m4a"))
        assertEquals("m4a", AudioMetaUtils.mimeTypeToFormat("audio/mp4a-latm"))
    }

    @Test
    fun mimeTypeToFormat_mapsSamsungFormats() {
        assertEquals("amr", AudioMetaUtils.mimeTypeToFormat("audio/amr"))
        assertEquals("amr", AudioMetaUtils.mimeTypeToFormat("audio/amr-wb"))
        assertEquals("amr", AudioMetaUtils.mimeTypeToFormat("audio/3gpp"))
        assertEquals("evrc", AudioMetaUtils.mimeTypeToFormat("audio/evrc"))
        assertEquals("evrc", AudioMetaUtils.mimeTypeToFormat("audio/x-evrc"))
        assertEquals("qcelp", AudioMetaUtils.mimeTypeToFormat("audio/qcelp"))
        assertEquals("qcelp", AudioMetaUtils.mimeTypeToFormat("audio/x-qcelp"))
        assertEquals("ima", AudioMetaUtils.mimeTypeToFormat("audio/x-ima-adpcm"))
        assertEquals("ima", AudioMetaUtils.mimeTypeToFormat("audio/ima-adpcm"))
    }

    @Test
    fun mimeTypeToFormat_mapsUniversalFormats() {
        assertEquals("aiff", AudioMetaUtils.mimeTypeToFormat("audio/x-aiff"))
        assertEquals("ac3", AudioMetaUtils.mimeTypeToFormat("audio/ac3"))
        assertEquals("dts", AudioMetaUtils.mimeTypeToFormat("audio/vnd.dts"))
    }
}
