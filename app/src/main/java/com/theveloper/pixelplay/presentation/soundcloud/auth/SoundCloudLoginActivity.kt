package com.theveloper.pixelplay.presentation.soundcloud.auth

import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.soundcloud.SoundCloudPrefsViewModel
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SoundCloudLoginActivity : ComponentActivity() {

    companion object {
        const val TARGET_URL = "https://soundcloud.com/signin"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
        const val EXTRA_OAUTH_TOKEN = "oauth_token"
        const val EXTRA_COOKIE_HEADER = "cookie_header"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelPlayTheme {
                SoundCloudWebLoginScreen(
                    onClose = { finish() },
                    onSessionCaptured = { token, cookies ->
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(EXTRA_OAUTH_TOKEN, token)
                                .putExtra(EXTRA_COOKIE_HEADER, cookies),
                        )
                        finish()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundCloudWebLoginScreen(
    onClose: () -> Unit,
    onSessionCaptured: (oauthToken: String, cookieHeader: String) -> Unit,
    prefsViewModel: SoundCloudPrefsViewModel = hiltViewModel(),
) {
    var progress by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler { onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.soundcloud_sign_in)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val result = extractSoundCloudSession()
                            if (result != null) {
                                prefsViewModel.saveSession(result.first, result.second)
                                onSessionCaptured(result.first, result.second)
                            } else {
                                Toast.makeText(
                                    webView?.context,
                                    R.string.soundcloud_sign_in_incomplete,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.soundcloud_sign_in_done))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                SoundCloudWebView(
                    onProgress = { progress = it },
                    onWebViewCreated = { webView = it },
                    onCookiesMaybeReady = {
                        val result = extractSoundCloudSession()
                        if (result != null) {
                            prefsViewModel.saveSession(result.first, result.second)
                            onSessionCaptured(result.first, result.second)
                        }
                    },
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SoundCloudWebView(
    onProgress: (Int) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onCookiesMaybeReady: () -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.userAgentString = SoundCloudLoginActivity.DESKTOP_UA
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgress(newProgress)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        onCookiesMaybeReady()
                    }
                }
                loadUrl(SoundCloudLoginActivity.TARGET_URL)
                onWebViewCreated(this)
            }
        },
    )
}

internal fun extractSoundCloudSession(): Pair<String, String>? {
    val cm = CookieManager.getInstance()
    val main = cm.getCookie("https://soundcloud.com").orEmpty()
    val api = cm.getCookie("https://api-v2.soundcloud.com").orEmpty()
    val merged = listOf(main, api).filter { it.isNotBlank() }.joinToString("; ")
    if (merged.isBlank()) return null

    val map = linkedMapOf<String, String>()
    merged.split(';')
        .map { it.trim() }
        .filter { it.contains('=') }
        .forEach { part ->
            val idx = part.indexOf('=')
            val key = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (key.isNotEmpty()) map[key] = value
        }

    val token = map["oauth_token"]
        ?: map["oauth_token".uppercase()]
        ?: return null
    if (token.isBlank()) return null

    val cookieHeader = map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    return token to cookieHeader
}
