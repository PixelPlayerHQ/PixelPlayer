package com.theveloper.pixelplay.data.download

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Search result bundle */
data class SearchResult(val tracks: List<ExtTrack>)

sealed interface BackendResult<out T> {
    data class Ok<T>(val value: T) : BackendResult<T>
    data class Failed(val response: DownloadResponse) : BackendResult<Nothing>
}

interface DownloadBackendApi {
    fun initIfNeeded()
    fun listInstalled(): List<InstalledExtension>
    fun installFromFile(path: String): BackendResult<InstalledExtension?>
    fun listRepoExtensions(forceRefresh: Boolean): List<RepoExtension>
    fun installFromRepo(id: String): BackendResult<InstalledExtension?>
    /** Persists and applies a user-supplied registry URL. Returns null on success, error message on failure. */
    fun setRegistryUrl(url: String): String?
    /** Removes the stored registry URL and clears the repo cache. */
    fun clearRegistryUrl()
    fun remove(id: String)
    fun setEnabled(id: String, enabled: Boolean)
    fun search(query: String, limit: Int = 30): List<ExtTrack>
    fun homeFeed(extensionId: String): HomeFeed
    /** Extension customSearch with a manifest filter id; throws with the extension's message on failure. */
    fun customSearch(extensionId: String, query: String, filter: String?, limit: Int = 30): List<ExtTrack>
    /** Album/playlist/artist details from a metadata extension; throws on failure. */
    fun providerMetadata(providerId: String, type: String, id: String): ProviderMetadata
    fun download(request: DownloadRequest): BackendResult<DownloadResponse>
    fun progressDelta(sinceSeq: Long): ProgressDelta
    fun waitProgressDelta(sinceSeq: Long, timeoutMs: Long): ProgressDelta
    fun cancel(itemId: String)
    fun pendingAuth(extensionId: String): String
    fun submitAuthCode(extensionId: String, code: String)
    /**
     * Completes a browser verification callback (spotiflac://session-grant or an OAuth
     * callback). Returns the owning extension id; throws when the extension rejects it.
     */
    fun completeCallback(sessionGrant: Boolean, state: String, code: String): String
}

@Singleton
class GobackendDownloadBackend @Inject constructor(
    private val settings: DownloadSettings,
) : DownloadBackendApi {

    private companion object { const val TAG = "DownloadBackend" }

    private val lock = Any()
    private var initialized = false

    override fun initIfNeeded() {
        synchronized(lock) {
            if (initialized) return
            val extDir = File(settings.rootDir, "extensions")
            val dataDir = File(settings.rootDir, "download_data")
            extDir.mkdirs(); dataDir.mkdirs()
            runCatching {
                gobackend.Gobackend.setExtensionStorageMasterKey(settings.extensionMasterKey)
                gobackend.Gobackend.initExtensionSystem(extDir.absolutePath, dataDir.absolutePath)
                gobackend.Gobackend.loadExtensionsFromDir(extDir.absolutePath)
            }
                .onSuccess { initialized = true }
                .onFailure { Log.e(TAG, "initExtensionSystem($extDir, $dataDir) failed", it) }
            // Best-effort repo init: offline/failure must NOT flip initialized
            // back to false, the core extension system still works without it.
            runCatching {
                gobackend.Gobackend.initExtensionRepoJSON(dataDir.absolutePath)
                settings.registryUrl?.let { gobackend.Gobackend.setRepoRegistryURLJSON(it) }
            }
        }
    }

    override fun listInstalled(): List<InstalledExtension> {
        initIfNeeded()
        if (!initialized) return emptyList()
        val raw = gobackend.Gobackend.getInstalledExtensions()
        val list = runCatching { DownloadJson.decodeFromString<List<InstalledExtension>>(raw.ifEmpty { "[]" }) }
            .getOrElse { emptyList() }
            // Go builds this from a map, so the order changes between calls; keep it stable A→Z.
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, InstalledExtension::displayName).thenBy { it.id })
        Log.d(TAG, "listInstalled: ${list.size} extensions: ${list.map { "${it.id}(types=${it.types},download=${it.isDownloadProvider},enabled=${it.enabled},err=${it.error},qualities=${it.qualityOptions.map(QualityOption::id)})" }}")
        return list
    }

    override fun installFromFile(path: String): BackendResult<InstalledExtension?> {
        initIfNeeded()
        if (!initialized) {
            Log.e(TAG, "installFromFile($path): extension system not initialized")
            return BackendResult.Failed(
                DownloadResponse(success = false, error = "extension system failed to initialize", errorType = "install_failed"),
            )
        }
        return runCatching {
            val raw = gobackend.Gobackend.loadExtensionFromPath(path)
            val id = DownloadJson.decodeFromString<InstallResult>(raw.ifEmpty { "{}" })
            BackendResult.Ok(listInstalled().firstOrNull { it.id == id.name })
        }.getOrElse {
            Log.e(TAG, "installFromFile($path) failed", it)
            BackendResult.Failed(
                DownloadResponse(success = false, error = it.message, errorType = "install_failed"),
            )
        }
    }

    override fun listRepoExtensions(forceRefresh: Boolean): List<RepoExtension> {
        if (settings.registryUrl == null) return emptyList() // no registry configured yet
        initIfNeeded()
        // getRepoExtensionsJSON returns a JSON string and may throw (gomobile
        // String method); empty list on any failure.
        return runCatching {
            // GetRepoExtensionsJSON marshals the extensions array directly.
            DownloadJson.decodeFromString<List<RepoExtension>>(
                gobackend.Gobackend.getRepoExtensionsJSON(forceRefresh),
            )
        }.onFailure { Log.e(TAG, "listRepoExtensions failed", it) }.getOrDefault(emptyList())
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName.ifEmpty { it.name.ifEmpty { it.id } } })
    }

    override fun setRegistryUrl(url: String): String? {
        initIfNeeded()
        val applied = runCatching { gobackend.Gobackend.setRepoRegistryURLJSON(url.trim()) }
        return applied.fold(
            onSuccess = { settings.registryUrl = url.trim(); null },
            onFailure = { it.message ?: "Invalid registry URL" },
        )
    }

    override fun clearRegistryUrl() {
        initIfNeeded()
        runCatching { gobackend.Gobackend.clearRepoRegistryURLJSON() }
        settings.registryUrl = null
    }

    override fun installFromRepo(id: String): BackendResult<InstalledExtension?> {
        initIfNeeded()
        val dir = File(settings.rootDir, "repo_dl")
        return runCatching {
            dir.mkdirs()
            // downloadRepoExtensionJSON verifies sha256 and returns the plain
            // .sflx destPath, not JSON.
            val destPath = gobackend.Gobackend.downloadRepoExtensionJSON(id, dir.absolutePath)
            installFromFile(destPath)
        }.getOrElse {
            Log.e(TAG, "installFromRepo($id) failed", it)
            BackendResult.Failed(
                DownloadResponse(success = false, error = it.message, errorType = "repo_download_failed"),
            )
        }.also { dir.deleteRecursively() }
    }

    override fun remove(id: String) { initIfNeeded(); gobackend.Gobackend.removeExtensionByID(id) }

    override fun setEnabled(id: String, enabled: Boolean) {
        initIfNeeded(); gobackend.Gobackend.setExtensionEnabledByID(id, enabled)
    }

    override fun search(query: String, limit: Int): List<ExtTrack> {
        initIfNeeded()
        val raw = gobackend.Gobackend.searchTracksWithMetadataProvidersJSON(query, limit.toLong(), true)
        return runCatching { DownloadJson.decodeFromString<List<ExtTrack>>(raw) }.getOrDefault(emptyList())
    }

    override fun homeFeed(extensionId: String): HomeFeed {
        initIfNeeded()
        return runCatching {
            DownloadJson.decodeFromString<HomeFeed>(gobackend.Gobackend.getExtensionHomeFeedJSON(extensionId))
        }.getOrElse {
            Log.e(TAG, "homeFeed($extensionId) failed", it)
            HomeFeed(error = it.message)
        }.also { Log.d(TAG, "homeFeed($extensionId): ${it.sections.size} sections, error=${it.error}") }
    }

    override fun customSearch(extensionId: String, query: String, filter: String?, limit: Int): List<ExtTrack> {
        initIfNeeded()
        val options = buildJsonObject {
            filter?.let { put("filter", it) }
            put("limit", limit)
        }
        val raw = gobackend.Gobackend.customSearchWithExtensionJSON(extensionId, query, options.toString())
        // Go sets provider_id on every result; keep the extension id as a fallback.
        return DownloadJson.decodeFromString<List<ExtTrack>>(raw.ifEmpty { "[]" })
            .map { if (it.providerId.isBlank()) it.copy(providerId = extensionId) else it }
            .also { Log.d(TAG, "customSearch($extensionId, filter=$filter): ${it.size} results") }
    }

    override fun providerMetadata(providerId: String, type: String, id: String): ProviderMetadata {
        initIfNeeded()
        val raw = gobackend.Gobackend.getProviderMetadataJSON(providerId, type, id)
        return DownloadJson.decodeFromString<ProviderMetadata>(raw.ifEmpty { "{}" })
    }

    override fun download(request: DownloadRequest): BackendResult<DownloadResponse> {
        initIfNeeded()
        return runCatching {
            val raw = gobackend.Gobackend.downloadByStrategy(DownloadJson.encodeToString(request))
            DownloadJson.decodeFromString<DownloadResponse>(raw)
        }.fold(
            onSuccess = { resp -> if (resp.success) BackendResult.Ok(resp) else BackendResult.Failed(resp) },
            onFailure = { BackendResult.Failed(DownloadResponse(success = false, error = it.message, errorType = "backend_crash")) },
        )
    }

    override fun progressDelta(sinceSeq: Long): ProgressDelta {
        val raw = gobackend.Gobackend.getAllDownloadProgressDelta(sinceSeq)
        return runCatching { DownloadJson.decodeFromString<ProgressDelta>(raw) }
            .getOrDefault(ProgressDelta(seq = sinceSeq))
    }

    override fun waitProgressDelta(sinceSeq: Long, timeoutMs: Long): ProgressDelta {
        val raw = gobackend.Gobackend.waitForAllDownloadProgressDelta(sinceSeq, timeoutMs)
        return runCatching { DownloadJson.decodeFromString<ProgressDelta>(raw) }
            .getOrDefault(ProgressDelta(seq = sinceSeq))
    }

    override fun cancel(itemId: String) { gobackend.Gobackend.cancelDownload(itemId) }

    override fun pendingAuth(extensionId: String): String {
        return runCatching { gobackend.Gobackend.getExtensionPendingAuthJSON(extensionId) }.getOrDefault("{}")
    }

    override fun submitAuthCode(extensionId: String, code: String) {
        gobackend.Gobackend.setExtensionAuthCodeByID(extensionId, code)
    }

    // Same sequence as SpotiFLAC MainActivity.handleExtensionOAuthIntent.
    override fun completeCallback(sessionGrant: Boolean, state: String, code: String): String {
        initIfNeeded()
        val extId: String
        val raw: String
        if (sessionGrant) {
            extId = gobackend.Gobackend.resolveExtensionCallbackState(state)
            gobackend.Gobackend.setExtensionSessionGrantByID(extId, code)
            raw = gobackend.Gobackend.invokeExtensionActionJSON(extId, "completeGrant")
            val result = runCatching { DownloadJson.decodeFromString<ActionResult>(raw) }.getOrDefault(ActionResult())
            if (!result.success) {
                throw IllegalStateException(result.error ?: result.message ?: "completeGrant failed for $extId")
            }
        } else {
            extId = gobackend.Gobackend.consumeExtensionCallbackState(state)
            gobackend.Gobackend.setExtensionAuthCodeByID(extId, code)
            gobackend.Gobackend.invokeExtensionActionJSON(extId, "completeSpotifyLogin")
        }
        return extId
    }
}

@Serializable
private data class InstallResult(val name: String = "", val success: Boolean = false, val error: String? = null)

@Serializable
private data class ActionResult(val success: Boolean = false, val error: String? = null, val message: String? = null)
