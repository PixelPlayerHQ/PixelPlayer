package com.theveloper.pixelplay.presentation.s3.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.s3.S3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class S3FolderItem(val prefix: String, val name: String)

sealed interface S3LoginState {
    data object Idle : S3LoginState
    data object Connecting : S3LoginState
    /** Credentials accepted; user picks a folder to sync. */
    data class FolderSetup(
        val path: String,
        val folders: List<S3FolderItem>
    ) : S3LoginState
    data object Success : S3LoginState
    data class Error(val message: String) : S3LoginState
}

@HiltViewModel
class S3LoginViewModel @Inject constructor(
    private val repository: S3Repository
) : ViewModel() {

    private val _state = MutableStateFlow<S3LoginState>(S3LoginState.Idle)
    val state: StateFlow<S3LoginState> = _state.asStateFlow()

    private var rootPrefix: String = ""
    private var currentRelativePath: String = ""

    fun getExistingConnection() = repository.getConnection()

    fun connect(
        endpoint: String,
        region: String,
        bucket: String,
        accessKey: String,
        secretKey: String,
        prefix: String
    ) {
        if (_state.value is S3LoginState.Connecting) return

        viewModelScope.launch {
            _state.value = S3LoginState.Connecting
            val result = repository.connect(endpoint, region, bucket, accessKey, secretKey, prefix)
            result.fold(
                onSuccess = {
                    rootPrefix = repository.getConnection()?.prefix.orEmpty()
                    browse(rootPrefix)
                },
                onFailure = {
                    _state.value = S3LoginState.Error(it.message ?: "Connection failed")
                }
            )
        }
    }

    /** Navigates the folder browser to a sub-path relative to the configured root prefix. */
    fun browse(relativePath: String) {
        currentRelativePath = relativePath
        viewModelScope.launch {
            repository.browseFolders(rootPrefix + relativePath).fold(
                onSuccess = { entries ->
                    _state.value = S3LoginState.FolderSetup(
                        path = relativePath,
                        folders = entries
                            .filter { it.prefix != rootPrefix + relativePath }
                            .map { entry ->
                                S3FolderItem(
                                    prefix = entry.prefix,
                                    name = entry.prefix.trimEnd('/').substringAfterLast('/').ifBlank { entry.prefix }
                                )
                            }
                    )
                },
                onFailure = {
                    _state.value = S3LoginState.Error(it.message ?: "Failed to list folders")
                }
            )
        }
    }

    /** Adds the folder currently being browsed (root prefix + relative path). */
    fun selectCurrentFolder() {
        selectFolder(rootPrefix + currentRelativePath)
    }

    fun selectFolder(prefix: String) {
        if (_state.value is S3LoginState.Connecting) return
        viewModelScope.launch {
            _state.value = S3LoginState.Connecting
            repository.addFolder(prefix).fold(
                onSuccess = { _state.value = S3LoginState.Success },
                onFailure = { _state.value = S3LoginState.Error(it.message ?: "Sync failed") }
            )
        }
    }

    fun clearError() {
        if (_state.value is S3LoginState.Error) {
            _state.value = S3LoginState.Idle
        }
    }
}
