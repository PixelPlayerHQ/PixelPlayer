package com.theveloper.pixelplay.presentation.qqmusic.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.qqmusic.QqMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class QqMusicLoginState {
    object Idle : QqMusicLoginState()
    data class Loading(val message: String) : QqMusicLoginState()
    data class Success(val nickname: String) : QqMusicLoginState()
    data class Error(val message: String) : QqMusicLoginState()
}

@HiltViewModel
class QqMusicLoginViewModel @Inject constructor(
    private val repository: QqMusicRepository
) : ViewModel() {

    private val _state = MutableStateFlow<QqMusicLoginState>(QqMusicLoginState.Idle)
    val state: StateFlow<QqMusicLoginState> = _state.asStateFlow()

    fun clearError() {
        if (_state.value is QqMusicLoginState.Error) {
            _state.value = QqMusicLoginState.Idle
        }
    }

    fun processCookies(cookieJson: String) {
        if (_state.value is QqMusicLoginState.Loading) return
        _state.value = QqMusicLoginState.Loading("Verifying QQ Music session...")

        viewModelScope.launch {
            val result = repository.loginWithCookies(cookieJson)
            result.fold(
                onSuccess = { nickname -> _state.value = QqMusicLoginState.Success(nickname) },
                onFailure = { err ->
                    _state.value = QqMusicLoginState.Error(
                        err.message ?: "QQ Music login failed"
                    )
                }
            )
        }
    }
}
