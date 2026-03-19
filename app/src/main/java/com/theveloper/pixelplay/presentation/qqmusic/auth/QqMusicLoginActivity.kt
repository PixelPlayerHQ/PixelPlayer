package com.theveloper.pixelplay.presentation.qqmusic.auth

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import org.json.JSONObject

@AndroidEntryPoint
class QqMusicLoginActivity : ComponentActivity() {
    companion object {
        const val TARGET_URL = "https://y.qq.com/"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelPlayTheme {
                QqMusicLoginScreen(onClose = { finish() })
            }
        }
    }
}

private data class QqMusicWebUiState(
    val title: String = "",
    val currentUrl: String = QqMusicLoginActivity.TARGET_URL,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoadingPage: Boolean = true,
    val pageProgress: Int = 0,
    val lastError: String? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QqMusicLoginScreen(
    viewModel: QqMusicLoginViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val loginState by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val titleStyle = rememberQqMusicLoginTitleStyle()

    var webView by remember { mutableStateOf<WebView?>(null) }
    var webUiState by remember { mutableStateOf(QqMusicWebUiState()) }
    var showExitDialog by remember { mutableStateOf(false) }
    var pageLoadTimeout by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            webView = null
        }
    }

    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is QqMusicLoginState.Success -> {
                Toast.makeText(context, "Welcome, ${state.nickname}!", Toast.LENGTH_SHORT).show()
                onClose()
            }

            is QqMusicLoginState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearError()
            }

            else -> Unit
        }
    }

    LaunchedEffect(webUiState.isLoadingPage, webUiState.currentUrl) {
        if (!webUiState.isLoadingPage) {
            pageLoadTimeout = false
            return@LaunchedEffect
        }

        delay(20_000)
        if (webUiState.isLoadingPage) {
            pageLoadTimeout = true
            snackbarHostState.showSnackbar("Page load timed out. You can retry without losing your progress.")
        }
    }

    BackHandler(enabled = true) {
        when {
            webView?.canGoBack() == true -> webView?.goBack()
            else -> showExitDialog = true
        }
    }

    fun captureAndSubmitCookies() {
        if (loginState is QqMusicLoginState.Loading) return

        readQqMusicCookies().fold(
            onSuccess = { cookieJson ->
                webUiState = webUiState.copy(lastError = null)
                viewModel.processCookies(cookieJson)
            },
            onFailure = { error ->
                val message = error.message ?: "Could not read session cookies."
                viewModel.clearError()
                webUiState = webUiState.copy(lastError = message)
            }
        )
    }

    fun navigateBack() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            showExitDialog = true
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Text(text = "Exit QQ Music login?", fontFamily = GoogleSansRounded)
            },
            text = {
                Text(
                    text = "You can come back later. Current page state will be discarded when closing.",
                    fontFamily = GoogleSansRounded
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onClose()
                    }
                ) {
                    Text(text = "Exit", fontFamily = GoogleSansRounded)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(text = "Stay", fontFamily = GoogleSansRounded)
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Login to QQ Music",
                        style = titleStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        onClick = ::navigateBack,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 4.dp,
                actions = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 10.dp),
                        onClick = { webView?.goBack() },
                        enabled = webUiState.canGoBack && loginState !is QqMusicLoginState.Loading,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Web back"
                        )
                    }

                    FilledIconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = { webView?.goForward() },
                        enabled = webUiState.canGoForward && loginState !is QqMusicLoginState.Loading,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Web forward"
                        )
                    }

                    FilledIconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = { webView?.reload() },
                        enabled = loginState !is QqMusicLoginState.Loading,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh"
                        )
                    }

                    FilledIconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = {
                            pageLoadTimeout = false
                            webUiState = webUiState.copy(lastError = null)
                            webView?.loadUrl(QqMusicLoginActivity.TARGET_URL)
                        },
                        enabled = loginState !is QqMusicLoginState.Loading,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = "Open home"
                        )
                    }
                },
                floatingActionButton = {
                    SmallExtendedFloatingActionButton(
                        onClick = ::captureAndSubmitCookies,
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        if (loginState is QqMusicLoginState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (loginState is QqMusicLoginState.Loading) "Saving..." else "Done",
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (webUiState.isLoadingPage) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = { (webUiState.pageProgress / 100f).coerceIn(0f, 1f) }
                )
            }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Text(
                    text = "Security note: your password is entered only in QQ Music web pages. PixelPlay stores session cookies to sync your library.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val effectiveError = when {
                pageLoadTimeout -> "Page is taking too long to load. Use refresh or try another network."
                webUiState.lastError != null -> webUiState.lastError
                else -> null
            }

            effectiveError?.let { errorText ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorText,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                pageLoadTimeout = false
                                webUiState = webUiState.copy(lastError = null)
                                webView?.reload()
                            }
                        ) {
                            Text(text = "Retry", fontFamily = GoogleSansRounded)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                QqMusicWebView(
                    modifier = Modifier.fillMaxSize(),
                    onWebViewCreated = { created ->
                        webView = created
                        webUiState = webUiState.copy(
                            canGoBack = created.canGoBack(),
                            canGoForward = created.canGoForward(),
                            currentUrl = created.url ?: QqMusicLoginActivity.TARGET_URL,
                            title = created.title.orEmpty()
                        )
                    },
                    onNavigationChanged = { view ->
                        webUiState = webUiState.copy(
                            canGoBack = view.canGoBack(),
                            canGoForward = view.canGoForward(),
                            currentUrl = view.url ?: QqMusicLoginActivity.TARGET_URL,
                            title = view.title.orEmpty()
                        )
                    },
                    onLoadingChanged = { loading, url ->
                        webUiState = webUiState.copy(
                            isLoadingPage = loading,
                            currentUrl = url ?: webUiState.currentUrl,
                            lastError = if (loading) null else webUiState.lastError
                        )
                    },
                    onProgressChanged = { progress ->
                        webUiState = webUiState.copy(pageProgress = progress.coerceIn(0, 100))
                    },
                    onMainFrameError = { message ->
                        webUiState = webUiState.copy(lastError = message, isLoadingPage = false)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberQqMusicLoginTitleStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(620),
                        FontVariation.width(128f),
                        FontVariation.Setting("ROND", 88f),
                        FontVariation.Setting("XTRA", 520f),
                        FontVariation.Setting("YOPQ", 90f),
                        FontVariation.Setting("YTLC", 505f)
                    )
                )
            ),
            fontWeight = FontWeight(700),
            fontSize = 18.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.2).sp
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun QqMusicWebView(
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit,
    onNavigationChanged: (WebView) -> Unit,
    onLoadingChanged: (Boolean, String?) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onMainFrameError: (String) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    userAgentString = QqMusicLoginActivity.DESKTOP_UA
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    settings.safeBrowsingEnabled = true
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        return false
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onLoadingChanged(true, url)
                        view?.let(onNavigationChanged)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onLoadingChanged(false, url)
                        view?.let(onNavigationChanged)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            val description = error?.description?.toString()?.ifBlank {
                                "WebView load failed."
                            } ?: "WebView load failed."
                            onMainFrameError(description)
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                            onMainFrameError(
                                "HTTP ${errorResponse?.statusCode ?: 0} while loading QQ Music."
                            )
                        }
                    }
                }

                loadUrl(QqMusicLoginActivity.TARGET_URL)
                onWebViewCreated(this)
            }
        }
    )
}

private fun readQqMusicCookies(): Result<String> {
    return try {
        val manager = CookieManager.getInstance()
        val map = linkedMapOf<String, String>()

        val cookieHosts = listOf(
            "https://y.qq.com/",
            "https://u6.y.qq.com/",
            "https://u.y.qq.com/",
            "https://c.y.qq.com/"
        )

        cookieHosts.forEach { host ->
            cookieStringToMap(manager.getCookie(host).orEmpty()).forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) {
                    map[key] = value
                }
            }
        }

        if (map.isEmpty()) {
            return Result.failure(IllegalStateException("No cookies found. Log in first."))
        }

        val hasLoginCookie = !map["uin"].isNullOrBlank() ||
            !map["qqmusic_key"].isNullOrBlank() ||
            !map["qm_keyst"].isNullOrBlank()

        if (!hasLoginCookie) {
            return Result.failure(
                IllegalStateException(
                    "Login not detected yet. Complete QQ Music login before pressing Done."
                )
            )
        }

        Result.success(JSONObject(map as Map<*, *>).toString())
    } catch (error: Throwable) {
        Result.failure(
            IllegalStateException("Failed to read QQ Music cookies: ${error.message}", error)
        )
    }
}

private fun cookieStringToMap(raw: String): MutableMap<String, String> {
    val map = linkedMapOf<String, String>()
    raw.split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() && it.contains('=') }
        .forEach { part ->
            val idx = part.indexOf('=')
            val key = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (key.isNotEmpty()) map[key] = value
        }
    return map
}
