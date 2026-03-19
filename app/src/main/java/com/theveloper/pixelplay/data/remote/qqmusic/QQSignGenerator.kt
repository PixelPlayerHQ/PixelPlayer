package com.theveloper.pixelplay.data.remote.qqmusic

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Android implementation of QQ Music Signature (sign) generation.
 * Uses an off-screen WebView to execute the obfuscated logic from Assets.
 */
class QQSignGenerator(private val context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val signLock = Any()

    @Volatile
    private var webView: WebView? = null

    @Volatile
    private var webViewReady: Boolean = false

    @Volatile
    private var encryptLatch: CountDownLatch? = null

    @Volatile
    private var encryptResultRef: AtomicReference<String?>? = null

    private inner class JsBridge {
        @JavascriptInterface
        fun onEncryptResult(value: String?) {
            encryptResultRef?.set(value)
            encryptLatch?.countDown()
        }
    }

    private val jsContent: String by lazy {
        appContext.assets.open("qq_sign.js").use { inputStream ->
            InputStreamReader(inputStream).readText()
        }
    }

    private val vmDecryptContent: String? by lazy {
        runCatching {
            appContext.assets.open("vm_new.js").use { inputStream ->
                InputStreamReader(inputStream).readText()
            }
        }.getOrNull()
    }

    private fun ensureWebView(): WebView {
        webView?.let { return it }

        val created = AtomicReference<WebView>()
        val createdLatch = CountDownLatch(1)
        val readyLatchRef = AtomicReference<CountDownLatch>()
        mainHandler.post {
            try {
                val readyLatch = CountDownLatch(1)
                readyLatchRef.set(readyLatch)
                val instance = WebView(appContext).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            webViewReady = true
                            readyLatch.countDown()
                        }
                    }
                    addJavascriptInterface(JsBridge(), "AndroidBridge")
                    // vm_new encryption relies on web crypto APIs; initialize on HTTPS origin.
                    loadUrl("https://y.qq.com/")
                }
                created.set(instance)
            } finally {
                createdLatch.countDown()
            }
        }

        createdLatch.await(2, TimeUnit.SECONDS)
        val instance = created.get()
            ?: throw IllegalStateException("Failed to initialize WebView signer")

        val readyLatch = readyLatchRef.get()
        if (readyLatch != null && !webViewReady) {
            readyLatch.await(8, TimeUnit.SECONDS)
        }

        webView = instance
        return instance
    }

    private fun decodeEvaluateResult(raw: String?): String? {
        if (raw == null || raw == "null" || raw.isBlank()) return null
        return try {
            if (raw.startsWith('"')) JSONArray("[$raw]").getString(0) else raw
        } catch (_: Exception) {
            raw
        }
    }

    /**
     * Generates a `zzb...` signature for the given request JSON string.
     */
    fun generateSign(jsonData: String): String? {
        return try {
            synchronized(signLock) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    Timber.e("generateSign should not run on main thread")
                    return null
                }

                val signerWebView = ensureWebView()
                val quotedJson = JSONObject.quote(jsonData)
                val evalScript = "(function(){$jsContent; return getSign($quotedJson);})()"

                val resultRef = AtomicReference<String?>()
                val latch = CountDownLatch(1)
                mainHandler.post {
                    signerWebView.evaluateJavascript(evalScript) { value ->
                        resultRef.set(decodeEvaluateResult(value))
                        latch.countDown()
                    }
                }

                if (!latch.await(3, TimeUnit.SECONDS)) {
                    Timber.e("WebView Sign timeout")
                    return null
                }
                resultRef.get()
            }
        } catch (e: Exception) {
            Timber.e(e, "WebView Sign error")
            null
        }
    }

    /**
     * Uses vm_new.js __cgiDecrypt to keep behavior aligned with reverse-engineering scripts.
     */
    fun decryptResponseWithVm(encryptedData: ByteArray): String? {
        val vmCode = vmDecryptContent ?: return null
        return try {
            synchronized(signLock) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    Timber.e("decryptResponseWithVm should not run on main thread")
                    return null
                }

                val signerWebView = ensureWebView()
                val b64 = Base64.encodeToString(encryptedData, Base64.NO_WRAP)
                val quotedB64 = JSONObject.quote(b64)
                val script = """
                    (function() {
                        var e = (typeof globalThis !== 'undefined') ? globalThis : this;
                        var oe = (typeof e !== 'undefined') ? e : ((typeof window !== 'undefined') ? window : ((typeof self !== 'undefined') ? self : this));
                        $vmCode
                        var raw = $quotedB64;
                        var bin = atob(raw);
                        var bytes = new Uint8Array(bin.length);
                        for (var i = 0; i < bin.length; i++) {
                            bytes[i] = bin.charCodeAt(i);
                        }
                        return oe.__cgiDecrypt(bytes.buffer);
                    })();
                """.trimIndent()

                val resultRef = AtomicReference<String?>()
                val latch = CountDownLatch(1)
                mainHandler.post {
                    signerWebView.evaluateJavascript(script) { value ->
                        resultRef.set(decodeEvaluateResult(value))
                        latch.countDown()
                    }
                }

                if (!latch.await(3, TimeUnit.SECONDS)) {
                    Timber.e("WebView vm_new decrypt timeout")
                    return null
                }
                resultRef.get()
            }
        } catch (e: Exception) {
            Timber.e(e, "WebView vm_new decrypt error")
            null
        }
    }

    /**
     * Uses vm_new.js __cgiEncrypt for request body encryption.
     */
    fun encryptRequestWithVm(plaintext: String): String? {
        val vmCode = vmDecryptContent ?: return null
        return try {
            synchronized(signLock) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    Timber.e("encryptRequestWithVm should not run on main thread")
                    return null
                }

                val signerWebView = ensureWebView()
                val quotedPlaintext = JSONObject.quote(plaintext)
                val script = """
                    (function() {
                        try {
                            var e = (typeof globalThis !== 'undefined') ? globalThis : this;
                            var oe = (typeof e !== 'undefined') ? e : ((typeof window !== 'undefined') ? window : ((typeof self !== 'undefined') ? self : this));
                            $vmCode
                            var payload = $quotedPlaintext;
                            var maybePromise = oe.__cgiEncrypt(payload);
                            if (maybePromise && typeof maybePromise.then === 'function') {
                                maybePromise.then(function(encrypted) {
                                    AndroidBridge.onEncryptResult(encrypted || "");
                                }).catch(function() {
                                    AndroidBridge.onEncryptResult("");
                                });
                            } else {
                                AndroidBridge.onEncryptResult(maybePromise || "");
                            }
                        } catch (err) {
                            AndroidBridge.onEncryptResult("");
                        }
                    })();
                """.trimIndent()

                val latch = CountDownLatch(1)
                val resultRef = AtomicReference<String?>()
                encryptLatch = latch
                encryptResultRef = resultRef

                mainHandler.post {
                    signerWebView.evaluateJavascript(script, null)
                }

                if (!latch.await(3, TimeUnit.SECONDS)) {
                    Timber.e("WebView vm_new encrypt timeout")
                    return null
                }

                val value = resultRef.get()
                if (value.isNullOrBlank()) null else value
            }
        } catch (e: Exception) {
            Timber.e(e, "WebView vm_new encrypt error")
            null
        } finally {
            encryptLatch = null
            encryptResultRef = null
        }
    }
}
