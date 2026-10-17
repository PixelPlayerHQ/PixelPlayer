package com.theveloper.pixelplay.presentation.download

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.download.BackendResult
import com.theveloper.pixelplay.data.download.DownloadBackendApi
import com.theveloper.pixelplay.data.download.InstalledExtension
import com.theveloper.pixelplay.data.download.RepoExtension
import com.theveloper.pixelplay.data.download.DownloadSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val backend: DownloadBackendApi,
    private val settings: DownloadSettings,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class UiState(
        val extensions: List<InstalledExtension> = emptyList(),
        val repoBrowse: List<RepoExtension> = emptyList(),
        val repoLoading: Boolean = false,
        val repoError: String? = null,
        val registryUrl: String? = null,
        val registryEditing: Boolean = false,
        val registryError: String? = null,
        val installing: Boolean = false,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch(Dispatchers.IO) {
        val extensions = runCatching { backend.listInstalled() }.getOrDefault(emptyList())
        _ui.update { it.copy(extensions = extensions) }
    }

    /**
     * Copies the picked document to a cache temp file before handing it to the
     * backend: .spotiflac-ext documents report opaque MIME types, so the
     * extension suffix is the only reliable signal for LoadExtensionFromPath.
     */
    fun install(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        _ui.update { it.copy(installing = true) }
        val temp = File(context.cacheDir, "extension-${System.currentTimeMillis()}.spotiflac-ext")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            } ?: run {
                _ui.update { it.copy(error = context.getString(R.string.extensions_install_failed)) }
                return@launch
            }
            when (val result = backend.installFromFile(temp.absolutePath)) {
                is BackendResult.Failed -> _ui.update {
                    it.copy(error = result.response.error ?: context.getString(R.string.extensions_install_failed))
                }
                is BackendResult.Ok -> load()
            }
        } catch (e: Exception) {
            _ui.update { it.copy(error = context.getString(R.string.extensions_install_failed)) }
        } finally {
            temp.delete()
            _ui.update { it.copy(installing = false) }
        }
    }

    fun remove(id: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { backend.remove(id) }
            .onFailure { t -> _ui.update { it.copy(error = t.message ?: context.getString(R.string.extensions_install_failed)) } }
        load()
    }

    fun toggle(id: String, enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { backend.setEnabled(id, enabled) }
            .onFailure { t -> _ui.update { it.copy(error = t.message ?: context.getString(R.string.extensions_install_failed)) } }
        load()
    }

    fun browseRepo(forceRefresh: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        val configured = settings.registryUrl
        if (configured == null) {
            _ui.update { it.copy(registryUrl = null, registryEditing = true, repoBrowse = emptyList(), repoLoading = false, repoError = null) }
            return@launch
        }
        _ui.update { it.copy(registryUrl = configured, repoLoading = true, repoError = null) }
        val items = runCatching { backend.listRepoExtensions(forceRefresh) }.getOrDefault(emptyList())
        _ui.update {
            // Empty can only mean failure here: the registry is never empty.
            it.copy(
                repoBrowse = items,
                repoLoading = false,
                repoError = if (items.isEmpty()) context.getString(R.string.extensions_repo_error) else null,
            )
        }
    }

    fun installFromRepo(id: String) = viewModelScope.launch(Dispatchers.IO) {
        _ui.update { it.copy(installing = true) }
        when (val result = backend.installFromRepo(id)) {
            is BackendResult.Failed -> _ui.update {
                it.copy(error = result.response.error ?: context.getString(R.string.extensions_install_failed))
            }
            is BackendResult.Ok -> {
                load()
                browseRepo()
            }
        }
        _ui.update { it.copy(installing = false) }
    }

    fun setRegistryUrl(url: String) = viewModelScope.launch(Dispatchers.IO) {
        val err = backend.setRegistryUrl(url)
        if (err == null) {
            _ui.update { it.copy(registryEditing = false, registryUrl = url.trim()) }
            browseRepo(forceRefresh = true)
        } else {
            _ui.update { it.copy(registryError = err) }
        }
    }

    fun editRegistry() = _ui.update { it.copy(registryEditing = true) }

    fun clearRegistry() = viewModelScope.launch(Dispatchers.IO) {
        backend.clearRegistryUrl()
        _ui.update { it.copy(registryUrl = null, registryEditing = true, repoBrowse = emptyList(), repoError = null) }
    }

    fun consumeRegistryEdit() = _ui.update { it.copy(registryEditing = false, registryError = null) }

    fun consumeError() = _ui.update { it.copy(error = null) }
}
