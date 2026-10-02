package com.theveloper.pixelplay.presentation.youtube.auth

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import com.theveloper.pixelplay.youtube.YoutubeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class YoutubeLoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelPlayTheme {
                YoutubeWebLoginScreen(onClose = { finish() })
            }
        }
    }

    companion object {
        const val TARGET_URL = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2F&hl=en"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YoutubeWebLoginScreen(
    onClose: () -> Unit,
    viewModel: YoutubeViewModel = hiltViewModel(),
) {
    var progress by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    BackHandler { onClose() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.youtube_sign_in),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.youtube_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val cookies = extractYoutubeSession()
                            if (cookies != null && viewModel.importSession(cookies)) {
                                onClose()
                            } else {
                                Toast.makeText(
                                    webView?.context,
                                    R.string.youtube_sign_in_incomplete,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.youtube_sign_in_done), fontFamily = GoogleSansRounded)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.fillMaxSize()) {
                var openedLikedMusic by remember { mutableStateOf(false) }
                var importing by remember { mutableStateOf(false) }
                YoutubeWebView(
                    onProgress = { progress = it },
                    onWebViewCreated = { webView = it },
                    onCookiesMaybeReady = { url ->
                        if (importing) return@YoutubeWebView
                        val cookies = extractYoutubeSession() ?: return@YoutubeWebView
                        val hasAuth = listOf("SAPISID", "__Secure-1PAPISID", "__Secure-3PAPISID")
                            .any { key -> cookies.contains("$key=", ignoreCase = true) }
                        if (!hasAuth) return@YoutubeWebView
                        val onMusic = url.orEmpty().contains("music.youtube.com")
                        if (!onMusic) {
                            if (!openedLikedMusic) {
                                openedLikedMusic = true
                                webView?.loadUrl("https://music.youtube.com/playlist?list=LM")
                            }
                            return@YoutubeWebView
                        }
                        importing = true
                        val view = webView
                        if (view == null) {
                            if (viewModel.importSession(cookies)) onClose() else importing = false
                            return@YoutubeWebView
                        }
                        // Same trick KuroMusic/InnerTune use: read the page's InnerTube config
                        // so requests carry the signed-in visitorData and channel (DATASYNC_ID).
                        view.evaluateJavascript(IDENTITY_SCRIPT) { result ->
                            val (visitor, dataSync) = parseIdentity(result)
                            val fresh = extractYoutubeSession() ?: cookies
                            if (viewModel.importSession(fresh, visitor, dataSync)) onClose() else importing = false
                        }
                    },
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YoutubeWebView(
    onProgress: (Int) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onCookiesMaybeReady: (String?) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgress(newProgress)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        onCookiesMaybeReady(url)
                    }
                }
                loadUrl(YoutubeLoginActivity.TARGET_URL)
                onWebViewCreated(this)
            }
        },
    )
}

private const val IDENTITY_SCRIPT = """
(function () {
  try {
    var cfg = (window.ytcfg && (ytcfg.data_ || (ytcfg.get && {VISITOR_DATA: ytcfg.get('VISITOR_DATA'), DATASYNC_ID: ytcfg.get('DATASYNC_ID')})))
      || (window.yt && yt.config_) || {};
    return JSON.stringify({ v: cfg.VISITOR_DATA || '', d: cfg.DATASYNC_ID || '' });
  } catch (e) { return '{}'; }
})()
"""

/** evaluateJavascript hands back a JSON string literal wrapping our JSON payload. */
internal fun parseIdentity(result: String?): Pair<String, String> {
    if (result.isNullOrBlank() || result == "null") return "" to ""
    return runCatching {
        val inner = org.json.JSONTokener(result).nextValue()
        val json = when (inner) {
            is org.json.JSONObject -> inner
            is String -> org.json.JSONObject(inner)
            else -> return "" to ""
        }
        json.optString("v").trim() to json.optString("d").trim()
    }.getOrDefault("" to "")
}

internal fun extractYoutubeSession(): String? {
    val manager = CookieManager.getInstance()
    val map = linkedMapOf<String, String>()
    // music.youtube.com first: those are the cookies YouTube Music itself sends. Other hosts only
    // fill in names that are missing so we never overwrite a value with one from a different domain.
    listOf(
        "https://music.youtube.com",
        "https://www.youtube.com",
        "https://youtube.com",
        "https://accounts.google.com",
        "https://google.com",
    ).forEach { host ->
        manager.getCookie(host).orEmpty()
            .split(';')
            .map { it.trim() }
            .filter { it.contains('=') }
            .forEach { part ->
                val index = part.indexOf('=')
                val key = part.substring(0, index).trim()
                val value = part.substring(index + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty() && !map.containsKey(key)) map[key] = value
            }
    }
    val signedIn = map.keys.any { key ->
        key.equals("SAPISID", ignoreCase = true) ||
            key.equals("__Secure-1PAPISID", ignoreCase = true) ||
            key.equals("__Secure-3PAPISID", ignoreCase = true)
    }
    if (!signedIn) return null
    return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
}
