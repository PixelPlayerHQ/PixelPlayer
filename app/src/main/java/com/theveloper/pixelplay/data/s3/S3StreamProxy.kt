package com.theveloper.pixelplay.data.s3

import android.net.Uri
import com.theveloper.pixelplay.data.stream.CloudStreamProxy
import com.theveloper.pixelplay.data.stream.CloudStreamSecurity
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local HTTP proxy for S3-compatible object storage streaming.
 *
 * Resolves `s3://{objectKey}` URIs by presigning a GET URL (SigV4, 1 h)
 * and proxying it through the shared [CloudStreamProxy] loopback server.
 * Object keys contain '/', so route params are URL-encoded; Ktor decodes
 * them back into [parseRouteParam].
 */
@Singleton
class S3StreamProxy @Inject constructor(
    private val repository: S3Repository,
    okHttpClient: OkHttpClient
) : CloudStreamProxy<String>(okHttpClient) {

    override val allowedHostSuffixes: Set<String>
        get() = repository.getConnection()?.endpointHost?.let { setOf(it) } ?: emptySet()

    override val cacheExpirationMs = 55L * 60 * 1000 // presign TTL is 1 h
    override val proxyTag = "S3StreamProxy"
    override val routePath = "/s3/{key}"
    override val routeParamName = "key"
    override val uriScheme = "s3"
    override val routePrefix = "/s3"

    override fun parseRouteParam(value: String): String? =
        value.takeIf { it.isNotBlank() }

    override fun validateId(id: String): Boolean =
        CloudStreamSecurity.validateS3ObjectKey(id)

    override fun formatIdForUrl(id: String): String =
        Uri.encode(id, "") // encode everything incl '/' and ' ' (URLEncoder's '+' would break)

    override suspend fun resolveStreamUrl(id: String): String? =
        repository.presignGetUrl(id)

    // "s3://bucket-prefix/path/song.mp3" — Uri splits host/path at the first '/'.
    override fun extractIdFromUri(uri: Uri): String? =
        ((uri.host ?: "") + (uri.path ?: "")).removePrefix("/").takeIf { it.isNotEmpty() }

    fun resolveS3Uri(uriString: String): String? = resolveUri(uriString)
}
