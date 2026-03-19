package com.theveloper.pixelplay.presentation.telegram.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.telegram.TdlibRequestException
import com.theveloper.pixelplay.data.telegram.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

data class TelegramLoginUiState(
    val phoneNumber: String = "",
    val code: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val inlineError: String? = null,
    val phoneEditMode: Boolean = false
)

@HiltViewModel
class TelegramLoginViewModel @Inject constructor(
    private val telegramRepository: TelegramRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    val authorizationState = telegramRepository.authorizationState

    private val _uiState = MutableStateFlow(TelegramLoginUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow()

    private val _playbackRequest = MutableSharedFlow<Song>(extraBufferCapacity = 1)
    val playbackRequest = _playbackRequest.asSharedFlow()

    init {
        observeAuthorizationErrors()
        observeAuthorizationState()
    }

    fun onPhoneNumberChanged(number: String) {
        _uiState.update {
            it.copy(
                phoneNumber = normalizePhoneNumber(number),
                inlineError = null
            )
        }
    }

    fun onCodeChanged(code: String) {
        _uiState.update {
            it.copy(
                code = code.filter(Char::isDigit).take(8),
                inlineError = null
            )
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update {
            it.copy(
                password = password,
                inlineError = null
            )
        }
    }

    fun clearInlineError() {
        _uiState.update { it.copy(inlineError = null) }
    }

    fun enablePhoneEditMode() {
        _uiState.update {
            it.copy(
                phoneEditMode = true,
                code = "",
                password = "",
                inlineError = null
            )
        }
        _events.tryEmit("Edit your phone number and send a new verification code.")
    }

    fun handleBackNavigation(authState: TdApi.AuthorizationState?): Boolean {
        val state = _uiState.value
        if (state.isLoading) {
            _events.tryEmit("Please wait for the current request to finish.")
            return true
        }

        if (state.phoneEditMode) {
            return false
        }

        return when (authState) {
            is TdApi.AuthorizationStateWaitCode,
            is TdApi.AuthorizationStateWaitPassword -> {
                enablePhoneEditMode()
                true
            }

            else -> false
        }
    }

    fun sendPhoneNumber() {
        val normalized = normalizePhoneNumber(_uiState.value.phoneNumber)
        if (!isValidPhoneNumber(normalized)) {
            val message = "Use an international phone number (example: +14155550123)."
            _uiState.update { it.copy(inlineError = message) }
            _events.tryEmit(message)
            return
        }

        runAuthAction(loadingMessage = "Sending verification code...") {
            telegramRepository.sendPhoneNumberAwait(normalized)
        }
    }

    fun checkCode() {
        val code = _uiState.value.code.filter(Char::isDigit)
        if (code.length < 3) {
            val message = "Enter the verification code sent by Telegram."
            _uiState.update { it.copy(inlineError = message) }
            _events.tryEmit(message)
            return
        }

        runAuthAction(loadingMessage = "Verifying code...") {
            telegramRepository.checkAuthenticationCodeAwait(code)
        }
    }

    fun checkPassword() {
        val password = _uiState.value.password
        if (password.isBlank()) {
            val message = "Enter your Telegram two-step verification password."
            _uiState.update { it.copy(inlineError = message) }
            _events.tryEmit(message)
            return
        }

        runAuthAction(loadingMessage = "Verifying password...") {
            telegramRepository.checkAuthenticationPasswordAwait(password)
        }
    }

    fun downloadAndPlay(song: Song) {
        val fileId = song.telegramFileId ?: return
        _uiState.update {
            it.copy(
                isLoading = true,
                loadingMessage = "Preparing playback...",
                inlineError = null
            )
        }

        viewModelScope.launch {
            val localPath = telegramRepository.downloadFileAwait(fileId)
            _uiState.update { state -> state.copy(isLoading = false, loadingMessage = "") }

            if (localPath != null) {
                val playableSong = song.copy(path = localPath, contentUriString = localPath)
                _playbackRequest.tryEmit(playableSong)
            } else {
                val message = "Could not download this track. Please try again."
                _uiState.update { state -> state.copy(inlineError = message) }
                _events.tryEmit(message)
            }
        }
    }

    fun clearData() {
        viewModelScope.launch {
            musicRepository.clearTelegramData()
            _events.tryEmit("Telegram cache cleared.")
        }
    }

    private fun runAuthAction(
        loadingMessage: String,
        action: suspend () -> Result<Unit>
    ) {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = loadingMessage,
                    inlineError = null
                )
            }

            val result = action()
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingMessage = "",
                            inlineError = null,
                            phoneEditMode = false
                        )
                    }
                },
                onFailure = { throwable ->
                    val message = mapThrowableToMessage(throwable)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingMessage = "",
                            inlineError = message
                        )
                    }
                    _events.tryEmit(message)
                }
            )
        }
    }

    private fun observeAuthorizationErrors() {
        viewModelScope.launch {
            telegramRepository.authErrors.collect { error ->
                val message = mapTdLibError(error.code, error.message)
                val current = _uiState.value
                if (current.inlineError == message && !current.isLoading) {
                    return@collect
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = "",
                        inlineError = message
                    )
                }
                _events.tryEmit(message)
            }
        }
    }

    private fun observeAuthorizationState() {
        viewModelScope.launch {
            telegramRepository.authorizationState.collect { state ->
                when (state) {
                    is TdApi.AuthorizationStateWaitPhoneNumber -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                loadingMessage = "",
                                inlineError = null,
                                phoneEditMode = false,
                                code = "",
                                password = ""
                            )
                        }
                    }

                    is TdApi.AuthorizationStateWaitCode -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                loadingMessage = "",
                                inlineError = null,
                                phoneEditMode = false,
                                password = ""
                            )
                        }
                    }

                    is TdApi.AuthorizationStateWaitPassword -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                loadingMessage = "",
                                inlineError = null
                            )
                        }
                    }

                    is TdApi.AuthorizationStateReady -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                loadingMessage = "",
                                inlineError = null,
                                phoneEditMode = false,
                                code = "",
                                password = ""
                            )
                        }
                    }

                    is TdApi.AuthorizationStateClosed -> {
                        val message = "Telegram session was closed. Try opening login again."
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                loadingMessage = "",
                                inlineError = message
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun normalizePhoneNumber(raw: String): String {
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter(Char::isDigit)
        return if (hasPlus) "+$digits" else digits
    }

    private fun isValidPhoneNumber(value: String): Boolean {
        val digits = value.filter(Char::isDigit)
        return value.startsWith("+") && digits.length in 7..15
    }

    private fun mapThrowableToMessage(error: Throwable): String {
        return when (error) {
            is TdlibRequestException -> mapTdLibError(error.code, error.message)
            is IllegalStateException -> error.message ?: "Telegram login failed."
            else -> {
                val message = error.message.orEmpty()
                if (message.contains("did not respond", ignoreCase = true)) {
                    "Telegram request timed out. Check your connection and retry."
                } else {
                    "Telegram login failed: ${message.ifBlank { "unknown error" }}"
                }
            }
        }
    }

    private fun mapTdLibError(code: Int, rawMessage: String?): String {
        val message = rawMessage.orEmpty()
        return when {
            message.contains("PHONE_NUMBER_INVALID", ignoreCase = true) -> {
                "Invalid phone number. Confirm country code and number."
            }

            message.contains("PHONE_NUMBER_FLOOD", ignoreCase = true) ||
                message.contains("FLOOD_WAIT", ignoreCase = true) -> {
                "Too many attempts. Wait a few minutes and try again."
            }

            message.contains("PHONE_CODE_INVALID", ignoreCase = true) -> {
                "Invalid verification code. Check it and try again."
            }

            message.contains("PHONE_CODE_EXPIRED", ignoreCase = true) -> {
                "Verification code expired. Request a new code."
            }

            message.contains("PASSWORD_HASH_INVALID", ignoreCase = true) -> {
                "Incorrect two-step password."
            }

            message.contains("NETWORK", ignoreCase = true) ||
                message.contains("TIMEOUT", ignoreCase = true) ||
                code == 408 -> {
                "Telegram network error. Check your internet and retry."
            }

            message.isBlank() -> "Telegram error ($code)."
            else -> "Telegram error ($code): $message"
        }
    }
}
