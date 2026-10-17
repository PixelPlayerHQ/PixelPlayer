package com.theveloper.pixelplay.data.s3

import android.util.Xml
import com.theveloper.pixelplay.data.stream.CloudStreamSecurity
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser

/** One "folder" = an S3 common prefix (e.g. `Music/`). */
data class S3FolderEntry(
    val prefix: String,
    val songCount: Int
)

data class S3ObjectEntry(
    val key: String,
    val sizeBytes: Long,
    val lastModified: String
)

data class S3ListPage(
    val folders: List<S3FolderEntry>,
    val objects: List<S3ObjectEntry>,
    val isTruncated: Boolean,
    val continuationToken: String?
)

@Singleton
class S3ApiService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    /**
     * Lists one level of folders and (optionally all) objects under [prefix].
     * With [delimiter] = "/" returns immediate sub-folders only.
     */
    suspend fun listObjects(
        endpoint: String,
        bucket: String,
        accessKey: String,
        secretKey: String,
        region: String,
        prefix: String = "",
        delimiter: String? = "/",
        continuationToken: String? = null,
        maxKeys: Int = 1000
    ): S3ListPage = withContext(Dispatchers.IO) {
        val params = sortedMapOf("list-type" to "2")
        if (prefix.isNotEmpty()) params["prefix"] = prefix
        if (delimiter != null) params["delimiter"] = delimiter
        params["max-keys"] = maxKeys.toString()
        continuationToken?.let { params["continuation-token"] = it }

        val request = S3SigV4.signedHeaders(
            builder = Request.Builder(),
            endpoint = endpoint,
            bucket = bucket,
            query = params,
            accessKey = accessKey,
            secretKey = secretKey,
            region = region
        ).build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("S3 list failed (${response.code}): $body")
            }
            parseListBucketResult(body)
        }
    }

    /** Lists every object under [prefix], following continuation tokens. */
    suspend fun listAllObjects(
        endpoint: String,
        bucket: String,
        accessKey: String,
        secretKey: String,
        region: String,
        prefix: String,
        maxKeys: Int = 1000
    ): List<S3ObjectEntry> {
        val all = mutableListOf<S3ObjectEntry>()
        var token: String? = null
        do {
            val page = listObjects(
                endpoint, bucket, accessKey, secretKey, region,
                prefix = prefix, delimiter = null,
                continuationToken = token, maxKeys = maxKeys
            )
            all += page.objects
            token = if (page.isTruncated) page.continuationToken else null
        } while (token != null)
        return all
    }

    /** Cheap connectivity + credential check: a single-key listing. */
    suspend fun ping(
        endpoint: String,
        bucket: String,
        accessKey: String,
        secretKey: String,
        region: String
    ) {
        listObjects(endpoint, bucket, accessKey, secretKey, region, maxKeys = 1)
    }

    suspend fun presignGetUrl(
        endpoint: String,
        bucket: String,
        objectKey: String,
        accessKey: String,
        secretKey: String,
        region: String,
        expiresInSeconds: Long = 3600
    ): String {
        CloudStreamSecurity.validateS3ObjectKey(objectKey)
        return S3SigV4.presignGetUrl(
            endpoint, bucket, objectKey, accessKey, secretKey, region, expiresInSeconds
        )
    }

    internal fun parseListBucketResult(xml: String): S3ListPage {
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        val folders = mutableListOf<S3FolderEntry>()
        val objects = mutableListOf<S3ObjectEntry>()
        var isTruncated = false
        var continuationToken: String? = null

        var insideContents = false
        var insideCommonPrefixes = false
        var currentKey: String? = null
        var currentSize = 0L

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "Contents" -> {
                        insideContents = true
                        currentKey = null
                        currentSize = 0L
                    }
                    "CommonPrefixes" -> insideCommonPrefixes = true
                    "Key" -> if (insideContents) currentKey = parser.nextText()
                    "Size" -> if (insideContents) currentSize = parser.nextText()?.toLongOrNull() ?: 0L
                    "Prefix" -> if (insideCommonPrefixes) folders.add(
                        S3FolderEntry(prefix = parser.nextText().orEmpty(), songCount = 0)
                    )
                    "IsTruncated" -> isTruncated = parser.nextText().equals("true", ignoreCase = true)
                    "NextContinuationToken" -> continuationToken = parser.nextText()
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "Contents" -> {
                        insideContents = false
                        currentKey?.takeIf { it.isNotEmpty() }?.let {
                            objects.add(S3ObjectEntry(key = it, sizeBytes = currentSize, lastModified = ""))
                        }
                    }
                    "CommonPrefixes" -> insideCommonPrefixes = false
                }
            }
            eventType = parser.next()
        }
        return S3ListPage(folders, objects, isTruncated, continuationToken)
    }
}
