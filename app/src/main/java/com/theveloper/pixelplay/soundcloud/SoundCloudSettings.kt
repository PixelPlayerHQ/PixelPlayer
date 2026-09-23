package com.theveloper.pixelplay.soundcloud

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.theveloper.pixelplay.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.soundCloudDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "soundcloud_settings",
)

data class SoundCloudSession(
    val oauthToken: String = "",
    val cookieHeader: String = "",
    val displayName: String = "",
    val permalink: String = "",
) {
    val isSignedIn: Boolean get() = oauthToken.isNotBlank()
}

/**
 * SoundCloud preferences: web client_id, optional public username, and web session (OAuth cookie).
 */
@Singleton
class SoundCloudSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.soundCloudDataStore

    val clientId: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.CLIENT_ID]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: BuildConfig.SOUNDCLOUD_CLIENT_ID.trim()
    }

    val username: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.USERNAME]?.trim().orEmpty()
    }

    val session: Flow<SoundCloudSession> = dataStore.data.map { prefs ->
        SoundCloudSession(
            oauthToken = prefs[Keys.OAUTH_TOKEN]?.trim().orEmpty(),
            cookieHeader = prefs[Keys.COOKIE_HEADER]?.trim().orEmpty(),
            displayName = prefs[Keys.DISPLAY_NAME]?.trim().orEmpty(),
            permalink = prefs[Keys.PERMALINK]?.trim().orEmpty(),
        )
    }

    val isSignedIn: Flow<Boolean> = session.map { it.isSignedIn }

    suspend fun setClientId(value: String) {
        dataStore.edit { it[Keys.CLIENT_ID] = value.trim() }
    }

    suspend fun setUsername(value: String) {
        dataStore.edit { it[Keys.USERNAME] = value.trim().removePrefix("@") }
    }

    suspend fun setSession(
        oauthToken: String,
        cookieHeader: String,
        displayName: String = "",
        permalink: String = "",
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.OAUTH_TOKEN] = oauthToken.trim()
            prefs[Keys.COOKIE_HEADER] = cookieHeader.trim()
            prefs[Keys.DISPLAY_NAME] = displayName.trim()
            prefs[Keys.PERMALINK] = permalink.trim().removePrefix("@")
            if (permalink.isNotBlank()) {
                prefs[Keys.USERNAME] = permalink.trim().removePrefix("@")
            }
        }
    }

    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.OAUTH_TOKEN)
            prefs.remove(Keys.COOKIE_HEADER)
            prefs.remove(Keys.DISPLAY_NAME)
            prefs.remove(Keys.PERMALINK)
        }
    }

    private object Keys {
        val CLIENT_ID = stringPreferencesKey("client_id")
        val USERNAME = stringPreferencesKey("username")
        val OAUTH_TOKEN = stringPreferencesKey("oauth_token")
        val COOKIE_HEADER = stringPreferencesKey("cookie_header")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val PERMALINK = stringPreferencesKey("permalink")
    }
}
