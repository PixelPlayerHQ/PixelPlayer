package com.theveloper.pixelplay.data.netease

import com.theveloper.pixelplay.data.stream.CloudStreamProxy
import com.theveloper.pixelplay.data.stream.CloudStreamSecurity
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local HTTP proxy server for streaming Netease Cloud Music audio.
 *
 * Resolves `netease://{songId}` URIs by fetching temporary streaming URLs
 * from the Netease API and proxying the audio data to ExoPlayer.
 */
@Singleton
class NeteaseStreamProxy @Inject constructor(
    private val repository: NeteaseRepository,
    okHttpClient: OkHttpClient
) : CloudStreamProxy<Long>(okHttpClient) {

    override val allowedHostSuffixes = setOf(
        "music.126.net",
        "music.163.com",
        "126.net",
        "163.com"
    )
    override val cacheExpirationMs = 15L * 60 * 1000
    override val proxyTag = "NeteaseStreamProxy"
    override val routePath = "/netease/{songId}"
    override val routeParamName = "songId"
    override val uriScheme = "netease"
    override val routePrefix = "/netease"

    override fun parseRouteParam(value: String): Long? = value.toLongOrNull()

    override fun validateId(id: Long): Boolean =
        CloudStreamSecurity.validateNeteaseSongId(id)

    override fun formatIdForUrl(id: Long): String = id.toString()

    override suspend fun resolveStreamUrl(id: Long): String? =
        repository.getSongUrl(id).getOrNull()

    fun resolveNeteaseUri(uriString: String): String? = resolveUri(uriString)
}
