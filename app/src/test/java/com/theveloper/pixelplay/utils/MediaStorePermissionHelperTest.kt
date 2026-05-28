package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaStorePermissionHelperTest {

    @Test
    fun isMediaStoreItemUriString_acceptsSpecificMediaStoreItems() {
        assertThat(
            MediaStorePermissionHelper.isMediaStoreItemUriString(
                "content://media/external/audio/media/9317"
            )
        ).isTrue()
        assertThat(
            MediaStorePermissionHelper.isMediaStoreItemUriString(
                "content://media/external_primary/audio/media/9317"
            )
        ).isTrue()
    }

    @Test
    fun isMediaStoreItemUriString_rejectsCollectionUris() {
        assertThat(
            MediaStorePermissionHelper.isMediaStoreItemUriString(
                "content://media/external/audio/media"
            )
        ).isFalse()
        assertThat(
            MediaStorePermissionHelper.isMediaStoreItemUriString(
                "content://media/external/audio/media/not-a-number"
            )
        ).isFalse()
    }

    @Test
    fun canUseSongIdForMediaStoreRequest_rejectsCloudProviderUris() {
        assertThat(
            MediaStorePermissionHelper.canUseSongIdForMediaStoreRequest("netease://9317")
        ).isFalse()
        assertThat(
            MediaStorePermissionHelper.canUseSongIdForMediaStoreRequest("telegram://123/456")
        ).isFalse()
    }

    @Test
    fun canUseSongIdForMediaStoreRequest_acceptsLocalFallbacks() {
        assertThat(MediaStorePermissionHelper.canUseSongIdForMediaStoreRequest("")).isTrue()
        assertThat(
            MediaStorePermissionHelper.canUseSongIdForMediaStoreRequest(
                "/storage/emulated/0/Music/song.mp3"
            )
        ).isTrue()
        assertThat(
            MediaStorePermissionHelper.canUseSongIdForMediaStoreRequest(
                "content://media/external/audio/media/42"
            )
        ).isTrue()
    }
}
