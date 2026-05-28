package com.theveloper.pixelplay.data.gdrive

/**
 * Google Drive integration constants.
 *
 * ## Developer Setup Required
 *
 * Before Google Drive streaming works you MUST supply your own OAuth2 Web Client ID:
 *
 * 1. Go to https://console.cloud.google.com → APIs & Services → Credentials
 * 2. Create (or locate) an "OAuth 2.0 Client ID" of type **Web application**
 * 3. Add your value to `local.properties`:
 *    ```
 *    gdrive.web_client_id=YOUR_ID.apps.googleusercontent.com
 *    ```
 * 4. In `app/build.gradle.kts` expose it as a BuildConfig field:
 *    ```kotlin
 *    val gdriveClientId = properties["gdrive.web_client_id"] as? String ?: ""
 *    buildConfigField("String", "GDRIVE_WEB_CLIENT_ID", "\"$gdriveClientId\"")
 *    ```
 * 5. Replace [WEB_CLIENT_ID] below with `BuildConfig.GDRIVE_WEB_CLIENT_ID`
 *
 * Until this is done [WEB_CLIENT_ID] is an empty string and GDrive auth will
 * fail immediately with a clear error rather than with a confusing placeholder literal.
 */
object GDriveConstants {
    /**
     * OAuth2 Web Client ID.
     * See class-level KDoc for setup instructions.
     */
    const val WEB_CLIENT_ID = "" // ← populate via BuildConfig (see KDoc above)

    const val SCOPE_DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"
    const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"

    val AUDIO_MIME_TYPES = setOf(
        "audio/mpeg", "audio/mp3", "audio/flac", "audio/wav", "audio/x-wav",
        "audio/mp4", "audio/x-m4a", "audio/aac", "audio/ogg",
        "audio/opus", "audio/x-aiff", "audio/alac", "audio/aiff",
        "audio/x-flac", "audio/vnd.wave", "audio/midi", "audio/x-midi",
        "audio/sp-midi", "audio/x-mid"
    )
}

