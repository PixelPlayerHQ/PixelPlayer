package com.theveloper.pixelplay.data.image

import android.net.Uri
import coil.ImageLoader
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.theveloper.pixelplay.utils.AlbumArtUtils
import com.theveloper.pixelplay.utils.LocalArtworkUri
import okio.Path.Companion.toPath
import javax.inject.Inject

class LocalArtworkCoilFetcher(
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val songId = LocalArtworkUri.parseSongId(uri.toString()) ?: return null
        val cachedFile = AlbumArtUtils.ensureAlbumArtCachedFile(
            appContext = options.context,
            songId = songId
        ) ?: return null

        return SourceResult(
            source = coil.decode.ImageSource(
                file = cachedFile.absolutePath.toPath(),
                fileSystem = okio.FileSystem.SYSTEM
            ),
            mimeType = null,
            dataSource = coil.decode.DataSource.DISK
        )
    }

    class Factory @Inject constructor() : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (LocalArtworkUri.isLocalArtworkUri(data)) {
                LocalArtworkCoilFetcher(data, options)
            } else {
                null
            }
        }
    }
}
