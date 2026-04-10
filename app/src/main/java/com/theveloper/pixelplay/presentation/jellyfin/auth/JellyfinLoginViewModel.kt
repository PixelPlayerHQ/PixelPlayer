package com.theveloper.pixelplay.presentation.jellyfin.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.jellyfin.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface JellyfinLoginState {
    data object Idle : JellyfinLoginState
    data object Loading : JellyfinLoginState
    data class Success(val username: String) : JellyfinLoginState
    data class Error(val message: String) : JellyfinLoginState
}

@HiltViewModel
class JellyfinLoginViewModel @Inject constructor(
    private val repository: JellyfinRepository
) : ViewModel() {

    private val _state = MutableStateFlow<JellyfinLoginState>(JellyfinLoginState.Idle)
    val state: StateFlow<JellyfinLoginState> = _state.asStateFlow()

    fun login(serverUrl: String, username: String, password: String) {
        if (_state.value is JellyfinLoginState.Loading) return

        viewModelScope.launch {
            _state.value = JellyfinLoginState.Loading

            val result = repository.login(serverUrl, username, password)

            _state.value = result.fold(
                onSuccess = { JellyfinLoginState.Success(it) },
                onFailure = { JellyfinLoginState.Error(it.message ?: "Login failed") }
            )
        }
    }

    fun clearError() {
        if (_state.value is JellyfinLoginState.Error) {
            _state.value = JellyfinLoginState.Idle
        }
    }

    fun reset() {
        _state.value = JellyfinLoginState.Idle
    }
}
