package com.theveloper.pixelplay.soundcloud

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Isolated OkHttp [Downloader] for NewPipe Extractor.
 *
 * Must not reuse the app OkHttpClient: AppModule forces a PixelPlayer User-Agent
 * that breaks SoundCloud api-v2 / media URLs.
 */
internal class SoundCloudDownloader : Downloader() {

    @Volatile
    var oauthToken: String? = null

    @Volatile
    var cookieHeader: String? = null

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val bodyBytes = request.dataToSend()
        val requestBody = bodyBytes?.toRequestBody(null)

        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), requestBody)
            .url(request.url())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Origin", "https://soundcloud.com")
            .header("Referer", "https://soundcloud.com/")

        oauthToken?.trim()?.takeIf { it.isNotEmpty() }?.let { token ->
            builder.header("Authorization", "OAuth $token")
        }
        cookieHeader?.trim()?.takeIf { it.isNotEmpty() }?.let { cookies ->
            builder.header("Cookie", cookies)
        }

        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { value -> builder.addHeader(name, value) }
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw IOException("SoundCloud rate limited (HTTP 429). Wait a moment and try again.")
            }
            val responseBody = response.body.string()
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBody,
                response.request.url.toString(),
            )
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
