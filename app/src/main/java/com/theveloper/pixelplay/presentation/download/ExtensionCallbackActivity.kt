package com.theveloper.pixelplay.presentation.download

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.download.DownloadBackendApi
import com.theveloper.pixelplay.data.download.DownloadRepository
import com.theveloper.pixelplay.data.download.DownloadWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Receives the browser redirect after an extension verification (extensions hard-code
 * spotiflac://session-grant as their callback), completes it in the backend and retries
 * the downloads that were waiting on it. Finishes immediately; work continues in background.
 */
@AndroidEntryPoint
class ExtensionCallbackActivity : ComponentActivity() {

    @Inject lateinit var backend: DownloadBackendApi
    @Inject lateinit var repository: DownloadRepository

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        finish()
        if (uri == null || !uri.scheme.equals("spotiflac", ignoreCase = true)) return

        val sessionGrant = uri.host.equals("session-grant", ignoreCase = true)
        val code = (if (sessionGrant) uri.getQueryParameter("grant") ?: uri.getQueryParameter("code")
        else uri.getQueryParameter("code"))?.trim().orEmpty()
        val state = uri.getQueryParameter("state")?.trim().orEmpty()
        if (code.isEmpty() || state.isEmpty()) {
            Log.w(TAG, "Extension callback missing code or state")
            return
        }

        val app = applicationContext
        GlobalScope.launch(Dispatchers.IO) {
            val extId = runCatching { backend.completeCallback(sessionGrant, state, code) }
                .onFailure { Log.w(TAG, "Extension callback failed", it) }
                .getOrNull()
            if (extId != null) {
                repository.retryVerification(extId)
                DownloadWorker.enqueue(app)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    app,
                    if (extId != null) R.string.download_auth_success else R.string.download_auth_failed,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private companion object { const val TAG = "ExtensionCallback" }
}
