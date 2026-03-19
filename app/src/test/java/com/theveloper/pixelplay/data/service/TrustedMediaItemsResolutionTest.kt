package com.theveloper.pixelplay.data.service

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class TrustedMediaItemsResolutionTest {

    @Test
    fun `unresolved caller item stays in queue but is excluded from artwork grants`() {
        val attackerSuppliedItem = mediaItem(
            mediaId = "missing-id",
            artworkUri = "content://com.theveloper.pixelplay.provider/files/private/token.txt"
        )
        val trustedItem = mediaItem(
            mediaId = "known-id",
            artworkUri = "content://com.theveloper.pixelplay.provider/cache/album.png"
        )

        val resolution = resolveMediaItemsWithTrustedArtworkGrants(
            requestedItems = listOf(attackerSuppliedItem, trustedItem)
        ) { mediaId ->
            if (mediaId == trustedItem.mediaId) trustedItem else null
        }

        assertSame(attackerSuppliedItem, resolution.mediaItems[0])
        assertSame(trustedItem, resolution.mediaItems[1])
        assertEquals(listOf(trustedItem), resolution.trustedArtworkGrantItems)
    }

    @Test
    fun `trusted artwork grants preserve the order of resolved server items`() {
        val requestedFirst = mediaItem("song-1")
        val requestedSecond = mediaItem("missing-id")
        val requestedThird = mediaItem("song-2")
        val trustedFirst = mediaItem("song-1")
        val trustedSecond = mediaItem("song-2")

        val resolution = resolveMediaItemsWithTrustedArtworkGrants(
            requestedItems = listOf(requestedFirst, requestedSecond, requestedThird)
        ) { mediaId ->
            when (mediaId) {
                trustedFirst.mediaId -> trustedFirst
                trustedSecond.mediaId -> trustedSecond
                else -> null
            }
        }

        assertEquals(
            listOf(trustedFirst, requestedSecond, trustedSecond),
            resolution.mediaItems
        )
        assertEquals(
            listOf(trustedFirst, trustedSecond),
            resolution.trustedArtworkGrantItems
        )
    }

    private fun mediaItem(mediaId: String, artworkUri: String? = null): MediaItem {
        val metadata = MediaMetadata.Builder().apply {
            artworkUri?.let { setArtworkUri(Uri.parse(it)) }
        }.build()

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            .build()
    }
}
