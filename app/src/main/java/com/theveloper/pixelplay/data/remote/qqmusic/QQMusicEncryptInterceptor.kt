package com.theveloper.pixelplay.data.remote.qqmusic

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import timber.log.Timber

/**
 * OkHttp Interceptor for QQ Music `musics.fcg` encrypted API.
 * Automatically handles:
 * 1. Signature generation (sign)
 * 2. Body encryption (AES-128-GCM -> Base64)
 * 3. Response decryption (XOR)
 */
class QQMusicEncryptInterceptor(
    private val signGenerator: QQSignGenerator
) : Interceptor {

    private val mediaType = "text/plain".toMediaType()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Only intercept requests to musics.fcg or those requiring encryption
        if (!originalRequest.url.toString().contains("musics.fcg")) {
            return chain.proceed(originalRequest)
        }

        Timber.d("QQMusicEncryptInterceptor: Intercepting request to ${originalRequest.url}")

        // 1. Get original JSON body
        val requestBuffer = Buffer()
        originalRequest.body?.writeTo(requestBuffer)
        val plaintextJson = requestBuffer.readUtf8()
        Timber.d("QQMusicEncryptInterceptor: Plain request json: $plaintextJson")

        // 2. Generate sign using the shared WebView-based signer
        val sign = signGenerator.generateSign(plaintextJson) ?: ""
        Timber.d("QQMusicEncryptInterceptor: Generated sign: $sign")

        // 3. Encrypt body to Base64 using AES-GCM
        val encryptedBase64Body = signGenerator.encryptRequestWithVm(plaintextJson)
            ?: QQMusicSecurity.encryptRequest(plaintextJson)

        // 4. Rebuild request with 'ag-1' encoding and 'sign' parameter
        val newUrl = originalRequest.url.newBuilder()
            .addQueryParameter("encoding", "ag-1")
            .addQueryParameter("sign", sign)
            .build()

        val encryptedRequest = originalRequest.newBuilder()
            .url(newUrl)
            .post(encryptedBase64Body.toRequestBody(mediaType))
            .header("accept", "application/octet-stream")
            .header("content-type", "text/plain")
            .build()

        // 5. Execute request and intercept response
        val response = chain.proceed(encryptedRequest)
        Timber.d("QQMusicEncryptInterceptor: Response code: ${response.code}")
        
        if (response.isSuccessful) {
            val responseBytes = response.body?.bytes() ?: return response
            Timber.d("QQMusicEncryptInterceptor: Response body size: ${responseBytes.size}")
            
            // 6. Decrypt response using vm_new.js (__cgiDecrypt) first, then fallback to XOR.
            val vmDecrypted = signGenerator.decryptResponseWithVm(responseBytes)
            if (vmDecrypted == null) {
                Timber.w("QQMusicEncryptInterceptor: vm_new decrypt returned null, fallback to XOR")
            }
            val decryptedJson = vmDecrypted ?: QQMusicSecurity.decryptResponse(responseBytes)
            Timber.d("QQMusicEncryptInterceptor: Decrypted JSON length: ${decryptedJson.length}")
            Timber.d("QQMusicEncryptInterceptor: Decrypted JSON content: $decryptedJson")
            runCatching {
                val root = org.json.JSONObject(decryptedJson)
                val req0Code = root.optJSONObject("req_0")?.optInt("code")
                val req0SubCode = root.optJSONObject("req_0")?.optInt("subcode")
                Timber.d("QQMusicEncryptInterceptor: Parsed req_0 code=$req0Code, subcode=$req0SubCode")
            }
            
            // 7. Return decrypted response as JSON
            return response.newBuilder()
                .body(decryptedJson.toResponseBody("application/json".toMediaType()))
                .build()
        }

        return response
    }
}
