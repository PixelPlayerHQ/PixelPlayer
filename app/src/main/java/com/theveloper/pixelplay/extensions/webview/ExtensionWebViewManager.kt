package com.theveloper.pixelplay.extensions.webview

import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionWebViewManager @Inject constructor() {

    private val _requestFlow = MutableStateFlow<ExtensionWebViewRequest<*>?>(null)
    val requestFlow: StateFlow<ExtensionWebViewRequest<*>?> = _requestFlow

    suspend fun <T> await(
        request: WebViewRequest<T>,
        reason: String
    ): Result<T?> {
        val deferred = CompletableDeferred<T?>()
        _requestFlow.value = ExtensionWebViewRequest(request, reason, deferred)
        
        return try {
            val result = deferred.await()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _requestFlow.value = null
        }
    }
}

data class ExtensionWebViewRequest<T>(
    val request: WebViewRequest<T>,
    val reason: String,
    val deferred: CompletableDeferred<T?>
)
