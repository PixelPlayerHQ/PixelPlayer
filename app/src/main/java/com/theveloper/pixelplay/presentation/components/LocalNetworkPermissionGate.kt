package com.theveloper.pixelplay.presentation.components

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.theveloper.pixelplay.utils.LocalNetworkAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Asks for [LocalNetworkAccess.PERMISSION] right before the app talks to a server on the local
 * network, so self-hosted servers keep working on Android 17+.
 */
class LocalNetworkPermissionGate internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val requestPermission: (onResult: (Boolean) -> Unit) -> Unit
) {
    /**
     * Calls [onResult] with `true` when [serverUrl] can be reached: it is not on the local
     * network, the permission is not needed on this device, or the user granted it.
     */
    fun ensureAccess(serverUrl: String?, onResult: (granted: Boolean) -> Unit) {
        scope.launch {
            if (LocalNetworkAccess.needsPermissionFor(context, serverUrl)) {
                requestPermission(onResult)
            } else {
                onResult(true)
            }
        }
    }
}

@Composable
fun rememberLocalNetworkPermissionGate(): LocalNetworkPermissionGate {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingResult = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingResult[0]?.invoke(granted)
        pendingResult[0] = null
    }
    return remember(context, scope, launcher) {
        LocalNetworkPermissionGate(context, scope) { onResult ->
            pendingResult[0] = onResult
            launcher.launch(LocalNetworkAccess.PERMISSION)
        }
    }
}
