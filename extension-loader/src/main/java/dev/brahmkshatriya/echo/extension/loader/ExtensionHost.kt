package dev.brahmkshatriya.echo.extension.loader

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.datasource.cache.SimpleCache
import dev.brahmkshatriya.echo.common.models.Message
import dev.brahmkshatriya.echo.common.models.NetworkConnection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ExtensionHost {
    val context: Context
    val settings: SharedPreferences
    val throwFlow: MutableSharedFlow<Throwable>
    val messageFlow: MutableSharedFlow<Message>
    val networkFlow: StateFlow<NetworkConnection>
    val cache: SimpleCache?
}
