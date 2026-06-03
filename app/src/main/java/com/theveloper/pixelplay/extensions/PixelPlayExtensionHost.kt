package com.theveloper.pixelplay.extensions

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dev.brahmkshatriya.echo.common.models.Message
import dev.brahmkshatriya.echo.common.models.NetworkConnection
import dev.brahmkshatriya.echo.extension.loader.ExtensionHost
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PixelPlayExtensionHost @Inject constructor(
    private val app: Application
) : ExtensionHost {
    override val context: Context get() = app
    override val settings: SharedPreferences = app.getSharedPreferences("extensions", Context.MODE_PRIVATE)
    override val throwFlow: MutableSharedFlow<Throwable> = MutableSharedFlow()
    override val messageFlow: MutableSharedFlow<Message> = MutableSharedFlow()

    private val _networkFlow = MutableStateFlow(getCurrentNetworkConnection())
    override val networkFlow: StateFlow<NetworkConnection> = _networkFlow

    override val cache: SimpleCache by lazy {
        val cacheDir = File(app.cacheDir, "extension_media")
        SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024L),
            StandaloneDatabaseProvider(app)
        )
    }

    init {
        observeNetworkChanges()
    }

    private fun getCurrentNetworkConnection(): NetworkConnection {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return NetworkConnection.NotConnected
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return NetworkConnection.NotConnected
        return when {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkConnection.Unmetered
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkConnection.Metered
            else -> NetworkConnection.NotConnected
        }
    }

    private fun observeNetworkChanges() {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _networkFlow.value = getCurrentNetworkConnection()
            }

            override fun onLost(network: Network) {
                _networkFlow.value = getCurrentNetworkConnection()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                _networkFlow.value = getCurrentNetworkConnection()
            }
        })
    }
}
