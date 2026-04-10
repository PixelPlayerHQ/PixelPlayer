package com.theveloper.pixelplay.data.jellyfin

import android.net.Uri
import com.theveloper.pixelplay.data.stream.CloudStreamProxy
import com.theveloper.pixelplay.data.stream.CloudStreamSecurity
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinStreamProxy @Inject constructor(
    private val repository: JellyfinRepository,
    okHttpClient: OkHttpClient
) : CloudStreamProxy<String>(okHttpClient) {

    override val allowedHostSuffixes: Set<String>
        get() = repository.serverUrl?.toHttpUrlOrNull()?.host?.let { setOf(it) } ?: emptySet()

    override val cacheExpirationMs = 30L * 60 * 1000

    override val proxyTag = "JellyfinStreamProxy"
    override val routePath = "/jellyfin/{itemId}"
    override val routeParamName = "itemId"
    override val uriScheme = "jellyfin"
    override val routePrefix = "/jellyfin"

    override fun parseRouteParam(value: String): String? =
        value.takeIf { it.isNotBlank() }

    override fun validateId(id: String): Boolean =
        CloudStreamSecurity.validateJellyfinItemId(id)

    override fun formatIdForUrl(id: String): String = id

    override suspend fun resolveStreamUrl(id: String): String? {
        return try {
            repository.getStreamUrl(id)
        } catch (e: Exception) {
            Timber.w(e, "JellyfinStreamProxy: Failed to resolve stream URL for item $id")
            null
        }
    }

    override fun extractIdFromUri(uri: Uri): String? =
        uri.host ?: uri.path?.removePrefix("/")

    fun resolveJellyfinUri(uriString: String): String? = resolveUri(uriString)

    suspend fun warmUpStreamUrl(uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "jellyfin") return
        val itemId = uri.host ?: uri.path?.removePrefix("/") ?: return
        if (!CloudStreamSecurity.validateJellyfinItemId(itemId)) return
        try {
            getOrFetchStreamUrl(itemId)
        } catch (e: Exception) {
            Timber.w(e, "warmUpStreamUrl failed for $itemId")
        }
    }
}
