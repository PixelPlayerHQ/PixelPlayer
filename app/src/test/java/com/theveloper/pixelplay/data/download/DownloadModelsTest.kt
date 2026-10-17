package com.theveloper.pixelplay.data.download

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadModelsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `download request serializes required fields`() {
        val req = DownloadRequest(
            service = "spotify",
            trackName = "Song",
            artistName = "Artist",
            albumName = "Album",
            coverUrl = "https://example/cover.jpg",
            outputDir = "/tmp/work",
            quality = "flac",
            embedMetadata = true,
            itemId = "item-1",
            durationMs = 200_000,
            trackNumber = 3,
            discNumber = 1,
            totalTracks = 12,
            totalDiscs = 1,
            releaseDate = "2020-01-01",
            isrc = "ISRC123",
        )
        val tree = json.parseToJsonElement(json.encodeToString(req)).jsonObject
        assertTrue(tree["use_extensions"]!!.jsonPrimitive.boolean)  // always true, spec-mandated
        assertEquals("item-1", tree["item_id"]!!.jsonPrimitive.content)
        assertEquals("flac", tree["quality"]!!.jsonPrimitive.content)
        assertEquals("/tmp/work", tree["output_dir"]!!.jsonPrimitive.content)
        assertFalse(tree.toString().contains("output_fd"))
    }

    @Test
    fun `download response parses file_path and actual_extension`() {
        val resp = json.decodeFromString<DownloadResponse>(
            """{"success":true,"file_path":"/tmp/x.flac","actual_extension":"flac",
                "service":"spotify","title":"Song","artist":"Artist"}"""
        )
        assertTrue(resp.success)
        assertEquals("/tmp/x.flac", resp.filePath)
        assertEquals("flac", resp.actualExtension)
    }

    @Test
    fun `download response parses error`() {
        val resp = json.decodeFromString<DownloadResponse>(
            """{"success":false,"error":"Verification required before download","error_type":"verification_required","service":"qobuz"}"""
        )
        assertFalse(resp.success)
        assertEquals("verification_required", resp.errorType)
    }

    @Test
    fun `progress delta parses items and removed`() {
        val delta = json.decodeFromString<ProgressDelta>(
            """{"seq":7,"items":{"i1":{"item_id":"i1","bytes_total":100,"bytes_received":50,
                "progress":0.5,"speed_mbps":1.2,"is_downloading":true,"status":"downloading","stage":"audio"}},
                "removed":["i0"]}"""
        )
        assertEquals(7L, delta.seq)
        assertEquals(0.5f, delta.items["i1"]!!.progress)
        assertEquals(listOf("i0"), delta.removed)
    }

    @Test
    fun `search results parse`() {
        val tracks = json.decodeFromString<List<ExtTrack>>(
            """[{"id":"t1","name":"Song","artists":"Artist","album_name":"Album",
                "duration_ms":180000,"cover_url":"https://c/1.jpg","provider_id":"spotify-metadata",
                "isrc":"X","track_number":2,"disc_number":1,"total_tracks":10,"total_discs":1,
                "release_date":"2019-05-01","spotify_id":"sp1"}]"""
        )
        assertEquals(1, tracks.size)
        assertEquals("Song", tracks[0].name)
        assertEquals("sp1", tracks[0].spotifyId)
    }

    @Test
    fun `installed extensions parse`() {
        val exts = json.decodeFromString<List<InstalledExtension>>(
            // GetInstalledExtensions returns flat ExtensionInfo objects (no nested manifest).
            """[{"id":"qobuz-ext","name":"qobuz","display_name":"Qobuz","version":"1.2.0",
                "enabled":true,"status":"ok","types":["download_provider"],
                "search_behavior":{"enabled":true,"filters":[{"id":"track"},{"id":"album"}]}}]"""
        )
        assertEquals("qobuz-ext", exts[0].id)
        assertEquals("Qobuz", exts[0].displayName)
        assertEquals("1.2.0", exts[0].version)
        assertTrue(exts[0].isDownloadProvider)
        assertTrue(exts[0].hasCustomSearch)
        assertEquals(listOf("track", "album"), exts[0].searchBehavior!!.filters.map { it.id })
    }
}
