package com.theveloper.pixelplay.extensions.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extensionEngine: dev.brahmkshatriya.echo.extension.loader.ExtensionLoader,
    private val userPreferences: com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _storeItems = MutableStateFlow<List<ExtensionStoreItem>>(emptyList())
    val storeItems: StateFlow<List<ExtensionStoreItem>> = _storeItems

    private val officialRegistry = "https://raw.githubusercontent.com/itsmechinmoy/echo-extensions/main/echo_extensions.json"
    private val loadedRegistries = mutableSetOf<String>()

    suspend fun fetchExtensions(code: String? = null) = withContext(Dispatchers.IO) {
        val registries = mutableListOf(officialRegistry)
        registries.addAll(userPreferences.extensionRegistriesFlow.first())

        if (code != null) {
            val newUrl = when {
                code.startsWith("http") -> code
                code.contains("/") -> "https://raw.githubusercontent.com/$code/main/echo_extensions.json"
                else -> null
            }
            if (newUrl != null && !registries.contains(newUrl)) {
                registries.add(newUrl)
                userPreferences.addExtensionRegistry(newUrl)
            }
        }

        registries.forEach { url ->
            if (loadedRegistries.contains(url) && code != url && code != null) return@forEach
            
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@forEach
                    val remoteExts = json.decodeFromString<List<RemoteExtension>>(body)
                    loadedRegistries.add(url)
                    
                    val current = _storeItems.value.toMutableList()
                    remoteExts.forEach { remote ->
                        if (current.none { it.remote.id == remote.id }) {
                            val installed = extensionEngine.all.value
                            val local = installed.find { it.metadata.id == remote.id }
                            current.add(ExtensionStoreItem(
                                remote = remote,
                                status = if (local != null) ExtensionStatus.INSTALLED else ExtensionStatus.AVAILABLE,
                                localVersion = local?.metadata?.version
                            ))
                        }
                    }
                    _storeItems.value = current
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateStoreItems(remoteExts: List<RemoteExtension>) {
        val installed = extensionEngine.all.value
        _storeItems.value = remoteExts.map { remote ->
            val local = installed.find { it.metadata.id == remote.id }
            ExtensionStoreItem(
                remote = remote,
                status = if (local != null) ExtensionStatus.INSTALLED else ExtensionStatus.AVAILABLE,
                localVersion = local?.metadata?.version
            )
        }
    }

    suspend fun downloadAndInstall(item: ExtensionStoreItem) = withContext(Dispatchers.IO) {
        val downloadUrl = item.remote.downloadUrl ?: getDownloadUrlFromGitHub(item.remote.updateUrl) ?: return@withContext
        
        // Update status to Downloading
        updateItemStatus(item.remote.id, ExtensionStatus.DOWNLOADING, 0.1f)

        try {
            val request = Request.Builder().url(downloadUrl).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val file = File(context.filesDir, "extensions/${item.remote.id}.apk")
                if (!file.parentFile.exists()) file.parentFile.mkdirs()
                
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                
                updateItemStatus(item.remote.id, ExtensionStatus.INSTALLED, 1f)
                // extensionEngine.refreshAndInit() is no longer needed; repository uses File flow.
            }
        } catch (e: Exception) {
            e.printStackTrace()
            updateItemStatus(item.remote.id, ExtensionStatus.AVAILABLE, 0f)
        }
    }

    private fun updateItemStatus(id: String, status: ExtensionStatus, progress: Float) {
        _storeItems.value = _storeItems.value.map {
            if (it.remote.id == id) it.copy(status = status, progress = progress) else it
        }
    }

    private fun getDownloadUrlFromGitHub(updateUrl: String): String? {
        try {
            // updateUrl example: https://api.github.com/repos/brahmkshatriya/echo-spotify-extension/releases
            val request = Request.Builder().url(updateUrl).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val releases = json.parseToJsonElement(body).jsonArray
                if (releases.isNotEmpty()) {
                    val latest = releases[0].jsonObject
                    val assets = latest["assets"]?.jsonArray ?: return null
                    val apkAsset = assets.find { 
                        it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true 
                    }
                    return apkAsset?.jsonObject["browser_download_url"]?.jsonPrimitive?.content
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
