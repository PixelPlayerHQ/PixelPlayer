package com.theveloper.pixelplay.soundcloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SoundCloudPrefsViewModel @Inject constructor(
    private val settings: SoundCloudSettings,
    private val sessionApi: SoundCloudSessionApi,
    private val client: SoundCloudClient,
    private val libraryGate: SoundCloudLibraryGate,
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
    val includeDownloadsInLibrary: StateFlow<Boolean> = settings.includeDownloadsInLibrary.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        true,
    )

    fun saveClientId(value: String) {
        viewModelScope.launch { settings.setClientId(value) }
    }

    fun saveUsername(value: String) {
        viewModelScope.launch { settings.setUsername(value) }
    }

    fun saveSession(oauthToken: String, cookieHeader: String) {
        viewModelScope.launch {
            val knownClientId = settings.clientId.first().ifBlank { clientId.value }
            client.setSession(oauthToken, cookieHeader)
            sessionApi.updateSession(oauthToken, cookieHeader, knownClientId.ifBlank { null })
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

    fun setIncludeDownloadsInLibrary(include: Boolean) {
        viewModelScope.launch {
            settings.setIncludeDownloadsInLibrary(include)
            runCatching { libraryGate.apply(include) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            settings.clearSession()
            client.setSession(null, null)
            sessionApi.updateSession(null, null, clientId.value)
        }
    }

    val likedPermalinks: StateFlow<Set<String>> = client.likedPermalinks

    fun permalinkFor(songId: String): String? = client.permalinkForSong(songId)

    /**
     * Returns an error message when the like could not be saved.
     */
    suspend fun toggleSoundCloudLike(songId: String): String? = withContext(Dispatchers.IO) {
        val url = client.permalinkForSong(songId)
            ?: return@withContext "This SoundCloud track can't be liked from here"
        if (!client.hasSession) return@withContext "Sign in to SoundCloud to like tracks"
        val shouldLike = url !in client.likedPermalinks.value
        runCatching { client.setTrackLiked(url, shouldLike) }
            .exceptionOrNull()
            ?.message
            ?.ifBlank { "Couldn't update the SoundCloud like" }
    }
}
