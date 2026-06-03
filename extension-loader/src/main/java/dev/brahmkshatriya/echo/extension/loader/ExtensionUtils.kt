package dev.brahmkshatriya.echo.extension.loader

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.models.Metadata
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.loader.exceptions.AppException.Companion.toAppException
import dev.brahmkshatriya.echo.extension.loader.exceptions.ExtensionNotFoundException
import dev.brahmkshatriya.echo.extension.loader.utils.ContextUtils.getSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

object ExtensionUtils {

    suspend fun <R> Extension<*>.get(
        block: suspend ExtensionClient.() -> R
    ) = runCatching {
        runCatching {
            withContext(Dispatchers.IO) { instance.value().getOrThrow().block() }
        }.getOrElse { throw it.toAppException(this) }
    }

    suspend inline fun <reified C, R> Extension<*>.getAs(
        crossinline block: suspend C.() -> R
    ): Result<R> = get {
        val client = this as? C
            ?: throw ClientException.NotSupported(C::class.simpleName ?: "Unknown Class")
        block(client)
    }

    suspend inline fun <reified C, R> Extension<*>.getIf(
        crossinline block: suspend C.() -> R
    ): Result<R?> = get {
        val client = this as? C
        client?.let { block(it) }
    }

    suspend fun <T> Result<T>.getOrThrow(
        throwableFlow: MutableSharedFlow<Throwable>
    ) = getOrElse {
        throwableFlow.emit(it)
        it.printStackTrace()
        null
    }

    suspend inline fun <reified C> Extension<*>.runIf(
        throwableFlow: MutableSharedFlow<Throwable>,
        crossinline block: suspend C.() -> Unit
    ) { getIf<C, Unit>(block).getOrThrow(throwableFlow) }

    suspend inline fun <reified C, R> Extension<*>.getIf(
        throwableFlow: MutableSharedFlow<Throwable>,
        crossinline block: suspend C.() -> R
    ): R? = getIf<C, R>(block).getOrThrow(throwableFlow)

    suspend fun Extension<*>.inject(
        id: String,
        throwableFlow: MutableSharedFlow<Throwable>,
        block: suspend ExtensionClient.() -> Unit
    ) = runCatching {
        instance.injectOrRun(id) { withContext(Dispatchers.IO) { block() } }
    }.getOrElse {
        throwableFlow.emit(it.toAppException(this))
    }

    suspend inline fun <reified T> Extension<*>.isClient() = instance.value().getOrNull() is T

    suspend fun <T : Extension<*>> Flow<List<T>>.getExtension(id: String?): T? {
        val list = first { it.isNotEmpty() }
        return list.find { it.id == id }
    }

    suspend fun <T : Extension<*>> Flow<List<T>>.getExtensionOrThrow(id: String?) =
        getExtension(id) ?: throw ExtensionNotFoundException(id)

    fun <T : Extension<*>> Flow<List<T>>.getExtensionFlow(id: String?): Flow<T?> {
        return map { list -> list.find { it.id == id } }
    }

    fun extensionPrefId(extensionType: String, extensionId: String) = "sec-$extensionType-$extensionId"
    
    fun String.prefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                this,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to a DIFFERENT file name to avoid SecurityException
            context.getSharedPreferences("err-$this", Context.MODE_PRIVATE)!!
        }
    }

    fun Metadata.prefs(context: Context) = extensionPrefId(type.name, id).prefs(context)
    fun Extension<*>.prefs(context: Context) = metadata.prefs(context)

    fun getSettings(context: Context, metadata: Metadata): Settings {
        return toSettings(metadata.prefs(context))
    }

    fun getGlobalSettings(context: Context): Settings {
        return toSettings(context.getSettings())
    }

    fun toSettings(prefs: SharedPreferences) = object : Settings {
        override fun getString(key: String) = prefs.getString(key, null)
        override fun putString(key: String, value: String?) {
            prefs.edit { putString(key, value) }
        }

        override fun getInt(key: String) =
            if (prefs.contains(key)) prefs.getInt(key, 0) else null

        override fun putInt(key: String, value: Int?) {
            prefs.edit { if (value != null) putInt(key, value) else remove(key) }
        }

        override fun getBoolean(key: String) =
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null

        override fun putBoolean(key: String, value: Boolean?) {
            prefs.edit { if (value != null) putBoolean(key, value) else remove(key) }
        }

        override fun getStringSet(key: String) = prefs.getStringSet(key, null)
        override fun putStringSet(key: String, value: Set<String>?) {
            prefs.edit { putStringSet(key, value) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun SharedPreferences.copyTo(dest: SharedPreferences) = dest.edit {
        all.entries.forEach { entry ->
            val value = entry.value ?: return@forEach
            val key = entry.key
            when (value) {
                is String -> putString(key, value)
                is Set<*> -> putStringSet(key, value as Set<String>)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is Boolean -> putBoolean(key, value)
                else -> {}
            }
        }
    }
}
