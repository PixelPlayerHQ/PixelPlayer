package com.theveloper.pixelplay.data.telegram

import android.content.Context
import com.theveloper.pixelplay.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramClientManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        init {
            try {
                System.loadLibrary("tdjni")
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "Failed to load TDLib native library")
            }
        }
    }

    private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authorizationState = _authorizationState.asStateFlow()

    private val _updates = MutableSharedFlow<TdApi.Object>(extraBufferCapacity = 64)
    val updates = _updates.asSharedFlow()

    private val _errors = MutableSharedFlow<TdApi.Error>(extraBufferCapacity = 16)
    val errors = _errors.asSharedFlow()

    private var client: Client? = null
    @Volatile
    private var recreateClientAfterClose = false

    // Handler for incoming updates from TDLib
    private val updateHandler = Client.ResultHandler { update ->
        if (update is TdApi.Update) {
            when (update) {
                is TdApi.UpdateAuthorizationState -> {
                    onAuthorizationStateUpdated(update.authorizationState)
                }
                is TdApi.UpdateUser -> {
                    // Handle user updates if needed
                }
                is TdApi.UpdateFile -> {
                    _updates.tryEmit(update)
                }
                // Add other update handlers here
                else -> {}
            }
        } else if (update is TdApi.Error) {
            reportTdError(update)
        }
    }

    init {
        initializeClient()
    }

    @Synchronized
    private fun initializeClient() {
        if (client != null) return
        // Set log verbosity to 1 (Errors only) to prevent heavy logging
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(1))
        } catch (e: Exception) {
            Timber.e(e, "Failed to set TDLib log verbosity")
        }

        // Create a new instance of TDLib Client
        client = Client.create(updateHandler, null, null)
    }

    private fun onAuthorizationStateUpdated(authState: TdApi.AuthorizationState) {
        _authorizationState.value = authState
        when (authState) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val databaseDirectory = File(context.filesDir, "tdlib").absolutePath
                val filesDirectory = File(context.filesDir, "tdlib_files").absolutePath

                // Field assignment (not the flat positional constructor) on purpose: the
                // flat TdApi.SetTdlibParameters(...) constructor's argument order isn't
                // documented for this tdlibx build and was previously guessed, which risks
                // silently shifting values into the wrong field (e.g. apiId landing where
                // systemLanguageCode is expected) with no compile-time or runtime error.
                // Named field assignment, as used in TDLib's own official Java example,
                // can't misalign like that.
                val parameters = TdApi.TdlibParameters().apply {
                    useTestDc = false
                    this.databaseDirectory = databaseDirectory
                    this.filesDirectory = filesDirectory
                    useFileDatabase = true
                    useChatInfoDatabase = true
                    useMessageDatabase = true
                    useSecretChats = false
                    apiId = BuildConfig.TELEGRAM_API_ID
                    apiHash = BuildConfig.TELEGRAM_API_HASH
                    systemLanguageCode = "en"
                    deviceModel = "PixelPlayer Instance"
                    systemVersion = android.os.Build.VERSION.RELEASE
                    applicationVersion = BuildConfig.VERSION_NAME
                    enableStorageOptimizer = true
                    ignoreFileNames = false
                }

                client?.send(TdApi.SetTdlibParameters(parameters), defaultHandler)
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                // UI should prompt for phone number
            }
            is TdApi.AuthorizationStateWaitCode -> {
                // UI should prompt for code
            }
            is TdApi.AuthorizationStateReady -> {
                Timber.d("Telegram Client Ready")
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                Timber.d("Logging out")
            }
            is TdApi.AuthorizationStateClosing -> {
                Timber.d("Closing")
            }
            is TdApi.AuthorizationStateClosed -> {
                Timber.d("Closed")
                client = null
                if (recreateClientAfterClose) {
                    recreateClientAfterClose = false
                    initializeClient()
                }
            }
            else -> {}
        }
    }

    fun sendPhoneNumber(phoneNumber: String) {
        val settings = TdApi.PhoneNumberAuthenticationSettings()
        client?.send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, settings), defaultHandler)
    }

    fun checkAuthenticationCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code), defaultHandler)
    }
    
    fun checkAuthenticationPassword(password: String) {
        client?.send(TdApi.CheckAuthenticationPassword(password), defaultHandler)
    }

    fun logout() {
        recreateClientAfterClose = true
        client?.send(TdApi.LogOut(), defaultHandler)
    }

    fun closeClient(recreate: Boolean = false) {
        recreateClientAfterClose = recreate
        client?.send(TdApi.Close(), defaultHandler)
    }

    /**
     * General purpose suspend function to send requests to TDLib
     */
    suspend fun <T : TdApi.Object> sendRequest(function: TdApi.Function<*>): T = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val localClient = client
        if (localClient != null) {
            localClient.send(function) { result ->
                if (result is TdApi.Error) {
                    reportTdError(result)
                    continuation.resumeWith(
                        Result.failure(
                            TdlibRequestException(
                                code = result.code,
                                rawMessage = result.message
                            )
                        )
                    )
                } else {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resumeWith(Result.success(result as T))
                }
            }
        } else {
            continuation.resumeWith(Result.failure(IllegalStateException("Telegram Client is not initialized")))
        }
    }

    private val defaultHandler = Client.ResultHandler { result ->
        if (result is TdApi.Error) {
            reportTdError(result)
        }
    }

    private fun reportTdError(error: TdApi.Error) {
        _errors.tryEmit(error)
        Timber.e("TDLib Error: ${error.code} - ${error.message}")
    }

    /**
     * Quick check if TDLib is ready to process requests.
     */
    fun isReady(): Boolean = _authorizationState.value is TdApi.AuthorizationStateReady

    /**
     * Suspends until the TDLib client reaches AuthorizationStateReady.
     * @param timeoutMs Maximum time to wait (default 30 seconds)
     * @return true if ready, false if timed out or closed
     */
    suspend fun awaitReady(timeoutMs: Long = 30_000L): Boolean {
        // Quick check first
        if (isReady()) return true
        
        return try {
            withTimeoutOrNull(timeoutMs) {
                authorizationState.first { state ->
                    state is TdApi.AuthorizationStateReady ||
                    state is TdApi.AuthorizationStateClosed
                }
            } is TdApi.AuthorizationStateReady
        } catch (e: Exception) {
            Timber.w("awaitReady failed: ${e.message}")
            false
        }
    }
}

class TdlibRequestException(
    val code: Int,
    rawMessage: String?
) : Exception(rawMessage ?: "Unknown TDLib error")
