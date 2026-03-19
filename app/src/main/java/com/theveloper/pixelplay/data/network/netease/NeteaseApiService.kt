package com.theveloper.pixelplay.data.network.netease

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct Netease Cloud Music API client.
 *
 * Modeled after NeriPlayer's NeteaseClient — uses OkHttp with cookie management,
 * supports all 4 crypto modes, and calls music.163.com/interface.music.163.com directly.
 */
@Singleton
class NeteaseApiService @Inject constructor() {

    companion object {
        private const val TAG = "NeteaseApi"
    }

    private val cookieStore: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

    @Volatile
    private var persistedCookies: Map<String, String> = emptyMap()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val host = url.host
                val list = cookieStore.getOrPut(host) { mutableListOf() }
                list.removeAll { c -> cookies.any { it.name == c.name } }
                list.addAll(cookies)
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // ─── Cookie Management ─────────────────────────────────────────────

    /** Check if user is logged in (has MUSIC_U cookie) */
    fun hasLogin(): Boolean = !persistedCookies["MUSIC_U"].isNullOrBlank()

    /** Set persisted cookies from saved state and inject into CookieJar */
    fun setPersistedCookies(cookies: Map<String, String>) {
        val m = cookies.toMutableMap()
        m.putIfAbsent("os", "pc")
        m.putIfAbsent("appver", "8.10.35")
        persistedCookies = m.toMap()

        seedCookieJarFromPersisted("music.163.com")
        seedCookieJarFromPersisted("interface.music.163.com")
    }

    /** Get all cookies currently in memory */
    fun getCookies(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        cookieStore.values.forEach { list ->
            list.forEach { cookie -> result[cookie.name] = cookie.value }
        }
        return result
    }

    fun logout() {
        cookieStore.clear()
        persistedCookies = emptyMap()
    }

    private fun seedCookieJarFromPersisted(host: String) {
        val list = cookieStore.getOrPut(host) { mutableListOf() }
        persistedCookies.forEach { (name, value) ->
            val c = Cookie.Builder()
                .name(name).value(value)
                .domain(host).path("/")
                .build()
            list.removeAll { it.name == name }
            list.add(c)
        }
    }

    private fun getCookie(name: String): String? = cookieStore.values
        .asSequence()
        .flatMap { it.asSequence() }
        .firstOrNull { it.name == name }
        ?.value

    private fun buildPersistedCookieHeader(): String? {
        val map = persistedCookies.toMutableMap()
        map.putIfAbsent("os", "pc")
        map.putIfAbsent("appver", "8.10.35")
        if (map.isEmpty()) return null
        return map.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    // ─── Core Request Method ───────────────────────────────────────────

    /** Visit music.163.com homepage to obtain __csrf cookie */
    @Throws(IOException::class)
    fun ensureWeapiSession() {
        request(
            url = "https://music.163.com/",
            params = emptyMap(), mode = CryptoMode.API,
            method = "GET", usePersistedCookies = true
        )
    }

    @Throws(IOException::class)
    fun request(
        url: String,
        params: Map<String, Any>,
        mode: CryptoMode = CryptoMode.WEAPI,
        method: String = "POST",
        usePersistedCookies: Boolean = true
    ): String {
        val requestUrl = url.toHttpUrl()
        Timber.d("$TAG: >>> $method $url [mode=$mode, persistedCookies=${usePersistedCookies}]")
        Timber.d("$TAG: >>> params keys=${params.keys}")
        Timber.d("$TAG: >>> hasLogin=${hasLogin()}, MUSIC_U=${persistedCookies["MUSIC_U"]?.take(20)}...")

        val bodyParams: Map<String, String> = when (mode) {
            CryptoMode.WEAPI -> NeteaseEncryption.weApiEncrypt(params)
            CryptoMode.EAPI -> NeteaseEncryption.eApiEncrypt(requestUrl.encodedPath, params)
            CryptoMode.LINUX -> NeteaseEncryption.linuxApiEncrypt(params)
            CryptoMode.API -> params.mapValues { it.value.toString() }
        }

        var reqUrl = requestUrl
        val builder = Request.Builder()
            .header("Accept", "*/*")
            .header("Accept-Language", "zh-CN,zh-Hans;q=0.9")
            .header("Connection", "keep-alive")
            .header("Referer", "https://music.163.com")
            .header("Host", requestUrl.host)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; PixelPlayer) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")

        if (usePersistedCookies) {
            buildPersistedCookieHeader()?.let { builder.header("Cookie", it) }
        }

        // WEAPI requires csrf_token
        if (mode == CryptoMode.WEAPI) {
            val csrf = persistedCookies["__csrf"] ?: getCookie("__csrf") ?: ""
            reqUrl = requestUrl.newBuilder()
                .setQueryParameter("csrf_token", csrf)
                .build()
        }

        builder.url(reqUrl)

        when (method.uppercase(Locale.getDefault())) {
            "POST" -> {
                val formBodyBuilder = FormBody.Builder(StandardCharsets.UTF_8)
                bodyParams.forEach { (k, v) -> formBodyBuilder.add(k, v) }
                builder.post(formBodyBuilder.build())
            }
            "GET" -> {
                val urlBuilder = reqUrl.newBuilder()
                bodyParams.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
                builder.url(urlBuilder.build())
            }
            else -> throw IllegalArgumentException("Unsupported method: $method")
        }

        try {
            okHttpClient.newCall(builder.build()).execute().use { resp ->
                val code = resp.code
                Timber.d("$TAG: <<< HTTP $code for $url")
                val bytes = resp.body?.bytes() ?: throw IOException("Empty response body")
                val body = String(bytes, StandardCharsets.UTF_8)
                Timber.d("$TAG: <<< body[${body.length}]: ${body.take(500)}")
                return body
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: !!! FAILED $method $url")
            throw e
        }
    }

    // ─── Convenience Methods ───────────────────────────────────────────

    fun callWeApi(path: String, params: Map<String, Any>, usePersistedCookies: Boolean = true): String {
        val p = if (path.startsWith("/")) path else "/$path"
        return request("https://music.163.com/weapi$p", params, CryptoMode.WEAPI, "POST", usePersistedCookies)
    }

    fun callEApi(path: String, params: Map<String, Any>, usePersistedCookies: Boolean = true): String {
        val p = if (path.startsWith("/")) path else "/$path"
        return request("https://interface.music.163.com/eapi$p", params, CryptoMode.EAPI, "POST", usePersistedCookies)
    }

    // ─── Authentication ────────────────────────────────────────────────

    fun sendCaptcha(phone: String, ctcode: Int = 86): String {
        val params = mapOf("cellphone" to phone, "ctcode" to ctcode.toString())
        return request("https://interface.music.163.com/weapi/sms/captcha/sent", params, CryptoMode.WEAPI, "POST", usePersistedCookies = false)
    }

    fun loginByCaptcha(phone: String, captcha: String, ctcode: Int = 86): String {
        val params = mutableMapOf<String, Any>(
            "phone" to phone,
            "countrycode" to ctcode.toString(),
            "remember" to "true",
            "type" to "1",
            "captcha" to captcha
        )
        return callEApi("/w/login/cellphone", params, usePersistedCookies = false)
    }

    // ─── User Info ─────────────────────────────────────────────────────

    fun getCurrentUserAccount(): String {
        return callWeApi("/w/nuser/account/get", emptyMap(), usePersistedCookies = true)
    }

    fun getCurrentUserId(): Long {
        val raw = getCurrentUserAccount()
        val root = JSONObject(raw)
        if (root.optInt("code", -1) != 200) {
            throw IllegalStateException("Failed to get user info: $raw")
        }
        return root.optJSONObject("profile")?.optLong("userId")
            ?: throw IllegalStateException("userId not found")
    }

    // ─── Content ───────────────────────────────────────────────────────

    fun getUserPlaylists(userId: Long, offset: Int = 0, limit: Int = 50): String {
        val params = mutableMapOf<String, Any>(
            "uid" to userId.toString(),
            "offset" to offset.toString(),
            "limit" to limit.toString(),
            "includeVideo" to "true"
        )
        return request("https://music.163.com/weapi/user/playlist", params, CryptoMode.WEAPI, "POST", usePersistedCookies = true)
    }

    fun getPlaylistDetail(playlistId: Long): String {
        val params = mutableMapOf<String, Any>(
            "id" to playlistId.toString(),
            "n" to "100000",
            "s" to "8"
        )
        return request("https://music.163.com/api/v6/playlist/detail", params, CryptoMode.API, "POST", usePersistedCookies = true)
    }

    /**
     * Fetch full track metadata for a list of song IDs.
     * This is used to complete playlist sync when playlist/detail embeds only a subset of tracks.
     */
    fun getSongDetails(songIds: List<Long>): String {
        if (songIds.isEmpty()) {
            return """{"code":200,"songs":[]}"""
        }

        val ids = JSONArray()
        val c = JSONArray()
        songIds.forEach { id ->
            ids.put(id)
            c.put(JSONObject().put("id", id))
        }

        val params = mutableMapOf<String, Any>(
            "ids" to ids.toString(),
            "c" to c.toString()
        )
        return request("https://music.163.com/api/v3/song/detail", params, CryptoMode.API, "POST", usePersistedCookies = true)
    }

    // ─── Song Data ─────────────────────────────────────────────────────

    /**
     * Get song download/streaming URL.
     * Uses EAPI encryption (like NeriPlayer).
     * Will retry with session warm-up if needed.
     */
    fun getSongDownloadUrl(songId: Long, level: String = "exhigh"): String {
        fun call(): String {
            val encodeType = if (level == "lossless" || level == "jyeffect") "flac" else "mp3"
            val params = mutableMapOf<String, Any>(
                "ids" to "[$songId]",
                "level" to level,
                "encodeType" to encodeType
            )
            return callEApi("/song/enhance/player/url/v1", params, usePersistedCookies = true)
        }

        var resp = call()
        return try {
            val code = JSONObject(resp).optInt("code", -1)
            if (code == 301 && hasLogin()) {
                try {
                    ensureWeapiSession()
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: session warm-up failed, continuing with original response")
                }
                resp = call()
            }
            resp
        } catch (_: Exception) {
            resp
        }
    }

    // ─── Search ────────────────────────────────────────────────────────

    fun searchSongs(keyword: String, limit: Int = 30, offset: Int = 0): String {
        val params = mutableMapOf<String, Any>(
            "s" to keyword,
            "type" to "1",
            "limit" to limit.toString(),
            "offset" to offset.toString(),
            "total" to "true"
        )
        return request("https://music.163.com/weapi/cloudsearch/get/web", params, CryptoMode.WEAPI, "POST", usePersistedCookies = true)
    }

    // ─── Lyrics ────────────────────────────────────────────────────────

    fun getLyrics(songId: Long): String {
        val params = mutableMapOf<String, Any>(
            "id" to songId.toString(),
            "cp" to "false",
            "lv" to -1,
            "tv" to -1,
            "kv" to -1,
            "rv" to -1,
            "yv" to 1,
            "ytv" to 1,
            "yrv" to 1
        )
        return callEApi("/song/lyric/v1", params, usePersistedCookies = true)
    }
}
