package dev.brahmkshatriya.echo.extension.loader.repo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.models.ImportType
import dev.brahmkshatriya.echo.common.models.Metadata
import dev.brahmkshatriya.echo.extension.loader.repo.ExtensionParser.Companion.FEATURE
import dev.brahmkshatriya.echo.extension.loader.repo.ExtensionParser.Companion.PACKAGE_FLAGS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.WeakHashMap
import kotlin.coroutines.suspendCoroutine

class AppRepository(
    private val scope: CoroutineScope,
    private val context: Context,
    private val parser: ExtensionParser
) : ExtensionRepository {
    private suspend fun Context.getStaticPackages() = withContext(Dispatchers.IO) {
        runCatching {
            packageManager.getInstalledPackages(PACKAGE_FLAGS).mapNotNull {
                runCatching {
                    val isExtension = it.reqFeatures.orEmpty().any { featureInfo ->
                        featureInfo?.name?.startsWith(FEATURE) ?: false
                    }
                    if (isExtension) File(it.applicationInfo!!.sourceDir!!) else null
                }.getOrNull()
            }
        }.getOrNull().orEmpty()
    }

    private val map =
        WeakHashMap<String, Pair<String, Result<Pair<Metadata, Lazy<ExtensionClient>>>>>()
    private val mutex = Mutex()

    override suspend fun loadExtensions() = mutex.withLock {
        parser.getAllDynamically(ImportType.App, map, context.getStaticPackages())
    }

    override val flow = channelFlow {
        send(null)
        send(loadExtensions())
        suspendCoroutine {
            val receiver = Receiver {
                scope.launch {
                    send(loadExtensions())
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(
                context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }


    class Receiver(private val onReceive: () -> Unit) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = onReceive()
    }
}
