package com.theveloper.pixelplay.data.download

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import android.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadSettings @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)
    val rootDir: File get() = File(context.filesDir, "downloads").apply { mkdirs() }

    /**
     * 32-byte master key for Go-side extension storage encryption, generated once
     * and kept in EncryptedSharedPreferences (Android Keystore backed). The Go
     * layer requires it via SetExtensionStorageMasterKey before InitExtensionSystem.
     */
    val extensionMasterKey: String by lazy {
        val secure = runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "extension_key_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrNull()
        // ponytail: plaintext fallback mirrors JellyfinRepository; upgrade path = fail
        // closed instead, but then the whole download feature dies on Keystore errors.
        val target: SharedPreferences = secure
            ?: context.getSharedPreferences("extension_key_prefs_plain", Context.MODE_PRIVATE)
        target.getString(KEY_MASTER_KEY, null) ?: SecureRandom().let { rng ->
            val bytes = ByteArray(32).also(rng::nextBytes)
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            target.edit().putString(KEY_MASTER_KEY, encoded).apply()
            encoded
        }
    }

    /** SAF tree URI of the user-chosen destination folder; null until picked. */
    var safTreeUri: String?
        get() = prefs.getString(KEY_TREE_URI, null)
        set(value) = prefs.edit().putString(KEY_TREE_URI, value).apply()

    var defaultQuality: String
        get() = prefs.getString(KEY_QUALITY, "flac") ?: "flac"
        set(value) = prefs.edit().putString(KEY_QUALITY, value).apply()

    /** Extension registry URL entered by the user; null until set. */
    var registryUrl: String?
        get() = prefs.getString(KEY_REGISTRY_URL, null)
        set(value) = prefs.edit().putString(KEY_REGISTRY_URL, value).apply()

    /** Last-chosen download provider extension id; remembered across sessions. */
    var defaultProvider: String?
        get() = prefs.getString(KEY_DEFAULT_PROVIDER, null)
        set(value) = prefs.edit().putString(KEY_DEFAULT_PROVIDER, value).apply()

    /** Extension used for search (customSearch); null = combined metadata search. */
    var searchProvider: String?
        get() = prefs.getString(KEY_SEARCH_PROVIDER, null)
        set(value) = prefs.edit().putString(KEY_SEARCH_PROVIDER, value).apply()

    fun clearSafTree() = prefs.edit().remove(KEY_TREE_URI).apply()

    private companion object {
        const val KEY_TREE_URI = "saf_tree_uri"
        const val KEY_QUALITY = "default_quality"
        const val KEY_REGISTRY_URL = "registry_url"
        const val KEY_DEFAULT_PROVIDER = "default_download_provider"
        const val KEY_SEARCH_PROVIDER = "search_provider"
        const val KEY_MASTER_KEY = "extension_master_key"
    }
}
