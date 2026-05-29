package com.theveloper.pixelplay.presentation.netease.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.cloud.NeteaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

sealed class NeteaseLoginState {
    object Idle : NeteaseLoginState()
    data class Loading(val message: String) : NeteaseLoginState()
    data class Success(val nickname: String) : NeteaseLoginState()
    data class Error(val message: String) : NeteaseLoginState()
}

@HiltViewModel
class NeteaseLoginViewModel @Inject constructor(
    private val repository: NeteaseRepository
) : ViewModel() {

    companion object {
        private const val COOKIE_LOGIN_TIMEOUT_MS = 25_000L
    }

    private val _state = MutableStateFlow<NeteaseLoginState>(NeteaseLoginState.Idle)
    val state: StateFlow<NeteaseLoginState> = _state.asStateFlow()

    fun clearError() {
        if (_state.value is NeteaseLoginState.Error) {
            _state.value = NeteaseLoginState.Idle
        }
    }

    fun processCookies(cookieJson: String) {
        if (_state.value is NeteaseLoginState.Loading) return

        _state.value = NeteaseLoginState.Loading("Verifying session with NetEase...")
        viewModelScope.launch {
            val result = try {
                withTimeout(COOKIE_LOGIN_TIMEOUT_MS) {
                    repository.loginWithCookies(cookieJson)
                }
            } catch (_: TimeoutCancellationException) {
                Result.failure(
                    IllegalStateException(
                        "Verification timed out after ${COOKIE_LOGIN_TIMEOUT_MS / 1000}s. Try again."
                    )
                )
            }

            result.fold(
                onSuccess = { nickname ->
                    _state.value = NeteaseLoginState.Success(nickname)
                },
                onFailure = { error ->
                    _state.value = NeteaseLoginState.Error(
                        error.message ?: "NetEase login failed"
                    )
                }
            )
        }
    }
}
