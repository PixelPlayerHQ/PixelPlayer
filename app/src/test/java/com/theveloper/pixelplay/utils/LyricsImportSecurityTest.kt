package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class LyricsImportSecurityTest {

    @Test
    fun validateImportedLyricsFile_acceptsSyncedLrcAndSanitizesControlCharacters() {
        val raw = "\uFEFF[00:01.00]\u202eHello world"

        val result = LyricsImportSecurity.validateImportedLyricsFile(
            fileName = "track.lrc",
            mimeType = "text/plain",
            inputStream = raw.byteInputStream(),
            reportedSizeBytes = raw.toByteArray().size.toLong()
        )

        assertThat(result).isInstanceOf(LyricsImportValidationResult.Valid::class.java)
        val valid = result as LyricsImportValidationResult.Valid
        assertThat(valid.value.sanitizedContent).isEqualTo("[00:01.00]Hello world")
        assertThat(valid.value.parsedLyrics.synced).hasSize(1)
    }

    @Test
    fun validateImportedLyricsFile_rejectsUnsupportedExtensions() {
        val result = LyricsImportSecurity.validateImportedLyricsFile(
            fileName = "track.txt",
            mimeType = "text/plain",
            inputStream = "[00:01.00]Hello".byteInputStream()
        )

        assertThat(result).isEqualTo(
            LyricsImportValidationResult.Invalid(LyricsImportFailureReason.UNSUPPORTED_EXTENSION)
        )
    }

    @Test
    fun validateImportedLyricsFile_rejectsUnsyncedLrcContent() {
        val result = LyricsImportSecurity.validateImportedLyricsFile(
            fileName = "track.lrc",
            mimeType = "text/plain",
            inputStream = "just plain text".byteInputStream()
        )

        assertThat(result).isEqualTo(
            LyricsImportValidationResult.Invalid(LyricsImportFailureReason.INVALID_LYRICS_CONTENT)
        )
    }

    @Test
    fun validateImportedLyricsFile_rejectsOversizedPayloadEvenWithoutReportedSize() {
        val chunk = "[00:01.00]hello world\n"
        val oversized = buildString {
            while (length <= LyricsImportSecurity.MAX_LYRICS_FILE_BYTES) {
                append(chunk)
            }
        }

        val result = LyricsImportSecurity.validateImportedLyricsFile(
            fileName = "track.lrc",
            mimeType = "text/plain",
            inputStream = oversized.byteInputStream(),
            reportedSizeBytes = null
        )

        assertThat(result).isEqualTo(
            LyricsImportValidationResult.Invalid(LyricsImportFailureReason.FILE_TOO_LARGE)
        )
    }

    @Test
    fun validateLocalLyricsFile_rejectsBinaryPayload() {
        val tempFile = File.createTempFile("lyrics-security", ".lrc")
        tempFile.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

        try {
            val result = LyricsImportSecurity.validateLocalLyricsFile(tempFile)

            assertThat(result).isEqualTo(
                LyricsImportValidationResult.Invalid(LyricsImportFailureReason.INVALID_ENCODING)
            )
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun validateImportedLyricsFile_acceptsUtf16BomPayload() {
        val lyrics = "[00:01.00]Hola mundo"
        val payload = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + lyrics.toByteArray(Charsets.UTF_16LE)

        val result = LyricsImportSecurity.validateImportedLyricsFile(
            fileName = "track.lrc",
            mimeType = "application/octet-stream",
            inputStream = payload.inputStream(),
            reportedSizeBytes = payload.size.toLong()
        )

        assertThat(result).isInstanceOf(LyricsImportValidationResult.Valid::class.java)
        val valid = result as LyricsImportValidationResult.Valid
        assertThat(valid.value.sanitizedContent).isEqualTo(lyrics)
        assertThat(valid.value.parsedLyrics.synced).hasSize(1)
    }

    @Test
    fun validateImportedLyricsFile_acceptsAppleTtmlLineByLine() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p begin="00:01.000" end="00:03.000">Hello world</p>
                  <p begin="00:04.000" end="00:06.000">Second line</p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val result = LyricsImportSecurity.validateImportedLyricsFile(
            fileName = "track.ttml",
            mimeType = "application/ttml+xml",
            inputStream = ttml.byteInputStream()
        )

        assertThat(result).isInstanceOf(LyricsImportValidationResult.Valid::class.java)
        val valid = result as LyricsImportValidationResult.Valid
        assertThat(valid.value.sanitizedContent).contains("[00:01.00]Hello world")
        assertThat(valid.value.parsedLyrics.synced).hasSize(2)
    }

    @Test
    fun validateImportedLyricsFile_acceptsAppleTtmlWordByWord() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p begin="00:14.780" end="00:18.750">
                    <span begin="00:14.780" end="00:15.100">When </span>
                    <span begin="00:15.100" end="00:15.300">I </span>
                    <span begin="00:15.300" end="00:15.700">talk</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val result = LyricsImportSecurity.validateImportedLyricsFile(
            fileName = "track.ttml",
            mimeType = "application/xml",
            inputStream = ttml.byteInputStream()
        )

        assertThat(result).isInstanceOf(LyricsImportValidationResult.Valid::class.java)
        val valid = result as LyricsImportValidationResult.Valid
        assertThat(valid.value.parsedLyrics.synced).hasSize(1)
        assertThat(valid.value.parsedLyrics.synced!!.first().line).isEqualTo("When I talk")
        assertThat(valid.value.parsedLyrics.synced!!.first().words).hasSize(3)
    }

    @Test
    fun validateImportedLyricsFile_acceptsAppleTtmlWithXmlDeclarationAndNamespaces() {
        val ttml = """
            <?xml version='1.0' encoding='utf-8'?>
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" itunes:timing="Word" xml:lang="en">
              <head>
                <metadata>
                  <ttm:agent type="person" xml:id="v1">
                    <ttm:name type="full">Chase Atlantic</ttm:name>
                  </ttm:agent>
                </metadata>
              </head>
              <body dur="0:15.000">
                <div begin="7.531" end="12.005" itunes:songPart="Verse">
                  <p begin="7.531" end="12.005" itunes:key="L1" ttm:agent="v1"><span begin="7.531" end="7.782">Yeah,</span> <span begin="9.208" end="9.443">I</span> <span begin="9.443" end="9.675">bet</span></p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val result = LyricsImportSecurity.validateImportedLyricsFile(
            fileName = "track.ttml",
            mimeType = "application/ttml+xml",
            inputStream = ttml.byteInputStream()
        )

        assertThat(result).isInstanceOf(LyricsImportValidationResult.Valid::class.java)
        val valid = result as LyricsImportValidationResult.Valid
        assertThat(valid.value.sanitizedContent).isEqualTo("[00:07.53]<00:07.53>Yeah, <00:09.20>I <00:09.44>bet")
        assertThat(valid.value.parsedLyrics.synced).hasSize(1)
        assertThat(valid.value.parsedLyrics.synced!!.first().line).isEqualTo("Yeah, I bet")
    }

    @Test
    fun validateImportedLyricsFile_rejectsTtmlWithDoctype() {
        val maliciousTtml = """
            <!DOCTYPE tt [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p begin="00:01.000">&xxe;</p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val result = LyricsImportSecurity.validateImportedLyricsFile(
            fileName = "track.ttml",
            mimeType = "application/ttml+xml",
            inputStream = maliciousTtml.byteInputStream()
        )

        assertThat(result).isEqualTo(
            LyricsImportValidationResult.Invalid(LyricsImportFailureReason.INVALID_LYRICS_CONTENT)
        )
    }

    @Test
    fun validateImportedLyricsFile_acceptsTtmlExtensionWithLrcPayload() {
        val disguisedLrc = "[00:01.00]v1: <00:01.00>Hello <00:01.50>world"

        val result = LyricsImportSecurity.validateImportedLyricsFile(
            fileName = "track.ttml",
            mimeType = "text/plain",
            inputStream = disguisedLrc.byteInputStream()
        )

        assertThat(result).isInstanceOf(LyricsImportValidationResult.Valid::class.java)
        val valid = result as LyricsImportValidationResult.Valid
        assertThat(valid.value.parsedLyrics.synced).hasSize(1)
        assertThat(valid.value.parsedLyrics.synced!!.first().line).isEqualTo("v1: Hello world")
    }
}
