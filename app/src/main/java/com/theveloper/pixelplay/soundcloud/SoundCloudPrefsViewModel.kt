package com.theveloper.pixelplay.soundcloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SoundCloudPrefsViewModel @Inject constructor(
    private val settings: SoundCloudSettings,
    private val sessionApi: SoundCloudSessionApi,
    private val client: SoundCloudClient,
) : ViewModel() {
    val clientId: StateFlow<String> = settings.clientId.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "",
    )
    val username: StateFlow<String> = settings.username.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "",
    )
    val session: StateFlow<SoundCloudSession> = settings.session.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SoundCloudSession(),
    )

    fun saveClientId(value: String) {
        viewModelScope.launch { settings.setClientId(value) }
    }

    fun saveUsername(value: String) {
        viewModelScope.launch { settings.setUsername(value) }
    }

    fun saveSession(oauthToken: String, cookieHeader: String) {
        viewModelScope.launch {
            client.setSession(oauthToken, cookieHeader)
            sessionApi.updateSession(oauthToken, cookieHeader, clientId.value)
            val profile = withContext(Dispatchers.IO) {
                runCatching { sessionApi.fetchMe() }.getOrNull()
            }
            settings.setSession(
                oauthToken = oauthToken,
                cookieHeader = cookieHeader,
                displayName = profile?.first.orEmpty(),
                permalink = profile?.second.orEmpty(),
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            settings.clearSession()
            client.setSession(null, null)
            sessionApi.updateSession(null, null, clientId.value)
        }
    }
}
