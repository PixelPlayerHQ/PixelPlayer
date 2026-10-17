package com.theveloper.pixelplay.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Guards the canonical artist-identity rule.
 *
 * Regression context: SyncWorker keyed its existing-artist map on the raw
 * `name` while SongMetadataEditor keyed on `trim().lowercase()`. The two
 * disagreed, so a resync of a library whose tags differ in case from the stored
 * rows minted a new artist row every scan and the artist list grew without
 * bound. These tests pin the normalisation that both writers now share.
 */
class ArtistNameKeyTest {

    @Test
    fun `case variants resolve to one key`() {
        assertEquals(artistNameKey("Beyoncé"), artistNameKey("beyoncé"))
        assertEquals(artistNameKey("BEYONCÉ"), artistNameKey("beyoncé"))
    }

    @Test
    fun `surrounding and internal whitespace is ignored`() {
        assertEquals(artistNameKey("Radiohead"), artistNameKey("  Radiohead  "))
        assertEquals(artistNameKey("Radiohead"), artistNameKey("Radiohead"))
        assertEquals(artistNameKey("AC DC"), artistNameKey("AC   DC"))
        assertEquals(artistNameKey("AC DC"), artistNameKey("\tAC \n DC \r"))
    }

    @Test
    fun `tabs and newlines are treated as whitespace not as name content`() {
        assertEquals(artistNameKey("Sigur Rós"), artistNameKey("Sigur\tRós"))
        assertEquals(artistNameKey("Sigur Rós"), artistNameKey("Sigur\nRós"))
    }

    @Test
    fun `key is already lowercase and trimmed`() {
        assertEquals("radiohead", artistNameKey("  RadioHead  "))
    }

    @Test
    fun `blank names collapse to the empty key`() {
        assertEquals("", artistNameKey(""))
        assertEquals("", artistNameKey("   "))
        assertEquals("", artistNameKey("\t\n"))
    }

    @Test
    fun `distinct artists keep distinct keys`() {
        assertNotEquals(artistNameKey("The Beatles"), artistNameKey("Beatles"))
        assertNotEquals(artistNameKey("Radiohead"), artistNameKey("Radiohead Live"))
    }

    @Test
    fun `artist names that contain delimiter characters are not collapsed`() {
        // These must remain separate artists: the app's default delimiters do not
        // include "&" or "/" precisely so names like these survive.
        assertNotEquals(artistNameKey("W&W"), artistNameKey("WandW"))
        assertNotEquals(artistNameKey("AC/DC"), artistNameKey("ACDC"))
        assertNotEquals(artistNameKey("Black Country, New Road"), artistNameKey("Black Country New Road"))
    }

    @Test
    fun `the same key is produced regardless of which surface normalises first`() {
        // The bug only manifested because two call sites normalised differently.
        // Feed both a "stored" and an "incoming" spelling through the same helper
        // and they must agree, which is what keeps a resync from forking a row.
        val stored = "Beyoncé"
        val incoming = "  beyoncé "
        assertEquals(artistNameKey(stored), artistNameKey(incoming))
    }
}